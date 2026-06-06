package com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IThaumcraftRecipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Thaumcraft 6 坩埚远程处理器。
 *
 * <p>支持中枢 ME 接口对坩埚的自动化：
 * <ul>
 *   <li>智能发配顺序：自动遍历 CrucibleRecipe 识别催化剂，确保催化剂最后投入</li>
 *   <li>研究绕过：使用 FakePlayer 临时授予所有坩埚配方研究</li>
 *   <li>源质残留管理：成功收集产物后才调用 spillRemnants 清空（可配置开关）</li>
 * </ul>
 *
 * <p><b>重要</b>：坩埚合成是瞬时的，且对投入顺序敏感。
 * 如果样板中包含多个相同物品且该物品同时是某配方的催化剂，
 * 只有其中一个会被识别为催化剂并移至队尾，其余会提前被分解为源质。</p>
 */
public class ThaumcraftCrucibleHandler implements IRemoteHandler {

    private static final String BLOCK_ID = "thaumcraft:crucible";
    private static final GameProfile CRUCIBLE_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("ae2e-crucible".getBytes()), "[AE2E]");
    private static final Set<String> GRANTED_RESEARCH = new HashSet<>();

    private static Class<?> CLASS_TILE_CRUCIBLE;
    private static Class<?> CLASS_ENTITY_SPECIAL_ITEM;
    private static Method METHOD_ATTEMPT_SMELT;
    private static Method METHOD_SPILL_REMNANTS;
    private static Method METHOD_GET_ITEM;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_CRUCIBLE = Class.forName("thaumcraft.common.tiles.crafting.TileCrucible");
            CLASS_ENTITY_SPECIAL_ITEM = Class.forName("thaumcraft.common.entities.EntitySpecialItem");

            METHOD_ATTEMPT_SMELT = CLASS_TILE_CRUCIBLE.getMethod("attemptSmelt", ItemStack.class, String.class);
            METHOD_SPILL_REMNANTS = CLASS_TILE_CRUCIBLE.getMethod("spillRemnants");
            // EntityItem.getItem() 运行时 SRG 名为 func_92059_d
            METHOD_GET_ITEM = CLASS_ENTITY_SPECIAL_ITEM.getMethod("func_92059_d");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ThaumcraftCrucibleHandler reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return BLOCK_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_TILE_CRUCIBLE.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_CRUCIBLE.isInstance(te)) return false;

        boolean hasItems = false;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        return hasItems;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_CRUCIBLE.isInstance(te)) return false;

        try {
            // 收集物品
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    items.add(stack.copy());
                }
            }
            if (items.isEmpty()) return false;

            // 智能识别催化剂并调整发配顺序
            ItemStack catalyst = identifyCatalyst(items);
            if (!catalyst.isEmpty()) {
                items = reorderItems(items, catalyst);
            }

            // FakePlayer 研究绕过
            String username = ensureResearch(world);

            // 处理物品
            for (ItemStack stack : items) {
                ItemStack remaining = (ItemStack) METHOD_ATTEMPT_SMELT.invoke(te, stack, username);
                if (remaining == null) {
                    remaining = ItemStack.EMPTY;
                }
            }
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] ThaumcraftCrucibleHandler.pushMaterials failed at {}", pos, e);
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs) {
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = collectSpecialItems(world, pos);

        // 只在成功收集到产物后才清空坩埚，避免失败/revert时误清空
        if (!result.isEmpty() && AE2EnhancedConfig.thaumcraft.clearAfterCraft) {
            try {
                TileEntity te = world.getTileEntity(pos);
                if (CLASS_TILE_CRUCIBLE.isInstance(te)) {
                    METHOD_SPILL_REMNANTS.invoke(te);
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] spillRemnants failed at {}", pos, e);
            }
        }

        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        // 只收集已弹出的产物，不清空坩埚（避免失败时 aspects 被浪费）
        initReflection();
        return collectSpecialItems(world, pos);
    }

    // ---- 内部方法 ----

    private List<ItemStack> collectSpecialItems(World world, BlockPos pos) {
        List<ItemStack> result = new ArrayList<>();
        AxisAlignedBB aabb = new AxisAlignedBB(
                pos.getX() - 0.5, pos.getY() + 0.5, pos.getZ() - 0.5,
                pos.getX() + 1.5, pos.getY() + 3.0, pos.getZ() + 1.5
        );
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Entity> clazz = (Class<? extends Entity>) CLASS_ENTITY_SPECIAL_ITEM;
            for (Entity entity : world.getEntitiesWithinAABB(clazz, aabb)) {
                if (!CLASS_ENTITY_SPECIAL_ITEM.isInstance(entity)) continue;

                ItemStack item = (ItemStack) METHOD_GET_ITEM.invoke(entity);
                if (item != null && !item.isEmpty()) {
                    result.add(item.copy());
                    entity.setDead();
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] collectSpecialItems failed at {}", pos, e);
        }
        return result;
    }

    /**
     * 遍历所有 CrucibleRecipe，识别当前物品列表中的催化剂。
     * 返回第一个匹配的催化剂 ItemStack（引用自 items 列表），找不到则返回 EMPTY。
     */
    private static ItemStack identifyCatalyst(List<ItemStack> items) {
        Map<net.minecraft.util.ResourceLocation, IThaumcraftRecipe> recipes = ThaumcraftApi.getCraftingRecipes();
        for (IThaumcraftRecipe recipe : recipes.values()) {
            if (!(recipe instanceof CrucibleRecipe)) continue;
            CrucibleRecipe cr = (CrucibleRecipe) recipe;
            Ingredient catalyst = cr.getCatalyst();
            if (catalyst == null) continue;
            for (ItemStack item : items) {
                if (catalyst.apply(item)) {
                    return item;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 将识别出的催化剂移至队尾，确保先分解源质来源、后投入催化剂触发合成。
     * 只移动第一个匹配的 stack，其余相同物品保留在前面。
     */
    private static List<ItemStack> reorderItems(List<ItemStack> items, ItemStack catalyst) {
        List<ItemStack> result = new ArrayList<>();
        boolean moved = false;
        for (ItemStack stack : items) {
            if (!moved && ItemStack.areItemsEqual(stack, catalyst) && ItemStack.areItemStackTagsEqual(stack, catalyst)) {
                moved = true;
                continue; // 跳过，稍后添加到队尾
            }
            result.add(stack);
        }
        if (moved) {
            result.add(catalyst);
        }
        return result;
    }

    private static String ensureResearch(World world) {
        if (!(world instanceof WorldServer)) return "[AE2E]";

        FakePlayer fakePlayer = FakePlayerFactory.get((WorldServer) world, CRUCIBLE_PROFILE);
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(fakePlayer);
        if (knowledge != null && GRANTED_RESEARCH.isEmpty()) {
            for (IThaumcraftRecipe recipe : ThaumcraftApi.getCraftingRecipes().values()) {
                if (recipe instanceof CrucibleRecipe) {
                    String research = ((CrucibleRecipe) recipe).getResearch();
                    if (research != null && !research.isEmpty() && !knowledge.isResearchKnown(research)) {
                        knowledge.addResearch(research);
                        GRANTED_RESEARCH.add(research);
                    }
                }
            }
        }
        return fakePlayer.getName();
    }
}

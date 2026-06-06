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
import java.util.HashMap;
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
 *   <li>催化剂延迟：催化剂固定延迟 3 tick 投入，给源质分解留出时间</li>
 *   <li>研究绕过：使用 FakePlayer 临时授予所有坩埚配方研究</li>
 *   <li>源质残留管理：成功收集产物后才调用 spillRemnants 清空（可配置开关）</li>
 * </ul>
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

    // 延迟催化剂缓存：dimension -> (pos -> DelayedCatalyst)
    private static final Map<Integer, Map<BlockPos, DelayedCatalyst>> DELAYED = new HashMap<>();

    private static class DelayedCatalyst {
        final ItemStack catalyst;
        final long targetTick;
        final String username;

        DelayedCatalyst(ItemStack catalyst, long targetTick, String username) {
            this.catalyst = catalyst;
            this.targetTick = targetTick;
            this.username = username;
        }
    }

    private static Map<BlockPos, DelayedCatalyst> getDelayed(World world) {
        return DELAYED.computeIfAbsent(world.provider.getDimension(), k -> new HashMap<>());
    }

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
        // 如果还有延迟催化剂未投入，不能开始新合成
        if (getDelayed(world).containsKey(pos)) return false;

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

            // 识别催化剂
            ItemStack identifiedCatalyst = identifyCatalyst(items);
            ItemStack catalyst = ItemStack.EMPTY;
            List<ItemStack> sources = new ArrayList<>();
            if (!identifiedCatalyst.isEmpty()) {
                boolean found = false;
                for (ItemStack stack : items) {
                    if (!found && ItemStack.areItemsEqual(stack, identifiedCatalyst)
                            && ItemStack.areItemStackTagsEqual(stack, identifiedCatalyst)) {
                        catalyst = stack;
                        found = true;
                    } else {
                        sources.add(stack);
                    }
                }
            } else {
                sources.addAll(items);
            }

            // FakePlayer 研究绕过
            String username = ensureResearch(world);

            // 投入源质来源
            for (ItemStack stack : sources) {
                ItemStack remaining = (ItemStack) METHOD_ATTEMPT_SMELT.invoke(te, stack, username);
                if (remaining == null) {
                    remaining = ItemStack.EMPTY;
                }
            }

            // 缓存催化剂，固定延迟 3 tick 投入
            if (!catalyst.isEmpty()) {
                getDelayed(world).put(pos, new DelayedCatalyst(catalyst.copy(),
                        world.getTotalWorldTime() + 10, username));
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
        initReflection();
        Map<BlockPos, DelayedCatalyst> delayed = getDelayed(world);
        DelayedCatalyst dc = delayed.get(pos);
        if (dc != null) {
            if (world.getTotalWorldTime() >= dc.targetTick) {
                TileEntity te = world.getTileEntity(pos);
                if (CLASS_TILE_CRUCIBLE.isInstance(te)) {
                    try {
                        ItemStack remaining = (ItemStack) METHOD_ATTEMPT_SMELT.invoke(te, dc.catalyst, dc.username);
                        if (remaining == null) remaining = ItemStack.EMPTY;
                    } catch (Exception e) {
                        AE2Enhanced.LOGGER.error("[AE2E] Delayed catalyst smelt failed at {}", pos, e);
                    }
                }
                delayed.remove(pos);
            } else {
                return false; // 还在等待延迟
            }
        }
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source) {
        initReflection();
        // 如果还有未延迟投入的催化剂，不能收集产物
        if (getDelayed(world).containsKey(pos)) {
            return new ArrayList<>();
        }

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
        getDelayed(world).remove(pos);
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

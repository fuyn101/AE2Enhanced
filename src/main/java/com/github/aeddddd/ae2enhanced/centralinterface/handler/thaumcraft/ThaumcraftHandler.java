package com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.RecipeMatcher;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IThaumcraftRecipe;
import thaumcraft.api.crafting.InfusionRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thaumcraft 注魔矩阵远程处理器。
 *
 * <p>配方匹配策略：在 {@code canStart} 中通过输入物品种类+数量遍历注魔配方列表，
 * 找到匹配后缓存；{@code pushMaterials} 将主材放入矩阵正下方基座、辅材放入周围基座；
 * {@code startProcess} 使用 {@code FakePlayer} 调用 {@code craftingStart} 并临时授予研究，
 * 以跳过进度检查。</p>
 */
public class ThaumcraftHandler implements IRemoteHandler {

    private static final String BLOCK_ID = "thaumcraft:infusion_matrix";

    // Thaumcraft 内部类反射（API 类直接引用，内部类反射隔离）
    private static Class<?> CLASS_TILE_INFUSION_MATRIX;
    private static Class<?> CLASS_TILE_PEDESTAL;
    private static Method METHOD_CRAFTING_START;
    private static Method METHOD_GET_SURROUNDINGS;
    private static Method METHOD_VALID_LOCATION;
    private static Method METHOD_SYNC_TILE;
    private static Method METHOD_MARK_DIRTY;
    private static Method METHOD_PEDESTAL_GET_STACK;
    private static Method METHOD_PEDESTAL_SET_STACK;
    private static Method METHOD_PEDESTAL_SET_STACK_FROM_INFUSION;
    private static Field FIELD_ACTIVE;
    private static Field FIELD_CRAFTING;
    private static Field FIELD_PEDESTALS;
    private static Field FIELD_RECIPE_OUTPUT;
    private static Field FIELD_RECIPE_OUTPUT_LABEL;
    private static Field FIELD_RECIPE_INPUT;
    private static Field FIELD_RECIPE_INGREDIENTS;
    private static Field FIELD_RECIPE_TYPE;
    private static Field FIELD_RECIPE_ESSENTIA;
    private static Field FIELD_RECIPE_INSTABILITY;
    private static Field FIELD_RECIPE_XP;
    private static Field FIELD_RECIPE_PLAYER;
    private static Field FIELD_CHECK_SURROUNDINGS;
    private static boolean reflectionReady = false;

    // 配方缓存：BlockPos → InfusionRecipe（仅在 startProcess 中使用，用于 FakePlayer research 和回退）
    private final Map<BlockPos, InfusionRecipe> recipeCache = new ConcurrentHashMap<>();

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_INFUSION_MATRIX = Class.forName("thaumcraft.common.tiles.crafting.TileInfusionMatrix");
            CLASS_TILE_PEDESTAL = Class.forName("thaumcraft.common.tiles.crafting.TilePedestal");

            METHOD_CRAFTING_START = CLASS_TILE_INFUSION_MATRIX.getMethod("craftingStart", EntityPlayer.class);
            METHOD_GET_SURROUNDINGS = CLASS_TILE_INFUSION_MATRIX.getDeclaredMethod("getSurroundings");
            METHOD_GET_SURROUNDINGS.setAccessible(true);
            METHOD_VALID_LOCATION = CLASS_TILE_INFUSION_MATRIX.getMethod("validLocation");
            METHOD_SYNC_TILE = CLASS_TILE_INFUSION_MATRIX.getMethod("syncTile", boolean.class);
            METHOD_MARK_DIRTY = CLASS_TILE_INFUSION_MATRIX.getMethod("func_70296_d");

            METHOD_PEDESTAL_GET_STACK = CLASS_TILE_PEDESTAL.getMethod("func_70301_a", int.class);
            METHOD_PEDESTAL_SET_STACK = CLASS_TILE_PEDESTAL.getMethod("func_70299_a", int.class, ItemStack.class);
            METHOD_PEDESTAL_SET_STACK_FROM_INFUSION = CLASS_TILE_PEDESTAL.getMethod("setInventorySlotContentsFromInfusion", int.class, ItemStack.class);

            FIELD_ACTIVE = CLASS_TILE_INFUSION_MATRIX.getField("active");
            FIELD_CRAFTING = CLASS_TILE_INFUSION_MATRIX.getField("crafting");
            FIELD_PEDESTALS = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("pedestals");
            FIELD_PEDESTALS.setAccessible(true);
            FIELD_RECIPE_OUTPUT = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeOutput");
            FIELD_RECIPE_OUTPUT.setAccessible(true);
            FIELD_RECIPE_OUTPUT_LABEL = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeOutputLabel");
            FIELD_RECIPE_OUTPUT_LABEL.setAccessible(true);
            FIELD_RECIPE_INPUT = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeInput");
            FIELD_RECIPE_INPUT.setAccessible(true);
            FIELD_RECIPE_INGREDIENTS = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeIngredients");
            FIELD_RECIPE_INGREDIENTS.setAccessible(true);
            FIELD_RECIPE_TYPE = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeType");
            FIELD_RECIPE_TYPE.setAccessible(true);
            FIELD_RECIPE_ESSENTIA = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeEssentia");
            FIELD_RECIPE_ESSENTIA.setAccessible(true);
            FIELD_RECIPE_INSTABILITY = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeInstability");
            FIELD_RECIPE_INSTABILITY.setAccessible(true);
            FIELD_RECIPE_XP = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipeXP");
            FIELD_RECIPE_XP.setAccessible(true);
            FIELD_RECIPE_PLAYER = CLASS_TILE_INFUSION_MATRIX.getDeclaredField("recipePlayer");
            FIELD_RECIPE_PLAYER.setAccessible(true);
            FIELD_CHECK_SURROUNDINGS = CLASS_TILE_INFUSION_MATRIX.getField("checkSurroundings");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ThaumcraftHandler reflection init failed", e);
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
        boolean valid = CLASS_TILE_INFUSION_MATRIX.isInstance(te);
        if (!valid) {
            AE2Enhanced.LOGGER.warn("[AE2E] ThaumcraftHandler isValidTarget failed at {}: te={}", pos, te);
        }
        return valid;
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft canStart: not a TileInfusionMatrix at {}", pos);
            return false;
        }

        if (isActive(te)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft canStart: matrix is active at {}", pos);
            return false;
        }
        if (isCrafting(te)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft canStart: matrix is crafting at {}", pos);
            return false;
        }
        if (!validLocation(te)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft canStart: validLocation=false at {}. Check pedestal at {} and pillars at diagonals.", pos, pos.down(2));
            return false;
        }

        // 清空并检查基座
        if (!clearAndCheckPedestals(world, pos, te)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft canStart: clearAndCheckPedestals failed at {}. Check main pedestal at {} is empty and is a TilePedestal.", pos, pos.down(2));
            return false;
        }

        // AE 样板约定：第一个非空槽位 = 主材，其余 = 辅材
        boolean hasItems = false;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        if (!hasItems) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft canStart: no items in ingredients at {}", pos);
        }
        return hasItems;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft pushMaterials: not a TileInfusionMatrix at {}", pos);
            return false;
        }

        // 清空所有基座
        clearAllPedestals(world, pos, te);

        // 按 AE 样板槽位顺序收集：第一个非空 = 主材，其余 = 辅材
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        if (stacks.isEmpty()) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft pushMaterials: no items to push at {}", pos);
            return false;
        }

        // 放入主材基座（矩阵正下方 2 格）
        ItemStack mainItem = stacks.remove(0);
        BlockPos mainPos = pos.down(2);
        TileEntity mainTe = world.getTileEntity(mainPos);
        if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft pushMaterials: main pedestal at {} is not a TilePedestal (te={})", mainPos, mainTe);
            return false;
        }
        try {
            METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, mainItem);
            AE2Enhanced.LOGGER.info("[AE2E] Thaumcraft pushMaterials: placed main item {} at {}", mainItem, mainPos);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft pushMaterials: failed to place main item at {}", mainPos, e);
            return false;
        }

        // 放入辅材（按 pedestals 列表顺序，逐个基座放剩余物品）
        int placed = 0;
        try {
            METHOD_GET_SURROUNDINGS.invoke(te);
            @SuppressWarnings("unchecked")
            List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
            AE2Enhanced.LOGGER.info("[AE2E] Thaumcraft pushMaterials: found {} aux pedestals, {} aux items to place", pedestals.size(), stacks.size());
            for (BlockPos pPos : pedestals) {
                if (stacks.isEmpty()) break;
                TileEntity pTe = world.getTileEntity(pPos);
                if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft pushMaterials: skipping non-pedestal at {}", pPos);
                    continue;
                }
                if (!((ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0)).isEmpty()) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft pushMaterials: pedestal at {} is not empty, skipping", pPos);
                    continue;
                }
                ItemStack aux = stacks.remove(0);
                METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, aux);
                AE2Enhanced.LOGGER.info("[AE2E] Thaumcraft pushMaterials: placed aux item {} at {}", aux, pPos);
                placed++;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft pushMaterials: exception placing aux items", e);
            return false;
        }

        if (!stacks.isEmpty()) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft pushMaterials: {} aux items could not be placed (not enough pedestals)", stacks.size());
        }
        return stacks.isEmpty();
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft startProcess: not a TileInfusionMatrix at {}", pos);
            return false;
        }

        // 先尝试让矩阵自己走正常流程：craftingStart 内部会查找配方、检查研究、吸收源质
        // 注意：craftingStart 返回 void，不能读取 boolean 返回值
        if (world instanceof WorldServer) {
            try {
                FakePlayer fakePlayer = FakePlayerFactory.get((WorldServer) world,
                    new GameProfile(UUID.randomUUID(), "[AE2E]"));

                // 第一次调用：不加研究，让 craftingStart 内部自行匹配
                METHOD_CRAFTING_START.invoke(te, fakePlayer);
                if (isActive(te)) {
                    AE2Enhanced.LOGGER.info("[AE2E] Thaumcraft startProcess: crafting started successfully at {}", pos);
                    recipeCache.remove(pos);
                    return true;
                }
                AE2Enhanced.LOGGER.info("[AE2E] Thaumcraft startProcess: first craftingStart did not activate matrix at {}", pos);

                // 未启动，可能是研究不足。从基座反查配方，临时授予研究后重试
                InfusionRecipe recipe = findRecipeFromPedestals(world, pos, te);
                if (recipe != null) {
                    AE2Enhanced.LOGGER.info("[AE2E] Thaumcraft startProcess: found recipe '{}', attempting research bypass", recipe.getResearch());
                    IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(fakePlayer);
                    String research = recipe.getResearch();
                    boolean added = false;
                    if (knowledge != null && research != null && !research.isEmpty()) {
                        added = knowledge.addResearch(research);
                    }
                    METHOD_CRAFTING_START.invoke(te, fakePlayer);
                    if (added && knowledge != null) {
                        knowledge.removeResearch(research);
                    }
                    if (isActive(te)) {
                        AE2Enhanced.LOGGER.info("[AE2E] Thaumcraft startProcess: crafting started with research bypass at {}", pos);
                        recipeCache.remove(pos);
                        return true;
                    }
                    AE2Enhanced.LOGGER.info("[AE2E] Thaumcraft startProcess: second craftingStart also failed at {}, falling back to forceStart", pos);
                    // 仍然失败（可能是源质不足等），回退到强制完成
                    recipeCache.put(pos, recipe);
                    return forceStartCrafting(world, pos, te, recipe);
                } else {
                    AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft startProcess: could not find matching recipe from pedestals at {}", pos);
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] ThaumcraftHandler startProcess exception at {}", pos, e);
                InfusionRecipe recipe = findRecipeFromPedestals(world, pos, te);
                if (recipe != null) {
                    recipeCache.put(pos, recipe);
                    return forceStartCrafting(world, pos, te, recipe);
                }
            }
        } else {
            AE2Enhanced.LOGGER.warn("[AE2E] Thaumcraft startProcess: world is not WorldServer at {}", pos);
        }

        recipeCache.remove(pos);
        return false;
    }

    /**
     * FakePlayer 方案失败时的回退：直接设置矩阵字段并立即完成合成。
     * 这会跳过注魔过程（源质吸收、稳定性波动等），直接产出结果。
     */
    private boolean forceStartCrafting(World world, BlockPos pos, TileEntity te, InfusionRecipe recipe) {
        try {
            // 获取主材
            BlockPos mainPos = pos.down(2);
            TileEntity mainTe = world.getTileEntity(mainPos);
            if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) return false;
            ItemStack mainItem = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0);
            if (mainItem.isEmpty()) return false;

            // 获取产物
            Object output = recipe.getRecipeOutput();
            if (output instanceof ItemStack) {
                ItemStack result = ((ItemStack) output).copy();

                // 消耗主材（如果有耐久则减 1，否则清空）
                if (mainItem.isItemStackDamageable()) {
                    mainItem.setItemDamage(mainItem.getItemDamage() + 1);
                    if (mainItem.getItemDamage() >= mainItem.getMaxDamage()) {
                        METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, ItemStack.EMPTY);
                    } else {
                        METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, mainItem);
                    }
                } else {
                    METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, ItemStack.EMPTY);
                }

                // 消耗辅材并处理 container item
                @SuppressWarnings("unchecked")
                List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
                NonNullList<Ingredient> components = recipe.getComponents();
                for (int i = 0; i < components.size() && i < pedestals.size(); i++) {
                    BlockPos pPos = pedestals.get(i);
                    TileEntity pTe = world.getTileEntity(pPos);
                    if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                    ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0);
                    if (stack.isEmpty()) continue;

                    ItemStack container = stack.getItem().getContainerItem(stack);
                    if (container != null && !container.isEmpty()) {
                        METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, container.copy());
                    } else {
                        METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, ItemStack.EMPTY);
                    }
                }

                // 放入产物
                METHOD_PEDESTAL_SET_STACK_FROM_INFUSION.invoke(mainTe, 0, result);
            }

            // 重置矩阵状态
            FIELD_ACTIVE.setBoolean(te, false);
            FIELD_CRAFTING.setBoolean(te, false);
            METHOD_MARK_DIRTY.invoke(te);
            METHOD_SYNC_TILE.invoke(te, false);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) return false;
        return !isActive(te) && !isCrafting(te);
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();

        // 主材基座产物
        BlockPos mainPos = pos.down(2);
        TileEntity mainTe = world.getTileEntity(mainPos);
        if (CLASS_TILE_PEDESTAL.isInstance(mainTe)) {
            try {
                ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, ItemStack.EMPTY);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // 辅材基座返还物品
        try {
            TileEntity te = world.getTileEntity(pos);
            if (CLASS_TILE_INFUSION_MATRIX.isInstance(te)) {
                @SuppressWarnings("unchecked")
                List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
                for (BlockPos pPos : pedestals) {
                    TileEntity pTe = world.getTileEntity(pPos);
                    if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                    ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0);
                    if (!stack.isEmpty()) {
                        result.add(stack.copy());
                        METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, ItemStack.EMPTY);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return result;
    }

    // ---- 配方查找（仅在 startProcess 回退路径中使用） ----

    /**
     * 从已放置到基座的物品反查注魔配方。
     * 用于 craftingStart 失败后的 FakePlayer research 授予和强制回退。
     */
    private static InfusionRecipe findRecipeFromPedestals(World world, BlockPos pos, TileEntity te) {
        // 读取主材
        BlockPos mainPos = pos.down(2);
        TileEntity mainTe = world.getTileEntity(mainPos);
        if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) return null;
        ItemStack mainItem;
        try {
            mainItem = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0);
        } catch (Exception e) {
            return null;
        }
        if (mainItem.isEmpty()) return null;

        // 读取辅材
        List<ItemStack> auxItems = new ArrayList<>();
        try {
            METHOD_GET_SURROUNDINGS.invoke(te);
            @SuppressWarnings("unchecked")
            List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
            for (BlockPos pPos : pedestals) {
                TileEntity pTe = world.getTileEntity(pPos);
                if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                ItemStack stack = (ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0);
                if (!stack.isEmpty()) {
                    auxItems.add(stack);
                }
            }
        } catch (Exception e) {
            return null;
        }

        // 遍历配方匹配
        Map<net.minecraft.util.ResourceLocation, IThaumcraftRecipe> recipes = ThaumcraftApi.getCraftingRecipes();
        for (IThaumcraftRecipe recipe : recipes.values()) {
            if (!(recipe instanceof InfusionRecipe)) continue;
            InfusionRecipe ir = (InfusionRecipe) recipe;
            Ingredient input = ir.getRecipeInput();
            NonNullList<Ingredient> components = ir.getComponents();
            if (input == null || components == null) continue;
            if (!input.apply(mainItem)) continue;
            if (auxItems.size() != components.size()) continue;
            if (RecipeMatcher.findMatches(auxItems, components) != null) {
                return ir;
            }
        }
        return null;
    }

    // ---- 辅助方法 ----

    private static boolean isActive(TileEntity te) {
        try {
            return FIELD_ACTIVE.getBoolean(te);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isCrafting(TileEntity te) {
        try {
            return FIELD_CRAFTING.getBoolean(te);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean validLocation(TileEntity te) {
        try {
            return (boolean) METHOD_VALID_LOCATION.invoke(te);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean clearAndCheckPedestals(World world, BlockPos pos, TileEntity te) {
        try {
            METHOD_GET_SURROUNDINGS.invoke(te);
            @SuppressWarnings("unchecked")
            List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);

            // 主材基座必须为空
            BlockPos mainPos = pos.down(2);
            TileEntity mainTe = world.getTileEntity(mainPos);
            if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) return false;
            if (!((ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(mainTe, 0)).isEmpty()) return false;

            // 辅材基座必须为空
            for (BlockPos pPos : pedestals) {
                TileEntity pTe = world.getTileEntity(pPos);
                if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                if (!((ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0)).isEmpty()) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void clearAllPedestals(World world, BlockPos pos, TileEntity te) {
        try {
            METHOD_GET_SURROUNDINGS.invoke(te);
            @SuppressWarnings("unchecked")
            List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);

            // 清空主材基座
            BlockPos mainPos = pos.down(2);
            TileEntity mainTe = world.getTileEntity(mainPos);
            if (CLASS_TILE_PEDESTAL.isInstance(mainTe)) {
                METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, ItemStack.EMPTY);
            }

            // 清空辅材基座
            for (BlockPos pPos : pedestals) {
                TileEntity pTe = world.getTileEntity(pPos);
                if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, ItemStack.EMPTY);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}

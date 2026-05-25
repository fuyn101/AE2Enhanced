package com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
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

    // 配方缓存：BlockPos → InfusionRecipe
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
        return CLASS_TILE_INFUSION_MATRIX.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) return false;

        if (isActive(te) || isCrafting(te)) return false;
        if (!validLocation(te)) return false;

        // 清空并检查基座
        if (!clearAndCheckPedestals(world, pos, te)) return false;

        // 通过 ingredients 推断配方
        InfusionRecipe recipe = findRecipeByIngredients(ingredients);
        if (recipe == null) return false;

        recipeCache.put(pos, recipe);
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) return false;

        InfusionRecipe recipe = recipeCache.get(pos);
        if (recipe == null) {
            recipe = findRecipeByIngredients(ingredients);
            if (recipe == null) return false;
            recipeCache.put(pos, recipe);
        }

        // 清空所有基座
        clearAllPedestals(world, pos, te);

        // 收集可用物品
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        // 找到主材
        Ingredient mainIngredient = recipe.getRecipeInput();
        ItemStack mainItem = ItemStack.EMPTY;
        for (int i = 0; i < available.size(); i++) {
            if (mainIngredient.apply(available.get(i))) {
                mainItem = available.remove(i);
                break;
            }
        }
        if (mainItem.isEmpty()) return false;

        // 放入主材基座（矩阵正下方 2 格）
        BlockPos mainPos = pos.down(2);
        TileEntity mainTe = world.getTileEntity(mainPos);
        if (!CLASS_TILE_PEDESTAL.isInstance(mainTe)) return false;
        try {
            METHOD_PEDESTAL_SET_STACK.invoke(mainTe, 0, mainItem);
        } catch (Exception e) {
            return false;
        }

        // 放入辅材
        NonNullList<Ingredient> components = recipe.getComponents();
        try {
            METHOD_GET_SURROUNDINGS.invoke(te);
            @SuppressWarnings("unchecked")
            List<BlockPos> pedestals = (List<BlockPos>) FIELD_PEDESTALS.get(te);
            int compIdx = 0;
            for (BlockPos pPos : pedestals) {
                if (compIdx >= components.size()) break;
                TileEntity pTe = world.getTileEntity(pPos);
                if (!CLASS_TILE_PEDESTAL.isInstance(pTe)) continue;
                if (!((ItemStack) METHOD_PEDESTAL_GET_STACK.invoke(pTe, 0)).isEmpty()) continue;

                Ingredient ing = components.get(compIdx);
                for (int i = 0; i < available.size(); i++) {
                    if (ing.apply(available.get(i))) {
                        METHOD_PEDESTAL_SET_STACK.invoke(pTe, 0, available.remove(i));
                        compIdx++;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_INFUSION_MATRIX.isInstance(te)) return false;

        InfusionRecipe recipe = recipeCache.get(pos);
        if (recipe == null) return false;

        // 使用 FakePlayer 调用 craftingStart，临时添加研究以跳过进度检查
        if (world instanceof WorldServer) {
            try {
                FakePlayer fakePlayer = FakePlayerFactory.get((WorldServer) world,
                    new GameProfile(UUID.randomUUID(), "[AE2E]"));
                IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(fakePlayer);
                String research = recipe.getResearch();
                boolean added = false;
                if (knowledge != null && research != null && !research.isEmpty()) {
                    added = knowledge.addResearch(research);
                }

                METHOD_CRAFTING_START.invoke(te, fakePlayer);

                // 清理临时研究
                if (added && knowledge != null) {
                    knowledge.removeResearch(research);
                }

                recipeCache.remove(pos);
                return true;
            } catch (Exception e) {
                // FakePlayer 方案失败，回退到手动触发
                recipeCache.remove(pos);
                return forceStartCrafting(world, pos, te, recipe);
            }
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

    // ---- 配方查找 ----

    private static InfusionRecipe findRecipeByIngredients(InventoryCrafting ingredients) {
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        Map<net.minecraft.util.ResourceLocation, IThaumcraftRecipe> recipes = ThaumcraftApi.getCraftingRecipes();
        for (IThaumcraftRecipe recipe : recipes.values()) {
            if (!(recipe instanceof InfusionRecipe)) continue;
            InfusionRecipe ir = (InfusionRecipe) recipe;

            Ingredient input = ir.getRecipeInput();
            NonNullList<Ingredient> components = ir.getComponents();
            if (input == null || components == null) continue;

            // 需要 1 个主材 + N 个辅材
            if (available.size() != 1 + components.size()) continue;

            // 找到主材
            ItemStack mainItem = null;
            List<ItemStack> auxItems = new ArrayList<>();
            for (ItemStack stack : available) {
                if (input.apply(stack) && mainItem == null) {
                    mainItem = stack;
                } else {
                    auxItems.add(stack);
                }
            }
            if (mainItem == null) continue;

            // 检查辅材是否匹配
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

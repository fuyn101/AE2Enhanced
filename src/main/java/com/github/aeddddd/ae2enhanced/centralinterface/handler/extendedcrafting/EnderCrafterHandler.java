package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extended Crafting 末影工作台远程处理器。
 *
 * <p>末影工作台为 3×3 合成网格，材料放入后自动开始合成，
 * 需要周围存在 Ender Alternator 才能推进进度。
 * 合成完成后产物留在 result 槽，matrix 中未消耗的物品保留。</p>
 *
 * <p>配方匹配策略（类似星辉工作台）：
 * {@code canStart} 中将 ingredients 临时放入 matrix，
 * 通过 {@code EnderCrafterRecipeManager.findMatchingRecipe} 验证有序配方是否匹配，
 * 匹配成功则缓存配方；{@code pushMaterials} 按槽位精确放置。</p>
 */
public class EnderCrafterHandler implements IRemoteHandler {

    private static final String BLOCK_ID = "extendedcrafting:ender_crafter";

    private static Class<?> CLASS_TILE_ENDER_CRAFTER;
    private static Class<?> CLASS_ABSTRACT_EXTENDED_TABLE;
    private static Class<?> CLASS_RECIPE_MANAGER;
    private static Class<?> CLASS_TABLE_CRAFTING;
    private static Method METHOD_GET_PROGRESS;
    private static Method METHOD_GET_PROGRESS_REQUIRED;
    private static Method METHOD_GET_ALTERNATOR_POSITIONS;
    private static Method METHOD_GET_RESULT;
    private static Method METHOD_SET_RESULT;
    private static Method METHOD_GET_MATRIX;
    private static Method METHOD_RECIPE_MANAGER_GET_INSTANCE;
    private static Method METHOD_FIND_MATCHING_RECIPE;
    private static Constructor<?> CTOR_TABLE_CRAFTING;
    private static boolean reflectionReady = false;

    // 配方缓存：BlockPos → IEnderCraftingRecipe（canStart 与 pushMaterials 之间传递）
    private final Map<BlockPos, Object> recipeCache = new ConcurrentHashMap<>();

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_ENDER_CRAFTER = Class.forName("com.blakebr0.extendedcrafting.tile.TileEnderCrafter");
            CLASS_ABSTRACT_EXTENDED_TABLE = Class.forName("com.blakebr0.extendedcrafting.tile.AbstractExtendedTable");
            CLASS_RECIPE_MANAGER = Class.forName("com.blakebr0.extendedcrafting.crafting.endercrafter.EnderCrafterRecipeManager");
            CLASS_TABLE_CRAFTING = Class.forName("com.blakebr0.extendedcrafting.crafting.table.TableCrafting");

            METHOD_GET_PROGRESS = CLASS_TILE_ENDER_CRAFTER.getMethod("getProgress");
            METHOD_GET_PROGRESS_REQUIRED = CLASS_TILE_ENDER_CRAFTER.getMethod("getProgressRequired");
            METHOD_GET_ALTERNATOR_POSITIONS = CLASS_TILE_ENDER_CRAFTER.getMethod("getAlternatorPositions");

            METHOD_GET_RESULT = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("getResult");
            METHOD_SET_RESULT = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("setResult", ItemStack.class);
            METHOD_GET_MATRIX = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("getMatrix");

            METHOD_RECIPE_MANAGER_GET_INSTANCE = CLASS_RECIPE_MANAGER.getMethod("getInstance");
            METHOD_FIND_MATCHING_RECIPE = CLASS_RECIPE_MANAGER.getMethod("findMatchingRecipe", CLASS_TABLE_CRAFTING, World.class);

            Class<?> iExtendedTableClass = Class.forName("com.blakebr0.extendedcrafting.lib.IExtendedTable");
            CTOR_TABLE_CRAFTING = CLASS_TABLE_CRAFTING.getConstructor(Container.class, iExtendedTableClass);

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] EnderCrafterHandler reflection init failed", e);
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
        return CLASS_TILE_ENDER_CRAFTER.isInstance(te);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return false;

        try {
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);

            // matrix 必须全部为空
            for (ItemStack stack : matrix) {
                if (!stack.isEmpty()) return false;
            }

            // result 必须为空
            ItemStack result = (ItemStack) METHOD_GET_RESULT.invoke(te);
            if (!result.isEmpty()) return false;

            // 周围必须有 Ender Alternator，否则 progress 不会增加
            List<BlockPos> alternators = (List<BlockPos>) METHOD_GET_ALTERNATOR_POSITIONS.invoke(te);
            if (alternators == null || alternators.isEmpty()) return false;

            // 保存当前 matrix 状态（此时全空，直接记录引用即可，恢复时清空）
            // 将 ingredients 按索引放入 matrix，模拟真实 push
            for (int i = 0; i < ingredients.getSizeInventory() && i < matrix.size(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    matrix.set(i, stack.copy());
                }
            }

            // 创建 TableCrafting 并查找配方
            Container dummyContainer = new Container() {
                @Override
                public boolean canInteractWith(EntityPlayer playerIn) {
                    return false;
                }
            };
            Object tableCrafting = CTOR_TABLE_CRAFTING.newInstance(dummyContainer, te);
            Object recipeManager = METHOD_RECIPE_MANAGER_GET_INSTANCE.invoke(null);
            Object recipe = METHOD_FIND_MATCHING_RECIPE.invoke(recipeManager, tableCrafting, world);

            // 无论是否匹配，恢复 matrix（清空）
            for (int i = 0; i < matrix.size(); i++) {
                matrix.set(i, ItemStack.EMPTY);
            }
            te.markDirty();

            if (recipe != null) {
                recipeCache.put(pos, recipe);
                return true;
            }
        } catch (Exception e) {
            // 异常时尝试清空 matrix 防止残留
            try {
                List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);
                for (int i = 0; i < matrix.size(); i++) {
                    matrix.set(i, ItemStack.EMPTY);
                }
                te.markDirty();
            } catch (Exception ignored) {}
            return false;
        }

        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return false;

        try {
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);

            // 按 AE 样板槽位顺序精确放入 matrix（3×3 一一对应）
            for (int i = 0; i < ingredients.getSizeInventory() && i < matrix.size(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    matrix.set(i, stack.copy());
                }
            }

            te.markDirty();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // 末影工作台自动检测配方并推进进度，无需显式启动
        recipeCache.remove(pos);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return result;

        try {
            // 收集产物（result 槽）
            ItemStack product = (ItemStack) METHOD_GET_RESULT.invoke(te);
            if (!product.isEmpty()) {
                result.add(product.copy());
                METHOD_SET_RESULT.invoke(te, ItemStack.EMPTY);
            }

            // 收集 matrix 中未消耗的残余物品
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);
            for (int i = 0; i < matrix.size(); i++) {
                ItemStack stack = matrix.get(i);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    matrix.set(i, ItemStack.EMPTY);
                }
            }

            te.markDirty();
        } catch (Exception e) {
            // ignore
        }

        return result;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return false;

        try {
            int progress = (int) METHOD_GET_PROGRESS.invoke(te);
            int progressReq = (int) METHOD_GET_PROGRESS_REQUIRED.invoke(te);

            // 合成完成
            if (progressReq > 0 && progress >= progressReq) {
                return true;
            }

            // 空闲且无产物
            if (progress == 0) {
                ItemStack result = (ItemStack) METHOD_GET_RESULT.invoke(te);
                return result.isEmpty();
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return result;

        recipeCache.remove(pos);

        try {
            // 收集 result 槽
            ItemStack product = (ItemStack) METHOD_GET_RESULT.invoke(te);
            if (!product.isEmpty()) {
                result.add(product.copy());
                METHOD_SET_RESULT.invoke(te, ItemStack.EMPTY);
            }

            // 收集 matrix 中所有物品
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);
            for (int i = 0; i < matrix.size(); i++) {
                ItemStack stack = matrix.get(i);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    matrix.set(i, ItemStack.EMPTY);
                }
            }

            te.markDirty();
        } catch (Exception e) {
            // ignore
        }

        return result;
    }
}

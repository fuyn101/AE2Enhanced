package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extended Crafting 末影工作台远程处理器。
 *
 * <p>末影工作台为 3×3 合成网格，材料放入后自动开始合成，
 * 需要周围存在 Ender Alternator 才能推进进度。
 * 合成完成后产物留在 result 槽，matrix 中未消耗的物品保留。</p>
 */
public class EnderCrafterHandler implements IRemoteHandler {

    private static final String BLOCK_ID = "extendedcrafting:ender_crafter";

    private static Class<?> CLASS_TILE_ENDER_CRAFTER;
    private static Class<?> CLASS_ABSTRACT_EXTENDED_TABLE;
    private static Method METHOD_GET_PROGRESS;
    private static Method METHOD_GET_PROGRESS_REQUIRED;
    private static Method METHOD_GET_ALTERNATOR_POSITIONS;
    private static Method METHOD_GET_RESULT;
    private static Method METHOD_SET_RESULT;
    private static Method METHOD_GET_MATRIX;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_ENDER_CRAFTER = Class.forName("com.blakebr0.extendedcrafting.tile.TileEnderCrafter");
            CLASS_ABSTRACT_EXTENDED_TABLE = Class.forName("com.blakebr0.extendedcrafting.tile.AbstractExtendedTable");

            METHOD_GET_PROGRESS = CLASS_TILE_ENDER_CRAFTER.getMethod("getProgress");
            METHOD_GET_PROGRESS_REQUIRED = CLASS_TILE_ENDER_CRAFTER.getMethod("getProgressRequired");
            METHOD_GET_ALTERNATOR_POSITIONS = CLASS_TILE_ENDER_CRAFTER.getMethod("getAlternatorPositions");

            METHOD_GET_RESULT = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("getResult");
            METHOD_SET_RESULT = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("setResult", ItemStack.class);
            METHOD_GET_MATRIX = CLASS_ABSTRACT_EXTENDED_TABLE.getMethod("getMatrix");

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
            // matrix 必须全部为空
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);
            for (ItemStack stack : matrix) {
                if (!stack.isEmpty()) return false;
            }

            // result 必须为空
            ItemStack result = (ItemStack) METHOD_GET_RESULT.invoke(te);
            if (!result.isEmpty()) return false;

            // 周围必须有 Ender Alternator，否则 progress 不会增加
            List<BlockPos> alternators = (List<BlockPos>) METHOD_GET_ALTERNATOR_POSITIONS.invoke(te);
            if (alternators == null || alternators.isEmpty()) return false;

        } catch (Exception e) {
            return false;
        }

        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ENDER_CRAFTER.isInstance(te)) return false;

        try {
            List<ItemStack> matrix = (List<ItemStack>) METHOD_GET_MATRIX.invoke(te);

            // 按 AE 样板槽位顺序直接放入 matrix（3×3 对应关系）
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

package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended Crafting 合成核心 + 基座处理器。
 *
 * <p>结构：中心 CraftingCore，周围 7×7 水平范围内放置 Pedestal。
 * slot 0 → Core 主材料，slot 1+ → Pedestals 辅助材料（各1个）。</p>
 */
public class ExtendedCraftingHandler implements IRemoteHandler {

    private static final String CORE_ID = "extendedcrafting:crafting_core";

    // 反射缓存
    private static Class<?> CLASS_CORE;
    private static Class<?> CLASS_PEDESTAL;
    private static Field FIELD_PROGRESS;
    private static Method METHOD_LOCATE_PEDESTALS;
    private static Method METHOD_GET_INVENTORY_CORE;
    private static Method METHOD_GET_INVENTORY_PEDESTAL;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_CORE = Class.forName("com.blakebr0.extendedcrafting.tile.TileCraftingCore");
            CLASS_PEDESTAL = Class.forName("com.blakebr0.extendedcrafting.tile.TilePedestal");
            FIELD_PROGRESS = CLASS_CORE.getDeclaredField("progress");
            FIELD_PROGRESS.setAccessible(true);
            METHOD_LOCATE_PEDESTALS = CLASS_CORE.getDeclaredMethod("locatePedestals");
            METHOD_LOCATE_PEDESTALS.setAccessible(true);
            METHOD_GET_INVENTORY_CORE = CLASS_CORE.getMethod("getInventory");
            METHOD_GET_INVENTORY_PEDESTAL = CLASS_PEDESTAL.getMethod("getInventory");
            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ExtendedCrafting reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return CORE_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_CORE.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;

        // Core inventory slot 0 必须为空
        IItemHandler coreInv = getCoreInventory(te);
        if (coreInv == null) return false;
        if (!coreInv.getStackInSlot(0).isEmpty()) return false;

        // 检查周围有足够 pedestals（空或已有匹配材料的也算，简化处理）
        List<BlockPos> pedestalPositions = getPedestalPositions(te);
        int needed = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) needed++;
        }
        return pedestalPositions.size() >= needed;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;

        IItemHandler coreInv = getCoreInventory(te);
        if (coreInv == null) return false;

        // Slot 0 → Core slot 0（主材料）
        ItemStack main = ingredients.getStackInSlot(0);
        if (!main.isEmpty()) {
            ItemStack inserted = coreInv.insertItem(0, main.copy(), false);
            if (!inserted.isEmpty() && inserted.getCount() == main.getCount()) {
                return false;
            }
        }

        // Slot 1+ → Pedestals（每个只放1个）
        List<BlockPos> pedestalPositions = getPedestalPositions(te);
        int pedestalIdx = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 找到下一个 pedestal，只插入到空槽位
            while (pedestalIdx < pedestalPositions.size()) {
                BlockPos pPos = pedestalPositions.get(pedestalIdx);
                TileEntity pTe = world.getTileEntity(pPos);
                if (CLASS_PEDESTAL.isInstance(pTe)) {
                    IItemHandler pInv = getPedestalInventory(pTe);
                    if (pInv != null && pInv.getStackInSlot(0).isEmpty()) {
                        ItemStack single = stack.copy();
                        single.setCount(1);
                        pInv.insertItem(0, single, false);
                        pedestalIdx++;
                        break;
                    }
                }
                pedestalIdx++;
            }
        }
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // EC CraftingCore 是自动检测配方并推进的，不需要显式启动
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        // progress == 0 表示合成已完成（或尚未开始），允许 collectProducts 提取产物
        return getProgress(te) == 0;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return result;
        if (getProgress(te) != 0) return result;

        // 从 Core slot 0 提取产物
        IItemHandler coreInv = getCoreInventory(te);
        if (coreInv != null) {
            ItemStack output = coreInv.extractItem(0, 64, false);
            if (!output.isEmpty()) {
                result.add(output);
            }
        }

        // 清理 Pedestals 中的残余材料（防止干扰下一次合成）
        List<BlockPos> pedestalPositions = getPedestalPositions(te);
        for (BlockPos pPos : pedestalPositions) {
            TileEntity pTe = world.getTileEntity(pPos);
            if (CLASS_PEDESTAL.isInstance(pTe)) {
                IItemHandler pInv = getPedestalInventory(pTe);
                if (pInv != null) {
                    ItemStack residual = pInv.extractItem(0, 64, false);
                    if (!residual.isEmpty()) {
                        result.add(residual);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return result;

        IItemHandler coreInv = getCoreInventory(te);
        if (coreInv != null) {
            for (int i = 0; i < coreInv.getSlots(); i++) {
                ItemStack stack = coreInv.extractItem(i, 64, false);
                if (!stack.isEmpty()) result.add(stack);
            }
        }

        List<BlockPos> pedestalPositions = getPedestalPositions(te);
        for (BlockPos pPos : pedestalPositions) {
            TileEntity pTe = world.getTileEntity(pPos);
            if (CLASS_PEDESTAL.isInstance(pTe)) {
                IItemHandler pInv = getPedestalInventory(pTe);
                if (pInv != null) {
                    ItemStack stack = pInv.extractItem(0, 64, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
        }
        return result;
    }

    private static int getProgress(TileEntity core) {
        try {
            return (int) FIELD_PROGRESS.get(core);
        } catch (Exception e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BlockPos> getPedestalPositions(TileEntity core) {
        try {
            return (List<BlockPos>) METHOD_LOCATE_PEDESTALS.invoke(core);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static IItemHandler getCoreInventory(TileEntity core) {
        IItemHandler cap = core.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (cap != null) return cap;
        try {
            return (IItemHandler) METHOD_GET_INVENTORY_CORE.invoke(core);
        } catch (Exception e) {
            return null;
        }
    }

    private static IItemHandler getPedestalInventory(TileEntity pedestal) {
        IItemHandler cap = pedestal.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (cap != null) return cap;
        try {
            return (IItemHandler) METHOD_GET_INVENTORY_PEDESTAL.invoke(pedestal);
        } catch (Exception e) {
            return null;
        }
    }
}

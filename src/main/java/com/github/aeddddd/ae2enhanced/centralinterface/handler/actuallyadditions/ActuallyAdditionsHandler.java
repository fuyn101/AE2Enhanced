package com.github.aeddddd.ae2enhanced.centralinterface.handler.actuallyadditions;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Actually Additions Empowerer + Display Stand 处理器。
 *
 * <p>结构：中心 Empowerer，水平4方向距离3格各1个 Display Stand。
 * slot 0 → Empowerer 主材料，slot 1+ → 4个 Display Stands 辅助材料。</p>
 */
public class ActuallyAdditionsHandler implements IRemoteHandler {

    private static final String EMPOWERER_ID = "actuallyadditions:block_empowerer";

    // 反射缓存
    private static Class<?> CLASS_EMPOWERER;
    private static Class<?> CLASS_DISPLAY_STAND;
    private static Method METHOD_GET_NEARBY_STANDS;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_EMPOWERER = Class.forName("de.ellpeck.actuallyadditions.mod.tile.TileEntityEmpowerer");
            CLASS_DISPLAY_STAND = Class.forName("de.ellpeck.actuallyadditions.mod.tile.TileEntityDisplayStand");
            METHOD_GET_NEARBY_STANDS = CLASS_EMPOWERER.getDeclaredMethod("getNearbyStands");
            METHOD_GET_NEARBY_STANDS.setAccessible(true);
            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ActuallyAdditions reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return EMPOWERER_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_EMPOWERER.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return false;

        // 检查 empowerer slot 0 是否为空或可容纳
        IItemHandler empowererInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (empowererInv == null) return false;
        if (!empowererInv.getStackInSlot(0).isEmpty()) return false;

        // 检查周围是否有足够的 display stands
        Object[] stands = getNearbyStands(te);
        if (stands == null) return false;

        int auxCount = ingredients.getSizeInventory() - 1;
        for (int i = 0; i < Math.min(auxCount, stands.length); i++) {
            if (stands[i] == null) return false;
            TileEntity standTe = (TileEntity) stands[i];
            IItemHandler standInv = standTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (standInv == null) return false;
            if (!standInv.getStackInSlot(0).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return false;

        IItemHandler empowererInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (empowererInv == null) return false;

        Object[] stands = getNearbyStands(te);
        if (stands == null) return false;

        // Slot 0 → Empowerer
        ItemStack main = ingredients.getStackInSlot(0);
        if (!main.isEmpty()) {
            ItemStack inserted = empowererInv.insertItem(0, main.copy(), false);
            if (!inserted.isEmpty() && inserted.getCount() == main.getCount()) {
                return false; // 完全没插入
            }
        }

        // Slot 1+ → Display Stands（每stand只放1个）
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int standIdx = i - 1;
            if (standIdx >= stands.length || stands[standIdx] == null) continue;
            TileEntity standTe = (TileEntity) stands[standIdx];
            IItemHandler standInv = standTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (standInv == null) continue;
            // 只插入1个，防止玩家未设置singleItem时出问题
            ItemStack single = stack.copy();
            single.setCount(1);
            standInv.insertItem(0, single, false);
        }
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // Empowerer 是自动检测配方的，不需要显式启动
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return false;
        try {
            int processTime = (int) CLASS_EMPOWERER.getField("processTime").get(te);
            IItemHandler inv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            // 空闲条件：无处理进度，且 slot 0 为空（或不是可能的输入——但合成完成后输出会在这里）
            // 更精确：processTime == 0 即可，因为输出会替换 slot 0，我们 collectProducts 时会取走
            return processTime == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return result;

        IItemHandler inv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (inv == null) return result;

        // 收集 Empowerer slot 0 的产物
        ItemStack output = inv.extractItem(0, 64, false);
        if (!output.isEmpty()) {
            result.add(output);
        }

        // 清理 Display Stands 中未消耗的残余（理论上配方会消耗，但以防万一）
        Object[] stands = getNearbyStands(te);
        if (stands != null) {
            for (Object stand : stands) {
                if (stand == null) continue;
                TileEntity standTe = (TileEntity) stand;
                IItemHandler standInv = standTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (standInv == null) continue;
                ItemStack residual = standInv.extractItem(0, 64, false);
                if (!residual.isEmpty()) {
                    result.add(residual);
                }
            }
        }
        return result;
    }

    private Object[] getNearbyStands(TileEntity empowerer) {
        try {
            Object result = METHOD_GET_NEARBY_STANDS.invoke(empowerer);
            return (Object[]) result;
        } catch (Exception e) {
            return null;
        }
    }
}

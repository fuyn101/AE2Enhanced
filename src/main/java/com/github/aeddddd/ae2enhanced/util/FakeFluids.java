package com.github.aeddddd.ae2enhanced.util;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * 流体假物品的打包/解包工具类。
 * 将 AE2 的 IAEFluidStack 与标准 AE2 的 IAEItemStack 互相转换。
 */
public class FakeFluids {

    /**
     * 将流体栈打包为假物品 IAEItemStack，用于在物品终端中显示。
     */
    public static IAEItemStack packFluid(IAEFluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluidStack() == null) return null;
        FluidStack fluid = fluidStack.getFluidStack();
        ItemStack fakeItem = ItemFluidDrop.createStack(fluid);
        IAEItemStack result = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createStack(fakeItem);
        if (result != null) {
            result.setStackSize(fluidStack.getStackSize());
        }
        return result;
    }

    /**
     * 将假物品 IAEItemStack 还原为流体栈，用于 extract/inject 操作。
     */
    public static IAEFluidStack unpackFluid(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack mcStack = itemStack.createItemStack();
        FluidStack fluid = ItemFluidDrop.getFluidStack(mcStack);
        if (fluid == null) return null;
        IFluidStorageChannel channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        IAEFluidStack result = channel.createStack(fluid);
        if (result != null) {
            result.setStackSize(itemStack.getStackSize());
        }
        return result;
    }
}

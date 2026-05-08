package com.github.aeddddd.ae2enhanced.util;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;

/**
 * 气体假物品的打包/解包工具类。
 * 将 MekanismEnergistics 的 IAEGasStack 与标准 AE2 的 IAEItemStack 互相转换。
 *
 * 注意：本类直接引用 mekeng / Mekanism 的类。
 * 由于位于条件加载的 mixin 配置中，仅在 Mekanism + mekeng 存在时才会被加载。
 */
public class FakeGases {

    public static IAEItemStack packGas(IAEGasStack gasStack) {
        if (gasStack == null || gasStack.getGasStack() == null) return null;
        GasStack gas = gasStack.getGasStack();
        String gasName = gas.getGas().getName();
        ItemStack fakeItem = ItemGasDrop.createStack(gasName, 1);
        IAEItemStack result = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createStack(fakeItem);
        if (result != null) {
            result.setStackSize(gasStack.getStackSize());
        }
        return result;
    }

    public static IAEGasStack unpackGas(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack mcStack = itemStack.createItemStack();
        String gasName = ItemGasDrop.getGasName(mcStack);
        if (gasName == null) return null;
        Gas gas = GasRegistry.getGas(gasName);
        if (gas == null) return null;
        GasStack gasStack = new GasStack(gas, 1);
        IGasStorageChannel channel = AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
        IAEGasStack result = channel.createStack(gasStack);
        if (result != null) {
            result.setStackSize(itemStack.getStackSize());
        }
        return result;
    }
}

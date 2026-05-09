package com.github.aeddddd.ae2enhanced.util;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;

/**
 * 气体假物品的 FakeItemHandler 实现与工具方法。
 *
 * 在模组初始化时调用 FakeGases.init() 注册 handler。
 */
public final class FakeGases {

    public static void init() {
        FakeItemRegister.registerHandler(ItemGasDrop.class, new FakeItemHandler<GasStack, IAEGasStack>() {

            @Override
            public GasStack getStack(ItemStack stack) {
                return ItemGasDrop.getGasStack(stack);
            }

            @Override
            public GasStack getStack(IAEItemStack stack) {
                return stack == null ? null : getStack(stack.createItemStack());
            }

            @Override
            public IAEGasStack getAEStack(ItemStack stack) {
                return getAEStack(AEItemStack.fromItemStack(stack));
            }

            @Override
            public IAEGasStack getAEStack(IAEItemStack stack) {
                if (stack == null) return null;
                GasStack gas = getStack(stack.createItemStack());
                if (gas == null || gas.getGas() == null) return null;
                AEGasStack result = AEGasStack.of(gas);
                if (result != null) {
                    result.setStackSize(stack.getStackSize());
                }
                return result;
            }

            @Override
            public ItemStack packStack(GasStack gas) {
                return ItemGasDrop.createStack(gas);
            }

            @Override
            public IAEItemStack packAEStack(GasStack gas) {
                if (gas == null || gas.amount <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(packStack(gas));
                if (stack == null) return null;
                stack.setStackSize(gas.amount);
                return stack;
            }

            @Override
            public IAEItemStack packAEStackLong(IAEGasStack gas) {
                if (gas == null || gas.getStackSize() <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(
                        ItemGasDrop.createStack(new GasStack(gas.getGas(), 1)));
                if (stack == null) return null;
                stack.setStackSize(gas.getStackSize());
                return stack;
            }
        });
    }

    public static boolean isGasFakeItem(ItemStack stack) {
        return ItemGasDrop.isGasDrop(stack);
    }

    public static ItemStack packGas2Drops(GasStack stack) {
        return FakeItemRegister.packStack(stack, ModItems.GAS_DROP);
    }

    public static IAEItemStack packGas2AEDrops(GasStack stack) {
        return FakeItemRegister.packAEStack(stack, ModItems.GAS_DROP);
    }

    public static IAEItemStack packGas2AEDrops(IAEGasStack stack) {
        return FakeItemRegister.packAEStackLong(stack, ModItems.GAS_DROP);
    }

    // 兼容方法：供 MixinNetworkMonitorGas / MixinNetworkInventoryHandlerGas 调用
    public static IAEItemStack packGas(IAEGasStack gasStack) {
        return packGas2AEDrops(gasStack);
    }

    public static IAEGasStack unpackGas(IAEItemStack itemStack) {
        return FakeItemRegister.getAEStack(itemStack);
    }
}

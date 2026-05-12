package com.github.aeddddd.ae2enhanced.util;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

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

    /**
     * 反射方法：从 JEI 拖动的 GasStack 对象打包为 AE 假物品。
     * 供 GhostIngredientTarget 调用（避免 GhostIngredientTarget 硬引用 Mekanism 类）。
     */
    public static IAEItemStack tryPackJEIGas(Object ingredient) {
        if (!Loader.isModLoaded("mekanism") || ingredient == null) return null;
        try {
            Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
            if (!gasStackClass.isInstance(ingredient)) return null;
            Object gas = ingredient;
            Object gasType = gasStackClass.getMethod("getGas").invoke(gas);
            int amount = (int) gasStackClass.getMethod("amount").invoke(gas);
            Object newGasStack = gasStackClass.getConstructor(gasType.getClass(), int.class)
                    .newInstance(gasType, Math.min(amount, 1000));
            return packGas2AEDrops((GasStack) newGasStack);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射方法：从手持的气体物品（IGasItem）打包为 AE 假物品。
     * 供 GhostIngredientTarget / Container 调用。
     */
    public static IAEItemStack tryPackJEIGasFromItem(ItemStack is) {
        if (!Loader.isModLoaded("mekanism") || is == null || is.isEmpty()) return null;
        try {
            Class<?> gasItemClass = Class.forName("mekanism.api.gas.IGasItem");
            Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
            java.lang.reflect.Field amountField = gasStackClass.getField("amount");
            // 优先尝试 IGasItem
            if (gasItemClass.isInstance(is.getItem())) {
                Object gasItem = is.getItem();
                Object gasStack = gasItemClass.getMethod("getGas", ItemStack.class).invoke(gasItem, is);
                if (gasStack != null) {
                    int amount = amountField.getInt(gasStack);
                    Object gasType = gasStackClass.getMethod("getGas").invoke(gasStack);
                    Object newGasStack = gasStackClass.getConstructor(gasType.getClass(), int.class)
                            .newInstance(gasType, Math.min(amount, 1000));
                    return packGas2AEDrops((GasStack) newGasStack);
                }
            }
            // 回退：从 NBT "mekData" -> "stored" 读取 GasStack
            if (is.hasTagCompound() && is.getTagCompound().hasKey("mekData", 10)) {
                Object mekData = is.getTagCompound().getCompoundTag("mekData");
                if (mekData != null && ((net.minecraft.nbt.NBTTagCompound) mekData).hasKey("stored", 10)) {
                    Object stored = ((net.minecraft.nbt.NBTTagCompound) mekData).getCompoundTag("stored");
                    java.lang.reflect.Method readFromNBT = gasStackClass.getMethod("readFromNBT", net.minecraft.nbt.NBTTagCompound.class);
                    Object gasStack = readFromNBT.invoke(null, stored);
                    if (gasStack != null) {
                        int amount = amountField.getInt(gasStack);
                        if (amount > 0) {
                            Object gasType = gasStackClass.getMethod("getGas").invoke(gasStack);
                            Object newGasStack = gasStackClass.getConstructor(gasType.getClass(), int.class)
                                    .newInstance(gasType, Math.min(amount, 1000));
                            return packGas2AEDrops((GasStack) newGasStack);
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

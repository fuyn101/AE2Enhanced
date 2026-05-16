package com.github.aeddddd.ae2enhanced.util;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * 流体假物品的 FakeItemHandler 实现与工具方法。
 *
 * 在模组初始化时调用 FakeFluids.init() 注册 handler。
 */
public final class FakeFluids {

    public static void init() {
        FakeItemRegister.registerHandler(ItemFluidDrop.class, new FakeItemHandler<FluidStack, IAEFluidStack>() {

            @Override
            public FluidStack getStack(ItemStack stack) {
                return ItemFluidDrop.getFluidStack(stack);
            }

            @Override
            public FluidStack getStack(IAEItemStack stack) {
                return stack == null ? null : getStack(stack.createItemStack());
            }

            @Override
            public IAEFluidStack getAEStack(ItemStack stack) {
                return getAEStack(AEItemStack.fromItemStack(stack));
            }

            @Override
            public IAEFluidStack getAEStack(IAEItemStack stack) {
                if (stack == null) return null;
                FluidStack fluid = getStack(stack.createItemStack());
                if (fluid == null) return null;
                AEFluidStack result = AEFluidStack.fromFluidStack(fluid);
                if (result != null) {
                    result.setStackSize(stack.getStackSize());
                }
                return result;
            }

            @Override
            public ItemStack packStack(FluidStack fluid) {
                return ItemFluidDrop.createStack(fluid);
            }

            @Override
            public IAEItemStack packAEStack(FluidStack fluid) {
                if (fluid == null || fluid.amount <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(packStack(fluid));
                if (stack == null) return null;
                stack.setStackSize(fluid.amount);
                return stack;
            }

            @Override
            public IAEItemStack packAEStackLong(IAEFluidStack fluid) {
                if (fluid == null || fluid.getStackSize() <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(
                        ItemFluidDrop.createStack(new FluidStack(fluid.getFluid(), 1)));
                if (stack == null) return null;
                stack.setStackSize(fluid.getStackSize());
                return stack;
            }
        });
    }

    public static boolean isFluidFakeItem(ItemStack stack) {
        return ItemFluidDrop.isFluidDrop(stack);
    }

    public static ItemStack packFluid2Drops(FluidStack stack) {
        return FakeItemRegister.packStack(stack, ModItems.FLUID_DROP);
    }

    public static IAEItemStack packFluid2AEDrops(FluidStack stack) {
        return FakeItemRegister.packAEStack(stack, ModItems.FLUID_DROP);
    }

    public static IAEItemStack packFluid2AEDrops(IAEFluidStack stack) {
        return FakeItemRegister.packAEStackLong(stack, ModItems.FLUID_DROP);
    }

    // 兼容方法：供 MixinNetworkMonitorFluid / MixinNetworkInventoryHandlerFluid 调用
    public static IAEItemStack packFluid(IAEFluidStack fluidStack) {
        return packFluid2AEDrops(fluidStack);
    }

    public static IAEFluidStack unpackFluid(IAEItemStack itemStack) {
        return FakeItemRegister.getAEStack(itemStack);
    }

    /**
     * 规范化 FluidStack 的 Fluid 引用，确保使用 FluidRegistry 中的 canonical 实例。
     * 这避免了不同来源的 FluidStack 因 Fluid 对象引用不同而导致匹配失败。
     */
    public static FluidStack canonicalizeFluidStack(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return fluidStack;
        String name = fluidStack.getFluid().getName();
        net.minecraftforge.fluids.Fluid canonical = FluidRegistry.getFluid(name);
        if (canonical == null) return fluidStack;
        return new FluidStack(canonical, fluidStack.amount, fluidStack.tag);
    }
}

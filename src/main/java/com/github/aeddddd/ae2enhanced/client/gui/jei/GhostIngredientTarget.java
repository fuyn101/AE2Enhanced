package com.github.aeddddd.ae2enhanced.client.gui.jei;

import ae2.api.storage.data.AEItemKey;
import ae2.container.slot.SlotFake;
import ae2.core.sync.network.NetworkHandler;
import ae2.core.sync.packets.PacketInventoryAction;
import ae2.helpers.InventoryAction;
import ae2.util.item.AEItemKey;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentiaSafe;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import java.awt.Rectangle;
import java.io.IOException;

/**
 * E1a：通用输入总线的 JEI Ghost Ingredient Target.
 * 支持 ItemStack(含流体/气体容器)、FluidStack、GasStack(反射)的拖放.
 */
public class GhostIngredientTarget implements IGhostIngredientHandler.Target<Object> {

    private final int guiLeft;
    private final int guiTop;
    private final Slot slot;

    public GhostIngredientTarget(int guiLeft, int guiTop, Slot slot) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.slot = slot;
    }

    @Nonnull
    @Override
    public Rectangle getArea() {
        return new Rectangle(this.guiLeft + this.slot.xPos, this.guiTop + this.slot.yPos, 16, 16);
    }

    @Override
    public void accept(@Nonnull Object ingredient) {
        AEItemKey aeStack = resolveIngredient(ingredient);
        if (aeStack == null) return;

        try {
            PacketInventoryAction p = new PacketInventoryAction(
                    InventoryAction.PLACE_JEI_GHOST_ITEM,
                    (SlotFake) this.slot,
                    aeStack
            );
            NetworkHandler.instance().sendToServer(p);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static AEItemKey resolveIngredient(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            return resolveItemStack((ItemStack) ingredient);
        }
        if (ingredient instanceof FluidStack) {
            return FakeFluids.packFluid2AEDrops((FluidStack) ingredient);
        }
        // 尝试反射处理 GasStack
        return tryResolveGas(ingredient);
    }

    private static AEItemKey resolveItemStack(ItemStack is) {
        if (is.isEmpty()) return null;

        // 流体容器
        if (is.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            IFluidHandlerItem fh = is.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (fh != null) {
                FluidStack drained = fh.drain(Integer.MAX_VALUE, false);
                if (drained != null && drained.amount > 0) {
                    return FakeFluids.packFluid2AEDrops(drained);
                }
            }
        }

        // 气体物品(反射调用 FakeGases)
        AEItemKey gasStack = tryResolveGasFromItem(is);
        if (gasStack != null) return gasStack;

        // 源质容器(反射调用 FakeEssentias)
        ItemStack essentiaFake = FakeEssentiaSafe.tryConvertContainerToFake(is);
        if (essentiaFake != null && !essentiaFake.isEmpty()) {
            return AEItemKey.fromItemStack(essentiaFake);
        }

        // 普通物品
        return AEItemKey.fromItemStack(is);
    }

    private static AEItemKey tryResolveGas(Object ingredient) {
        try {
            Class<?> fakeGasesClass = Class.forName("com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases");
            return (AEItemKey) fakeGasesClass.getMethod("tryPackJEIGas", Object.class).invoke(null, ingredient);
        } catch (Throwable e) {
            return null;
        }
    }

    private static AEItemKey tryResolveGasFromItem(ItemStack is) {
        try {
            Class<?> fakeGasesClass = Class.forName("com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases");
            return (AEItemKey) fakeGasesClass.getMethod("tryPackJEIGasFromItem", ItemStack.class).invoke(null, is);
        } catch (Throwable e) {
            return null;
        }
    }
}

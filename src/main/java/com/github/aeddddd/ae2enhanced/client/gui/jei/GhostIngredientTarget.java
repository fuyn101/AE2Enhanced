package com.github.aeddddd.ae2enhanced.client.gui.jei;

import ae2.api.stacks.AEItemKey;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import java.awt.Rectangle;

/**
 * E1a：通用输入总线的 JEI Ghost Ingredient Target.
 * 支持 ItemStack(含流体容器) 的拖放.
 *
 * <p>AE2S 迁移：原 {@code ae2.container.slot.SlotFake} 与
 * {@code InventoryAction.PLACE_JEI_GHOST_ITEM} 已不存在。当前保留目标区域
 * 与成分解析，实际放置逻辑暂存根，等待 AE2S 提供等效 API 后再恢复。</p>
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
        // AE2S 目前没有与 PLACE_JEI_GHOST_ITEM 等效的网络包，暂存根。
    }

    public static AEItemKey resolveIngredient(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            return resolveItemStack((ItemStack) ingredient);
        }
        if (ingredient instanceof FluidStack) {
            return resolveFluidStack((FluidStack) ingredient);
        }
        return null;
    }

    private static AEItemKey resolveItemStack(ItemStack is) {
        if (is.isEmpty()) return null;

        // 流体容器
        if (is.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            IFluidHandlerItem fh = is.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (fh != null) {
                FluidStack drained = fh.drain(Integer.MAX_VALUE, false);
                if (drained != null && drained.amount > 0) {
                    return resolveFluidStack(drained);
                }
            }
        }

        return AEItemKey.of(is);
    }

    private static AEItemKey resolveFluidStack(FluidStack fluid) {
        // AE2S 流体 drops / fake items 需要专用工具类，当前项目缺少这些类，暂返回 null。
        return null;
    }
}

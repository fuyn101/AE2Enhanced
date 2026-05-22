package com.github.aeddddd.ae2enhanced.container;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * 高容量库存 —— 突破 ItemStack.getMaxStackSize() 的 64 上限。
 * AppEngInternalInventory / ItemStackHandler 默认的 getStackLimit 会使用
 * Math.min(getSlotLimit(slot), stack.getMaxStackSize())，导致即使 slotLimit
 * 设为 4096，insertItem 仍被截断为 64。此类覆盖 getStackLimit 以移除该限制。
 */
public class HighCapacityInventory extends AppEngInternalInventory {

    public HighCapacityInventory(IAEAppEngInventory inventory, int size, int maxStack) {
        super(inventory, size, maxStack);
    }

    @Override
    protected int getStackLimit(int slot, @Nonnull ItemStack stack) {
        return this.getSlotLimit(slot);
    }
}

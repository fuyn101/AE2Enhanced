package com.github.aeddddd.ae2enhanced.client.gui.slot;

import appeng.container.slot.AppEngSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * 高容量槽位 —— 突破 ItemStack.getMaxStackSize() 的 64 上限。
 * AppEngSlot 默认的 getItemStackLimit 会使用
 * Math.min(getSlotStackLimit(), stack.getMaxStackSize())，导致 GUI 交互时
 * 每次最多放入 64 个。此类覆盖 getItemStackLimit 以移除该限制。
 */
public class SlotHighCapacity extends AppEngSlot {

    public SlotHighCapacity(IItemHandler inv, int idx, int x, int y) {
        super(inv, idx, x, y);
    }

    @Override
    public int func_178170_b(@Nonnull ItemStack stack) {
        return this.func_75219_a();
    }
}

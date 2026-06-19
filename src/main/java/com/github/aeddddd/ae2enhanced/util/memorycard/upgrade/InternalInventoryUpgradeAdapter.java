package com.github.aeddddd.ae2enhanced.util.memorycard.upgrade;

import ae2.api.inventories.InternalInventory;
import net.minecraft.item.ItemStack;

/**
 * 将 AE2S 的 {@link InternalInventory} 适配为 {@link IUpgradeProvider}.
 * 用于 AE2S Part/Tile 的升级槽操作.
 */
public class InternalInventoryUpgradeAdapter implements IUpgradeProvider {

    private final InternalInventory inventory;

    public InternalInventoryUpgradeAdapter(InternalInventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public int getSlotCount() {
        return inventory != null ? inventory.size() : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.size()) {
            return ItemStack.EMPTY;
        }
        return inventory.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (inventory == null || slot < 0 || slot >= inventory.size()) {
            return;
        }
        inventory.setItemDirect(slot, stack);
    }

    @Override
    public void clearSlots() {
        if (inventory == null) return;
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setItemDirect(i, ItemStack.EMPTY);
        }
    }
}

package com.github.aeddddd.ae2enhanced.util.placement;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * 将 PlacementConfig 包装为 IInventory，供 Container 使用。
 */
public class PlacementConfigInventory implements IInventory {

    private final PlacementConfig config;

    public PlacementConfigInventory(PlacementConfig config) {
        this.config = config;
    }

    @Override
    public int getSizeInventory() {
        return PlacementConfig.TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < PlacementConfig.TOTAL_SLOTS; i++) {
            if (!config.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return config.getStackInSlot(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        config.setStackInSlot(index, ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack stack = config.getStackInSlot(index);
        config.setStackInSlot(index, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (!stack.isEmpty()) {
            stack = stack.copy();
            stack.setCount(1);
        }
        config.setStackInSlot(index, stack);
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory(EntityPlayer player) {}

    @Override
    public void closeInventory(EntityPlayer player) {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {}

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < PlacementConfig.TOTAL_SLOTS; i++) {
            config.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    public String getName() {
        return "PlacementConfig";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentString(getName());
    }
}

package com.github.aeddddd.ae2enhanced.container;

import appeng.container.slot.SlotFakeTypeOnly;
import appeng.tile.inventory.AppEngInternalAEInventory;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * EMC 接口容器.
 */
public class ContainerEMCInterface extends Container {

    private final TileEMCInterface tile;

    public ContainerEMCInterface(InventoryPlayer playerInventory, TileEMCInterface tile) {
        this.tile = tile;

        AppEngInternalAEInventory config = tile.getConfig();
        // 白名单槽 3×3
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int slot = y * 3 + x;
                this.addSlotToContainer(new SlotFakeTypeOnly(config, slot, 62 + x * 18, 20 + y * 18));
            }
        }

        // 玩家背包
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlotToContainer(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    public TileEMCInterface getTile() {
        return tile;
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return tile.getWorld().getTileEntity(tile.getPos()) == tile
                && playerIn.getDistanceSq(tile.getPos().add(0.5, 0.5, 0.5)) <= 64.0;
    }

    @Override
    @Nonnull
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer playerIn, int index) {
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();

        // 白名单槽 -> 背包
        if (index < TileEMCInterface.WHITELIST_SIZE) {
            if (!this.mergeItemStack(stack, TileEMCInterface.WHITELIST_SIZE, this.inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        }
        // 背包 -> 白名单槽
        else {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            for (int i = 0; i < TileEMCInterface.WHITELIST_SIZE; i++) {
                Slot fakeSlot = this.inventorySlots.get(i);
                if (!fakeSlot.getHasStack()) {
                    fakeSlot.putStack(copy);
                    return ItemStack.EMPTY;
                }
            }
            return ItemStack.EMPTY;
        }

        slot.onSlotChanged();
        return stack;
    }
}

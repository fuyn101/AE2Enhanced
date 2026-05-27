package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 智能样板接口的 Container。
 *
 * 槽位布局（暂定，等待 UV 坐标确认后调整）：
 * - 0: 空白样板输入槽
 * - 1: 编码样板输出槽
 * - 2-38: 玩家背包
 */
public class ContainerSmartPatternInterface extends Container {

    private final TileSmartPatternInterface tile;

    public ContainerSmartPatternInterface(InventoryPlayer playerInv, TileSmartPatternInterface tile) {
        this.tile = tile;

        // 空白样板输入槽 (暂定位: 80, 35)
        this.addSlotToContainer(new SlotItemHandler(tile.getInventory(), 0, 80, 35));

        // 编码样板输出槽 (暂定位: 80, 65)
        this.addSlotToContainer(new SlotItemHandler(tile.getInventory(), 1, 80, 65) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return false; // 输出槽不允许放入
            }
        });

        // 玩家背包 (标准布局)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !tile.isInvalid() && player.getDistanceSq(tile.getPos()) <= 64.0;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stackInSlot = slot.getStack();
            itemstack = stackInSlot.copy();
            if (index < 2) {
                // 从 TileEntity 槽位移到玩家背包
                if (!this.mergeItemStack(stackInSlot, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包移到 TileEntity 槽位
                if (!this.mergeItemStack(stackInSlot, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stackInSlot.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }
        return itemstack;
    }

    public TileSmartPatternInterface getTile() {
        return tile;
    }
}

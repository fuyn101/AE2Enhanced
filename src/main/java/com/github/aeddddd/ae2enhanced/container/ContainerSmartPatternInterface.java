package com.github.aeddddd.ae2enhanced.container;

import appeng.container.slot.SlotFake;
import com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern;
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
 * 槽位布局（基于 UV 分析报告）：
 * - 0~44:  配方显示槽位 (9列 x 5行, SlotFake)
 *   行Y: 37, 55, 73, 91, 109
 *   列X: 8, 26, 44, 62, 80, 98, 116, 134, 152
 * - 45:    空白样板输入槽 (8, 127)
 * - 46:    编码样板输出槽 (26, 127)
 * - 47~73: 玩家背包 (3行, Y=141/159/177)
 * - 74~82: 玩家快捷栏 (Y=199)
 */
public class ContainerSmartPatternInterface extends Container {

    private static final int[] RECIPE_COL_X = {8, 26, 44, 62, 80, 98, 116, 134, 152};
    private static final int[] RECIPE_ROW_Y = {37, 55, 73, 91, 109};

    public static final int SLOT_RECIPE_START = 0;
    public static final int SLOT_RECIPE_COUNT = 45;
    public static final int SLOT_BLANK_INPUT = 45;
    public static final int SLOT_ENCODED_OUTPUT = 46;
    public static final int SLOT_PLAYER_START = 47;

    private final TileSmartPatternInterface tile;

    public ContainerSmartPatternInterface(InventoryPlayer playerInv, TileSmartPatternInterface tile) {
        this.tile = tile;

        // 配方显示槽位 (45个 SlotFake)
        int slotIndex = 0;
        for (int row = 0; row < RECIPE_ROW_Y.length; row++) {
            for (int col = 0; col < RECIPE_COL_X.length; col++) {
                this.addSlotToContainer(new SlotFake(
                    tile.getRecipeDisplayInventory(), slotIndex,
                    RECIPE_COL_X[col], RECIPE_ROW_Y[row]
                ));
                slotIndex++;
            }
        }

        // 空白样板输入槽
        this.addSlotToContainer(new SlotItemHandler(tile.getInventory(), 0, 8, 127) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof ItemSmartBlankPattern;
            }
        });

        // 编码样板输出槽
        this.addSlotToContainer(new SlotItemHandler(tile.getInventory(), 1, 26, 127) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }
        });

        // 玩家背包 (3行)
        for (int row = 0; row < 3; row++) {
            int y = 141 + row * 18;
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, y));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 199));
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

            if (index == SLOT_BLANK_INPUT || index == SLOT_ENCODED_OUTPUT) {
                // 从 TileEntity 槽位移到玩家背包
                if (!this.mergeItemStack(stackInSlot, SLOT_PLAYER_START, SLOT_PLAYER_START + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= SLOT_PLAYER_START) {
                // 从玩家背包移到空白样板输入槽
                if (!this.mergeItemStack(stackInSlot, SLOT_BLANK_INPUT, SLOT_BLANK_INPUT + 1, false)) {
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

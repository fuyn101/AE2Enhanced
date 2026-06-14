package com.github.aeddddd.ae2enhanced.container;

import appeng.container.slot.SlotFakeTypeOnly;
import appeng.tile.inventory.AppEngInternalAEInventory;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * EMC 接口容器.
 *
 * <p>复用 3.png 纹理图集风格；每页 7×9 过滤槽，共 10 页。
 * 过滤槽仅允许存在 EMC 值的物品。</p>
 */
public class ContainerEMCInterface extends Container {

    public static final int PAGE_ROWS = 7;
    public static final int PAGE_COLS = 9;
    public static final int SLOTS_PER_PAGE = PAGE_ROWS * PAGE_COLS;

    private final TileEMCInterface tile;
    private int currentPage = 0;

    public ContainerEMCInterface(InventoryPlayer playerInventory, TileEMCInterface tile) {
        this.tile = tile;

        AppEngInternalAEInventory config = tile.getConfig();
        // 创建所有过滤槽，初始只显示第 0 页
        for (int i = 0; i < TileEMCInterface.WHITELIST_SIZE; i++) {
            this.addSlotToContainer(new SlotFakeEMCOnly(config, i, -1000, -1000));
        }
        refreshSlotPositions();

        // 玩家背包
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 174 + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlotToContainer(new Slot(playerInventory, i, 8 + i * 18, 232));
        }
    }

    public TileEMCInterface getTile() {
        return tile;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        if (page < 0) page = 0;
        if (page >= TileEMCInterface.WHITELIST_PAGES) page = TileEMCInterface.WHITELIST_PAGES - 1;
        if (page == this.currentPage) return;
        this.currentPage = page;
        refreshSlotPositions();
    }

    private void refreshSlotPositions() {
        int startSlot = this.currentPage * SLOTS_PER_PAGE;
        for (int i = 0; i < TileEMCInterface.WHITELIST_SIZE; i++) {
            Slot slot = this.inventorySlots.get(i);
            int relative = i - startSlot;
            if (relative >= 0 && relative < SLOTS_PER_PAGE) {
                int x = relative % PAGE_COLS;
                int y = relative / PAGE_COLS;
                slot.xPos = 8 + x * 18;
                slot.yPos = 29 + y * 18;
            } else {
                slot.xPos = -1000;
                slot.yPos = -1000;
            }
        }
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

        // 过滤槽 -> 背包
        if (index < TileEMCInterface.WHITELIST_SIZE) {
            if (!this.mergeItemStack(stack, TileEMCInterface.WHITELIST_SIZE, this.inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        }
        // 背包 -> 过滤槽：仅允许有 EMC 值的物品，放入第一个空槽
        else {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            if (!hasEmc(copy)) return ItemStack.EMPTY;
            for (int i = 0; i < TileEMCInterface.WHITELIST_SIZE; i++) {
                Slot fakeSlot = this.inventorySlots.get(i);
                if (!fakeSlot.getHasStack() && fakeSlot.isItemValid(copy)) {
                    fakeSlot.putStack(copy);
                    return ItemStack.EMPTY;
                }
            }
            return ItemStack.EMPTY;
        }

        slot.onSlotChanged();
        return stack;
    }

    private static boolean hasEmc(@Nonnull ItemStack stack) {
        return ProjectEHelper.isAvailable() && ProjectEHelper.getEmcValue(stack) > 0;
    }

    /**
     * 仅接受存在 EMC 值的假物品槽.
     */
    private static class SlotFakeEMCOnly extends SlotFakeTypeOnly {
        SlotFakeEMCOnly(AppEngInternalAEInventory inv, int idx, int x, int y) {
            super(inv, idx, x, y);
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return hasEmc(stack);
        }
    }
}

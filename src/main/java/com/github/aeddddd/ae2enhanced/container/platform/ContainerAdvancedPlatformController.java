package com.github.aeddddd.ae2enhanced.container.platform;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotFake;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;
import com.github.aeddddd.ae2enhanced.platform.subnet.Subnet;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * 先进中枢平台控制器主 GUI 的 Container。
 *
 * <p>槽位布局：</p>
 * <ul>
 *   <li>0~49:   过滤槽位 (10列 × 5行, SlotFake)</li>
 *   <li>50~76:  玩家背包 (3行)</li>
 *   <li>77~85:  玩家快捷栏</li>
 * </ul>
 */
public class ContainerAdvancedPlatformController extends AEBaseContainer {

    public static final int FILTER_SLOTS = 50;
    public static final int FILTER_COLS = 10;
    public static final int FILTER_ROWS = 5;
    public static final int FILTER_START_X = 77;
    public static final int FILTER_START_Y = 22;
    public static final int FILTER_SPACING = 18;

    public static final int PLAYER_INV_X = 42;
    public static final int PLAYER_INV_Y = 174;

    public static final int SLOT_FILTER_START = 0;
    public static final int SLOT_FILTER_END = 50;
    public static final int SLOT_PLAYER_START = 50;
    public static final int SLOT_HOTBAR_START = 77;

    private final TileAdvancedPlatformController tile;
    private final AppEngInternalInventory filterInventory;
    private final ItemStack[] lastFilterItems;
    private int selectedSubnetId = 0;
    private boolean inputMode = true;

    public ContainerAdvancedPlatformController(InventoryPlayer ip, TileAdvancedPlatformController tile) {
        super(ip, tile, null);
        this.tile = tile;
        this.filterInventory = new AppEngInternalInventory(null, FILTER_SLOTS);
        this.lastFilterItems = new ItemStack[FILTER_SLOTS];
        for (int i = 0; i < FILTER_SLOTS; i++) {
            this.lastFilterItems[i] = ItemStack.EMPTY;
        }

        // 过滤槽位: 10列 × 5行 (SlotFake)
        for (int row = 0; row < FILTER_ROWS; row++) {
            for (int col = 0; col < FILTER_COLS; col++) {
                int index = row * FILTER_COLS + col;
                int x = FILTER_START_X + col * FILTER_SPACING;
                int y = FILTER_START_Y + row * FILTER_SPACING;
                this.addSlotToContainer(new SlotFake(this.filterInventory, index, x, y));
            }
        }

        // 玩家背包 3行
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(ip, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(ip, col,
                    PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    public void setSelectedSubnetId(int subnetId) {
        if (this.selectedSubnetId != subnetId) {
            this.selectedSubnetId = subnetId;
            refreshFilterSlots();
        }
    }

    public void setInputMode(boolean inputMode) {
        if (this.inputMode != inputMode) {
            this.inputMode = inputMode;
            refreshFilterSlots();
        }
    }

    public void refreshFilterSlots() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            this.filterInventory.setStackInSlot(i, ItemStack.EMPTY);
            this.lastFilterItems[i] = ItemStack.EMPTY;
        }
        if (this.selectedSubnetId <= 0) {
            return;
        }
        Subnet subnet = tile.getSubnet(this.selectedSubnetId);
        if (subnet == null) {
            return;
        }
        Set<ItemStackKey> filter = this.inputMode ? subnet.getAllowFromMain() : subnet.getAllowToMain();
        int slot = 0;
        for (ItemStackKey key : filter) {
            if (slot >= FILTER_SLOTS) {
                break;
            }
            this.filterInventory.setStackInSlot(slot, key.toItemStack(1));
            this.lastFilterItems[slot] = key.toItemStack(1);
            slot++;
        }
    }

    @Override
    public void func_75142_b() {
        super.func_75142_b();
        if (Platform.isServer()) {
            boolean changed = false;
            for (int i = 0; i < FILTER_SLOTS; i++) {
                ItemStack current = this.filterInventory.getStackInSlot(i);
                if (!ItemStack.areItemStacksEqual(current, this.lastFilterItems[i])) {
                    changed = true;
                    this.lastFilterItems[i] = current.isEmpty() ? ItemStack.EMPTY : current.copy();
                }
            }
            if (changed) {
                updateTileFromSlots();
            }
        }
    }

    private void updateTileFromSlots() {
        if (this.selectedSubnetId <= 0) {
            return;
        }
        Subnet subnet = tile.getSubnet(this.selectedSubnetId);
        if (subnet == null) {
            return;
        }
        Set<ItemStackKey> filter = this.inputMode ? subnet.getAllowFromMain() : subnet.getAllowToMain();
        Set<ItemStackKey> newFilter = new HashSet<>();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack stack = this.filterInventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                newFilter.add(ItemStackKey.of(stack));
            }
        }
        if (!filter.equals(newFilter)) {
            filter.clear();
            filter.addAll(newFilter);
            tile.markDirty();
            tile.sendPlatformInitToAllViewingPlayers();
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return Platform.hasPermissions(tile.getWorld(), tile.getPos(), player);
    }

    public TileAdvancedPlatformController getTile() {
        return tile;
    }

    public int getSelectedSubnetId() {
        return selectedSubnetId;
    }

    public boolean isInputMode() {
        return inputMode;
    }

    public AppEngInternalInventory getFilterInventory() {
        return filterInventory;
    }
}

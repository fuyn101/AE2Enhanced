package com.github.aeddddd.ae2enhanced.container.platform;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotFake;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;
import com.github.aeddddd.ae2enhanced.platform.zone.FaceIoConfig;
import com.github.aeddddd.ae2enhanced.platform.zone.Zone;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.util.HashSet;
import java.util.Set;

/**
 * 先进中枢平台控制器二级菜单 GUI 的 Container。
 *
 * <p>槽位布局：</p>
 * <ul>
 *   <li>0~49:   IO 配置槽位 (10列 × 5行, SlotFake)</li>
 *   <li>50~76:  玩家背包 (3行)</li>
 *   <li>77~85:  玩家快捷栏</li>
 * </ul>
 */
public class ContainerAdvancedPlatformSubmenu extends AEBaseContainer {

    public static final int IO_CONFIG_SLOTS = 50;
    public static final int IO_CONFIG_COLS = 10;
    public static final int IO_CONFIG_ROWS = 5;
    public static final int IO_START_X = 77;
    public static final int IO_START_Y = 22;
    public static final int IO_SPACING = 18;

    public static final int PLAYER_INV_X = 42;
    public static final int PLAYER_INV_Y = 174;

    public static final int SLOT_IO_START = 0;
    public static final int SLOT_IO_END = 50;
    public static final int SLOT_PLAYER_START = 50;
    public static final int SLOT_HOTBAR_START = 77;

    private final TileAdvancedPlatformController tile;
    private final int selectedSubnetId;
    private final AppEngInternalInventory ioConfigInventory;
    private final ItemStack[] lastIoItems;
    private int selectedZoneId = 0;
    private EnumFacing selectedFace = null;

    public ContainerAdvancedPlatformSubmenu(InventoryPlayer ip, TileAdvancedPlatformController tile, int selectedSubnetId) {
        super(ip, tile, null);
        this.tile = tile;
        this.selectedSubnetId = selectedSubnetId;
        this.ioConfigInventory = new AppEngInternalInventory(null, IO_CONFIG_SLOTS);
        this.lastIoItems = new ItemStack[IO_CONFIG_SLOTS];
        for (int i = 0; i < IO_CONFIG_SLOTS; i++) {
            this.lastIoItems[i] = ItemStack.EMPTY;
        }

        // IO 配置槽位: 10列 × 5行 (SlotFake)
        for (int row = 0; row < IO_CONFIG_ROWS; row++) {
            for (int col = 0; col < IO_CONFIG_COLS; col++) {
                int index = row * IO_CONFIG_COLS + col;
                int x = IO_START_X + col * IO_SPACING;
                int y = IO_START_Y + row * IO_SPACING;
                this.addSlotToContainer(new SlotFake(this.ioConfigInventory, index, x, y));
            }
        }


    }

    public void setSelectedZoneId(int zoneId) {
        if (this.selectedZoneId != zoneId) {
            this.selectedZoneId = zoneId;
            refreshIoConfigSlots();
        }
    }

    public void setSelectedFace(EnumFacing face) {
        if (this.selectedFace != face) {
            this.selectedFace = face;
            refreshIoConfigSlots();
        }
    }

    public void refreshIoConfigSlots() {
        for (int i = 0; i < IO_CONFIG_SLOTS; i++) {
            this.ioConfigInventory.setStackInSlot(i, ItemStack.EMPTY);
            this.lastIoItems[i] = ItemStack.EMPTY;
        }
        if (this.selectedZoneId <= 0 || this.selectedFace == null) {
            return;
        }
        Zone zone = tile.getZoneRegistry().getZone(this.selectedZoneId);
        if (zone == null) {
            return;
        }
        FaceIoConfig config = zone.getFaceIo().get(this.selectedFace);
        if (config == null) {
            return;
        }
        int slot = 0;
        for (ItemStackKey key : config.getFilter()) {
            if (slot >= IO_CONFIG_SLOTS) {
                break;
            }
            this.ioConfigInventory.setStackInSlot(slot, key.toItemStack(1));
            this.lastIoItems[slot] = key.toItemStack(1);
            slot++;
        }
    }

    @Override
    public void func_75142_b() {
        super.func_75142_b();
        if (Platform.isServer()) {
            boolean changed = false;
            for (int i = 0; i < IO_CONFIG_SLOTS; i++) {
                ItemStack current = this.ioConfigInventory.getStackInSlot(i);
                if (!ItemStack.areItemStacksEqual(current, this.lastIoItems[i])) {
                    changed = true;
                    this.lastIoItems[i] = current.isEmpty() ? ItemStack.EMPTY : current.copy();
                }
            }
            if (changed) {
                updateTileFromIoSlots();
            }
        }
    }

    private void updateTileFromIoSlots() {
        if (this.selectedZoneId <= 0 || this.selectedFace == null) {
            return;
        }
        Zone zone = tile.getZoneRegistry().getZone(this.selectedZoneId);
        if (zone == null) {
            return;
        }
        FaceIoConfig config = zone.getFaceIo().get(this.selectedFace);
        if (config == null) {
            return;
        }
        Set<ItemStackKey> newFilter = new HashSet<>();
        for (int i = 0; i < IO_CONFIG_SLOTS; i++) {
            ItemStack stack = this.ioConfigInventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                newFilter.add(ItemStackKey.of(stack));
            }
        }
        if (!config.getFilter().equals(newFilter)) {
            config.getFilter().clear();
            config.getFilter().addAll(newFilter);
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

    public int getSelectedZoneId() {
        return selectedZoneId;
    }

    public EnumFacing getSelectedFace() {
        return selectedFace;
    }

    public AppEngInternalInventory getIoConfigInventory() {
        return ioConfigInventory;
    }
}

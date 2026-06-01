package com.github.aeddddd.ae2enhanced.container.platform;

import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

/**
 * 先进中枢平台控制器二级菜单 GUI 的 Container。
 *
 * <p>槽位布局：</p>
 * <ul>
 *   <li>0~49:   IO 配置槽位 (10列 × 5行, AppEngSlot)</li>
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

    public ContainerAdvancedPlatformSubmenu(InventoryPlayer ip, TileAdvancedPlatformController tile, int selectedSubnetId) {
        super(ip, tile, null);
        this.tile = tile;
        this.selectedSubnetId = selectedSubnetId;
        this.ioConfigInventory = new AppEngInternalInventory(null, IO_CONFIG_SLOTS);

        // IO 配置槽位: 10列 × 5行
        for (int row = 0; row < IO_CONFIG_ROWS; row++) {
            for (int col = 0; col < IO_CONFIG_COLS; col++) {
                int index = row * IO_CONFIG_COLS + col;
                int x = IO_START_X + col * IO_SPACING;
                int y = IO_START_Y + row * IO_SPACING;
                this.addSlotToContainer(new AppEngSlot(this.ioConfigInventory, index, x, y));
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

    public AppEngInternalInventory getIoConfigInventory() {
        return ioConfigInventory;
    }
}

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
 * 先进中枢平台控制器主 GUI 的 Container。
 *
 * <p>槽位布局：</p>
 * <ul>
 *   <li>0~49:   过滤槽位 (10列 × 5行, AppEngSlot)</li>
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

    public ContainerAdvancedPlatformController(InventoryPlayer ip, TileAdvancedPlatformController tile) {
        super(ip, tile, null);
        this.tile = tile;
        this.filterInventory = new AppEngInternalInventory(null, FILTER_SLOTS);

        // 过滤槽位: 10列 × 5行
        for (int row = 0; row < FILTER_ROWS; row++) {
            for (int col = 0; col < FILTER_COLS; col++) {
                int index = row * FILTER_COLS + col;
                int x = FILTER_START_X + col * FILTER_SPACING;
                int y = FILTER_START_Y + row * FILTER_SPACING;
                this.addSlotToContainer(new AppEngSlot(this.filterInventory, index, x, y));
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

    public AppEngInternalInventory getFilterInventory() {
        return filterInventory;
    }
}

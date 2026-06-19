package com.github.aeddddd.ae2enhanced.container;

import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.inventories.InternalInventory;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.container.guisync.GuiSync;
import ae2.container.implementations.UpgradeableContainer;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.OptionalFakeSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.util.Platform;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * 先进 ME 收集器的 Container.
 *
 * <p>7×9 过滤槽(容量卡解锁后 5 行),5 个升级槽.</p>
 */
public class ContainerAdvancedMECollector extends UpgradeableContainer<TileAdvancedMECollector> {

    private final TileAdvancedMECollector tile;

    @GuiSync(value = 10)
    public int range = 2;

    @GuiSync(value = 11)
    public int sideLength = 5;

    public ContainerAdvancedMECollector(InventoryPlayer ip, TileAdvancedMECollector tile) {
        super(ip, tile);
        this.tile = tile;
    }

    @Override
    protected void setupConfig() {
        InternalInventory config = this.getHost().getConfig();
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                if (y < 2) {
                    this.addSlotToContainer(new FakeSlot(config, y * 9 + x, 8 + x * 18, 29 + y * 18));
                } else {
                    this.addSlotToContainer(new OptionalFakeSlot(config, this, y * 9 + x, 8 + x * 18, 29 + y * 18, y - 2));
                }
            }
        }

        IUpgradeInventory upgrades = this.getHost().getUpgrades();
        this.addSlotToContainer(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, 0, 187, 8).setNotDraggable());
        this.addSlotToContainer(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, 1, 187, 26).setNotDraggable());
        this.addSlotToContainer(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, 2, 187, 44).setNotDraggable());
        this.addSlotToContainer(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, 3, 187, 62).setNotDraggable());
        this.addSlotToContainer(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, 4, 187, 80).setNotDraggable());
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        int capacityUpgrades = getInstalledCapacityUpgrades();
        return idx < capacityUpgrades;
    }

    private int getInstalledCapacityUpgrades() {
        int count = 0;
        IUpgradeInventory upgrades = this.getHost().getUpgrades();
        if (ItemRegistry.UPGRADE_CARD != null) {
            for (int i = 0; i < upgrades.size(); i++) {
                net.minecraft.item.ItemStack stack = upgrades.getStackInSlot(i);
                if (!stack.isEmpty()
                        && stack.getItem() == ItemRegistry.UPGRADE_CARD
                        && stack.getMetadata() == ItemUpgradeCard.META_CAPACITY) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    @Override
    public void broadcastChanges() {
        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getHost().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setCraftingMode((YesNo) this.getHost().getConfigManager().getSetting(Settings.CRAFT_ONLY));
            this.range = this.tile.getRange();
            this.sideLength = this.tile.getActualSideLength();
        }
        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return Platform.hasPermissions(new ae2.api.util.DimensionalBlockPos(this.tile), player);
    }

    public TileAdvancedMECollector getTile() {
        return this.tile;
    }
}

package com.github.aeddddd.ae2enhanced.container;

import ae2.api.config.FuzzyMode;
import ae2.api.config.Settings;
import ae2.api.config.YesNo;
import ae2.api.inventories.InternalInventory;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.Upgrades;
import ae2.container.guisync.GuiSync;
import ae2.container.implementations.UpgradeableContainer;
import ae2.container.slot.FakeSlot;
import ae2.container.slot.IOptionalSlotHost;
import ae2.container.slot.OptionalFakeSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.util.Platform;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * 先进 ME 收集器的 Container.
 *
 * <p>7×9 过滤槽(容量卡解锁后 5 行),5 个升级槽.</p>
 */
public class ContainerAdvancedMECollector extends UpgradeableContainer<TileAdvancedMECollector> implements IOptionalSlotHost {

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
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        InternalInventory config = this.getHost().getConfig();
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                if (y < 2) {
                    this.addSlotToContainer(new FakeSlot(config, y * 9 + x, 8 + x * 18, 29 + y * 18));
                } else {
                    this.addSlotToContainer(new OptionalFakeSlot(config, this, y * 9 + x, 8, 29, x, y, y - 2));
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
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        int capacityUpgrades = this.getHost().getInstalledUpgrades(Upgrades.CAPACITY);
        return idx < capacityUpgrades;
    }

    @Override
    public void func_75142_b() {
        this.verifyPermissions(ae2.api.config.SecurityPermissions.BUILD, false);
        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getHost().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setCraftingMode((YesNo) this.getHost().getConfigManager().getSetting(Settings.CRAFT_ONLY));
            this.range = this.tile.getRange();
            this.sideLength = this.tile.getActualSideLength();
        }
        this.standardDetectAndSendChanges();
    }

    public TileAdvancedMECollector getTile() {
        return this.tile;
    }
}

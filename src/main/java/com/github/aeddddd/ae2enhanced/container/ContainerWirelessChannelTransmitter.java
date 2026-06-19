package com.github.aeddddd.ae2enhanced.container;

import ae2.api.inventories.InternalInventory;
import ae2.container.AEBaseContainer;
import ae2.container.slot.AppEngSlot;
import ae2.util.Platform;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * F1a：无线频道发生器 GUI 的 Container.
 */
public class ContainerWirelessChannelTransmitter extends AEBaseContainer {

    private final TileWirelessChannelTransmitter tile;

    public ContainerWirelessChannelTransmitter(InventoryPlayer ip, TileWirelessChannelTransmitter tile) {
        super(ip, tile);
        this.tile = tile;

        final net.minecraftforge.items.ItemStackHandler handler = tile.getInventory();
        InternalInventory inv = new InternalInventory() {
            @Override
            public int size() {
                return handler.getSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return handler.getStackInSlot(slot);
            }

            @Override
            public void setItemDirect(int slot, ItemStack stack) {
                handler.setStackInSlot(slot, stack);
            }
        };

        this.addSlotToContainer(new AppEngSlot(inv, TileWirelessChannelTransmitter.SLOT_CARD, 81, 48));

        this.addPlayerInventorySlots(8, 84);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return Platform.hasPermissions(new ae2.api.util.DimensionalBlockPos(this.tile), player);
    }

    public TileWirelessChannelTransmitter getTile() {
        return this.tile;
    }
}

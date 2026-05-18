package com.github.aeddddd.ae2enhanced.container;

import appeng.container.AEBaseContainer;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.part.PartWirelessChannelTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/**
 * F1a：无线频道发生器 GUI 的 Container。
 */
public class ContainerWirelessChannelTransmitter extends AEBaseContainer {

    private final PartWirelessChannelTransmitter part;

    public ContainerWirelessChannelTransmitter(InventoryPlayer ip, PartWirelessChannelTransmitter part) {
        super(ip, part.getTile(), part);
        this.part = part;

        this.addSlotToContainer(new SlotItemHandler(part.getInventory(), PartWirelessChannelTransmitter.SLOT_INPUT, 44, 35) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof ItemChannelReceiverCard && !ItemChannelReceiverCard.isBound(stack);
            }
        });

        this.addSlotToContainer(new SlotItemHandler(part.getInventory(), PartWirelessChannelTransmitter.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }
        });

        this.bindPlayerInventory(ip, 0, 84);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return Platform.hasPermissions(this.getTileEntity().getWorld(), this.getTileEntity().getPos(), player);
    }
}

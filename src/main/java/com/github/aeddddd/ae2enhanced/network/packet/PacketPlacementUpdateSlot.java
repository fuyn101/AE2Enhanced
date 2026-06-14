package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 客户端通知服务端更新放置工具幽灵槽。
 */
public class PacketPlacementUpdateSlot implements IMessage {

    private int slotIndex;
    private ItemStack stack;

    public PacketPlacementUpdateSlot() {
    }

    public PacketPlacementUpdateSlot(int slotIndex, ItemStack stack) {
        this.slotIndex = slotIndex;
        this.stack = stack;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.slotIndex = buf.readInt();
        this.stack = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slotIndex);
        ByteBufUtils.writeItemStack(buf, stack);
    }
}

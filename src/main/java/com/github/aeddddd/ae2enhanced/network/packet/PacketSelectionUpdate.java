package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * P0 占位包 —— 选区更新同步。
 */
public class PacketSelectionUpdate implements IMessage {

    public PacketSelectionUpdate() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketSelectionUpdate, IMessage> {

        @Override
        public IMessage onMessage(PacketSelectionUpdate message, MessageContext ctx) {
            return null;
        }
    }
}

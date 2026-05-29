package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * P0 占位包 —— RTS 视角状态变更同步。
 */
public class PacketRTSStateChange implements IMessage {

    public PacketRTSStateChange() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketRTSStateChange, IMessage> {

        @Override
        public IMessage onMessage(PacketRTSStateChange message, MessageContext ctx) {
            return null;
        }
    }
}

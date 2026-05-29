package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PacketPlatformGenerateResult implements IMessage {

    private boolean success;
    private List<BlockPos> conflicts;

    public PacketPlatformGenerateResult() {
    }

    public PacketPlatformGenerateResult(boolean success, List<BlockPos> conflicts) {
        this.success = success;
        this.conflicts = conflicts != null ? conflicts : Collections.emptyList();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.success = buf.readBoolean();
        int count = buf.readInt();
        this.conflicts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            conflicts.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeInt(conflicts.size());
        for (BlockPos pos : conflicts) {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        }
    }

    public static class Handler implements IMessageHandler<PacketPlatformGenerateResult, IMessage> {

        @Override
        public IMessage onMessage(PacketPlatformGenerateResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (!message.success && Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendMessage(
                            new net.minecraft.util.text.TextComponentString(
                                    I18n.format("message.ae2enhanced.platform.conflict", message.conflicts.size())));
                }
            });
            return null;
        }
    }
}

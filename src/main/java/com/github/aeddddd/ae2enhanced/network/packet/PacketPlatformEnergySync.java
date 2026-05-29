package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.client.platform.ClientPlatformState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPlatformEnergySync implements IMessage {

    private BlockPos controllerPos;
    private long rfBuffer;
    private long rfBufferCapacity;
    private long networkEnergyStored;

    public PacketPlatformEnergySync() {
    }

    public PacketPlatformEnergySync(BlockPos pos, long buffer, long capacity, long networkStored) {
        this.controllerPos = pos;
        this.rfBuffer = buffer;
        this.rfBufferCapacity = capacity;
        this.networkEnergyStored = networkStored;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.controllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.rfBuffer = buf.readLong();
        this.rfBufferCapacity = buf.readLong();
        this.networkEnergyStored = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(controllerPos.getX());
        buf.writeInt(controllerPos.getY());
        buf.writeInt(controllerPos.getZ());
        buf.writeLong(rfBuffer);
        buf.writeLong(rfBufferCapacity);
        buf.writeLong(networkEnergyStored);
    }

    public static class Handler implements IMessageHandler<PacketPlatformEnergySync, IMessage> {

        @Override
        public IMessage onMessage(PacketPlatformEnergySync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientPlatformState.updateEnergy(message.controllerPos,
                        message.rfBuffer, message.rfBufferCapacity, message.networkEnergyStored);
            });
            return null;
        }
    }
}

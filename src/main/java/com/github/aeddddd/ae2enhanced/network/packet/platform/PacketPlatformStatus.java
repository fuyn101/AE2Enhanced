package com.github.aeddddd.ae2enhanced.network.packet.platform;

import com.github.aeddddd.ae2enhanced.client.platform.ClientPlatformState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * S→C packet for periodic status sync (subnet storage usage).
 */
public class PacketPlatformStatus implements IMessage {

    private Map<Integer, Long> subnetStorageUsage;
    private BlockPos controllerPos;

    public PacketPlatformStatus() {
        this.subnetStorageUsage = new HashMap<>();
    }

    public PacketPlatformStatus(BlockPos controllerPos, Map<Integer, Long> subnetStorageUsage) {
        this.controllerPos = controllerPos;
        this.subnetStorageUsage = subnetStorageUsage != null ? subnetStorageUsage : new HashMap<>();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.controllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        int count = buf.readInt();
        this.subnetStorageUsage = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int subnetId = buf.readInt();
            long usage = buf.readLong();
            this.subnetStorageUsage.put(subnetId, usage);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(controllerPos.getX());
        buf.writeInt(controllerPos.getY());
        buf.writeInt(controllerPos.getZ());
        buf.writeInt(subnetStorageUsage.size());
        for (Map.Entry<Integer, Long> entry : subnetStorageUsage.entrySet()) {
            buf.writeInt(entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public Map<Integer, Long> getSubnetStorageUsage() {
        return subnetStorageUsage;
    }

    public static class Handler implements IMessageHandler<PacketPlatformStatus, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketPlatformStatus message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ClientPlatformState.updatePlatformStatus(message.controllerPos, message.subnetStorageUsage);
            });
            return null;
        }
    }
}

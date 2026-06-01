package com.github.aeddddd.ae2enhanced.network.packet.platform;

import com.github.aeddddd.ae2enhanced.client.platform.ClientPlatformState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * S→C packet sending subnet list and zone list summary.
 */
public class PacketPlatformInit implements IMessage {

    private List<SubnetData> subnets;
    private List<ZoneSummary> zones;
    private BlockPos controllerPos;

    public PacketPlatformInit() {
        this.subnets = new ArrayList<>();
        this.zones = new ArrayList<>();
    }

    public PacketPlatformInit(BlockPos controllerPos, List<SubnetData> subnets, List<ZoneSummary> zones) {
        this.controllerPos = controllerPos;
        this.subnets = subnets != null ? subnets : new ArrayList<>();
        this.zones = zones != null ? zones : new ArrayList<>();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.controllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        int subnetCount = buf.readInt();
        this.subnets = new ArrayList<>();
        for (int i = 0; i < subnetCount; i++) {
            int id = buf.readInt();
            String name = ByteBufUtils.readUTF8String(buf);
            this.subnets.add(new SubnetData(id, name));
        }
        int zoneCount = buf.readInt();
        this.zones = new ArrayList<>();
        for (int i = 0; i < zoneCount; i++) {
            int id = buf.readInt();
            String name = ByteBufUtils.readUTF8String(buf);
            int subnetId = buf.readInt();
            int blockCount = buf.readInt();
            int[] faceModes = new int[6];
            for (int j = 0; j < 6; j++) {
                faceModes[j] = buf.readByte();
            }
            this.zones.add(new ZoneSummary(id, name, subnetId, blockCount, faceModes));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(controllerPos.getX());
        buf.writeInt(controllerPos.getY());
        buf.writeInt(controllerPos.getZ());
        buf.writeInt(subnets.size());
        for (SubnetData subnet : subnets) {
            buf.writeInt(subnet.id);
            ByteBufUtils.writeUTF8String(buf, subnet.name);
        }
        buf.writeInt(zones.size());
        for (ZoneSummary zone : zones) {
            buf.writeInt(zone.id);
            ByteBufUtils.writeUTF8String(buf, zone.name);
            buf.writeInt(zone.subnetId);
            buf.writeInt(zone.blockCount);
            for (int j = 0; j < 6; j++) {
                buf.writeByte(zone.faceModes[j]);
            }
        }
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public List<SubnetData> getSubnets() {
        return subnets;
    }

    public List<ZoneSummary> getZones() {
        return zones;
    }

    public static class SubnetData {
        public final int id;
        public final String name;

        public SubnetData(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class ZoneSummary {
        public final int id;
        public final String name;
        public final int subnetId;
        public final int blockCount;
        public final int[] faceModes;

        public ZoneSummary(int id, String name, int subnetId, int blockCount) {
            this(id, name, subnetId, blockCount, new int[6]);
        }

        public ZoneSummary(int id, String name, int subnetId, int blockCount, int[] faceModes) {
            this.id = id;
            this.name = name;
            this.subnetId = subnetId;
            this.blockCount = blockCount;
            this.faceModes = faceModes != null ? faceModes : new int[6];
        }
    }

    public static class Handler implements IMessageHandler<PacketPlatformInit, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketPlatformInit message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                List<ClientPlatformState.SubnetData> subnets = new ArrayList<>();
                for (SubnetData sd : message.subnets) {
                    subnets.add(new ClientPlatformState.SubnetData(sd.id, sd.name));
                }
                List<ClientPlatformState.ZoneSummary> zones = new ArrayList<>();
                for (ZoneSummary zs : message.zones) {
                    zones.add(new ClientPlatformState.ZoneSummary(zs.id, zs.name, zs.subnetId, zs.blockCount, zs.faceModes));
                }
                ClientPlatformState.updatePlatformInit(message.controllerPos, subnets, zones);
            });
            return null;
        }
    }
}

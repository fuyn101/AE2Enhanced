package com.github.aeddddd.ae2enhanced.network.packet.platform;

import com.github.aeddddd.ae2enhanced.platform.zone.FaceIoConfig;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * C→S packet for zone assign and IO config.
 */
public class PacketZoneAction implements IMessage {

    public enum Action { ASSIGN, IO_CONFIG }

    private Action action;
    private int zoneId;
    private int subnetId;
    private EnumFacing face;
    private FaceIoConfig ioConfig;
    private BlockPos controllerPos;

    public PacketZoneAction() {
    }

    public PacketZoneAction(Action action, BlockPos controllerPos, int zoneId, int subnetId,
                            EnumFacing face, FaceIoConfig ioConfig) {
        this.action = action;
        this.controllerPos = controllerPos;
        this.zoneId = zoneId;
        this.subnetId = subnetId;
        this.face = face;
        this.ioConfig = ioConfig;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = Action.values()[buf.readByte()];
        this.controllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.zoneId = buf.readInt();
        this.subnetId = buf.readInt();
        this.face = EnumFacing.values()[buf.readByte()];
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.ioConfig = new FaceIoConfig();
        if (tag != null) {
            this.ioConfig.readFromNBT(tag);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeInt(controllerPos.getX());
        buf.writeInt(controllerPos.getY());
        buf.writeInt(controllerPos.getZ());
        buf.writeInt(zoneId);
        buf.writeInt(subnetId);
        buf.writeByte(face != null ? face.ordinal() : 0);
        ByteBufUtils.writeTag(buf, ioConfig != null ? ioConfig.writeToNBT() : new NBTTagCompound());
    }

    public Action getAction() {
        return action;
    }

    public int getZoneId() {
        return zoneId;
    }

    public int getSubnetId() {
        return subnetId;
    }

    public EnumFacing getFace() {
        return face;
    }

    public FaceIoConfig getIoConfig() {
        return ioConfig;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public static class Handler implements IMessageHandler<PacketZoneAction, IMessage> {

        @Override
        public IMessage onMessage(PacketZoneAction message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                TileEntity te = player.world.getTileEntity(message.controllerPos);
                if (te instanceof TileAdvancedPlatformController) {
                    TileAdvancedPlatformController controller = (TileAdvancedPlatformController) te;
                    switch (message.action) {
                        case ASSIGN:
                            controller.assignZoneToSubnet(message.zoneId, message.subnetId);
                            break;
                        case IO_CONFIG:
                            controller.setZoneFaceIoConfig(message.zoneId, message.face, message.ioConfig);
                            break;
                        default:
                            break;
                    }
                }
            });
            return null;
        }
    }
}

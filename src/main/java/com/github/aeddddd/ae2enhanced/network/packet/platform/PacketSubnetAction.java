package com.github.aeddddd.ae2enhanced.network.packet.platform;

import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * C→S packet for subnet create/delete/rename.
 */
public class PacketSubnetAction implements IMessage {

    public enum Action { CREATE, DELETE, RENAME }

    private Action action;
    private int subnetId;
    private String name;
    private BlockPos controllerPos;

    public PacketSubnetAction() {
    }

    public PacketSubnetAction(Action action, BlockPos controllerPos, int subnetId, String name) {
        this.action = action;
        this.controllerPos = controllerPos;
        this.subnetId = subnetId;
        this.name = name != null ? name : "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = Action.values()[buf.readByte()];
        this.controllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.subnetId = buf.readInt();
        this.name = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeInt(controllerPos.getX());
        buf.writeInt(controllerPos.getY());
        buf.writeInt(controllerPos.getZ());
        buf.writeInt(subnetId);
        ByteBufUtils.writeUTF8String(buf, name);
    }

    public Action getAction() {
        return action;
    }

    public int getSubnetId() {
        return subnetId;
    }

    public String getName() {
        return name;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public static class Handler implements IMessageHandler<PacketSubnetAction, IMessage> {

        @Override
        public IMessage onMessage(PacketSubnetAction message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                TileEntity te = player.world.getTileEntity(message.controllerPos);
                if (te instanceof TileAdvancedPlatformController) {
                    TileAdvancedPlatformController controller = (TileAdvancedPlatformController) te;
                    switch (message.action) {
                        case CREATE:
                            controller.createSubnet(message.name);
                            break;
                        case DELETE:
                            controller.deleteSubnet(message.subnetId);
                            break;
                        case RENAME:
                            controller.renameSubnet(message.subnetId, message.name);
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

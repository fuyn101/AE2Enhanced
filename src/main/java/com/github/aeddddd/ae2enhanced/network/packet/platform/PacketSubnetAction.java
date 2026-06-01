package com.github.aeddddd.ae2enhanced.network.packet.platform;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformController;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C→S packet for subnet create/delete/rename/select/open-submenu/filter-update.
 */
public class PacketSubnetAction implements IMessage {

    public enum Action { CREATE, DELETE, RENAME, OPEN_SUBMENU, OPEN_MAIN, SELECT, UPDATE_FILTER }

    private Action action;
    private int subnetId;
    private String name;
    private BlockPos controllerPos;
    private boolean inputMode;
    private List<ItemStack> filterItems;

    public PacketSubnetAction() {
        this.filterItems = new ArrayList<>();
    }

    public PacketSubnetAction(Action action, BlockPos controllerPos, int subnetId, String name) {
        this(action, controllerPos, subnetId, name, true, new ArrayList<>());
    }

    public PacketSubnetAction(Action action, BlockPos controllerPos, int subnetId, String name,
                               boolean inputMode, List<ItemStack> filterItems) {
        this.action = action;
        this.controllerPos = controllerPos;
        this.subnetId = subnetId;
        this.name = name != null ? name : "";
        this.inputMode = inputMode;
        this.filterItems = filterItems != null ? filterItems : new ArrayList<>();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = Action.values()[buf.readByte()];
        this.controllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.subnetId = buf.readInt();
        this.name = ByteBufUtils.readUTF8String(buf);
        this.inputMode = buf.readBoolean();
        int filterCount = buf.readInt();
        this.filterItems = new ArrayList<>();
        for (int i = 0; i < filterCount; i++) {
            this.filterItems.add(ByteBufUtils.readItemStack(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeInt(controllerPos.getX());
        buf.writeInt(controllerPos.getY());
        buf.writeInt(controllerPos.getZ());
        buf.writeInt(subnetId);
        ByteBufUtils.writeUTF8String(buf, name);
        buf.writeBoolean(inputMode);
        buf.writeInt(filterItems.size());
        for (ItemStack stack : filterItems) {
            ByteBufUtils.writeItemStack(buf, stack);
        }
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

    public boolean isInputMode() {
        return inputMode;
    }

    public List<ItemStack> getFilterItems() {
        return filterItems;
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
                            controller.sendPlatformInitToAllViewingPlayers();
                            break;
                        case DELETE:
                            controller.deleteSubnet(message.subnetId);
                            controller.sendPlatformInitToAllViewingPlayers();
                            break;
                        case RENAME:
                            controller.renameSubnet(message.subnetId, message.name);
                            controller.sendPlatformInitToAllViewingPlayers();
                            break;
                        case OPEN_SUBMENU:
                            player.openGui(AE2Enhanced.instance,
                                    GuiHandler.encodeSubmenuId(message.subnetId),
                                    player.world,
                                    message.controllerPos.getX(),
                                    message.controllerPos.getY(),
                                    message.controllerPos.getZ());
                            break;
                        case OPEN_MAIN:
                            player.openGui(AE2Enhanced.instance,
                                    GuiHandler.GUI_ADVANCED_PLATFORM_CONTROLLER,
                                    player.world,
                                    message.controllerPos.getX(),
                                    message.controllerPos.getY(),
                                    message.controllerPos.getZ());
                            break;
                        case SELECT:
                            if (player.openContainer instanceof ContainerAdvancedPlatformController) {
                                ContainerAdvancedPlatformController c = (ContainerAdvancedPlatformController) player.openContainer;
                                c.setSelectedSubnetId(message.subnetId);
                                c.setInputMode(message.inputMode);
                            }
                            break;
                        case UPDATE_FILTER:
                            // 主网过滤已禁用，subnetId==0 时忽略
                            if (message.subnetId == 0) {
                                break;
                            }
                            com.github.aeddddd.ae2enhanced.platform.subnet.Subnet subnet = controller.getSubnet(message.subnetId);
                            if (subnet != null) {
                                java.util.Set<ItemStackKey> filter = message.inputMode
                                        ? subnet.getAllowFromMain()
                                        : subnet.getAllowToMain();
                                filter.clear();
                                for (ItemStack stack : message.filterItems) {
                                    if (stack != null && !stack.isEmpty()) {
                                        filter.add(ItemStackKey.of(stack));
                                    }
                                }
                                controller.markDirty();
                                controller.sendPlatformInitToAllViewingPlayers();
                            }
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

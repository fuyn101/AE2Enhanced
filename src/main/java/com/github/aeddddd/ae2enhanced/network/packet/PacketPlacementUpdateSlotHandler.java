package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.container.ContainerPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 处理客户端发来的幽灵槽更新。
 */
public class PacketPlacementUpdateSlotHandler implements IMessageHandler<PacketPlacementUpdateSlot, PacketPlacementUpdateSlot> {

    @Override
    public PacketPlacementUpdateSlot onMessage(PacketPlacementUpdateSlot message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            if (player.openContainer instanceof ContainerPlacementTool) {
                ContainerPlacementTool container = (ContainerPlacementTool) player.openContainer;
                ItemStack stack = message.getStack();
                if (!stack.isEmpty()) {
                    stack = stack.copy();
                    stack.setCount(1);
                }
                container.getConfig().setStackInSlot(message.getSlotIndex(), stack);
            }
        });
        return null;
    }
}

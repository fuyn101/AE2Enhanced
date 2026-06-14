package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.container.ContainerPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 处理放置工具 GUI 操作包。
 */
public class PacketPlacementGuiActionHandler implements IMessageHandler<PacketPlacementGuiAction, PacketPlacementGuiAction> {

    @Override
    public PacketPlacementGuiAction onMessage(PacketPlacementGuiAction message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            if (player.openContainer instanceof ContainerPlacementTool) {
                ContainerPlacementTool container = (ContainerPlacementTool) player.openContainer;
                PlacementConfig config = container.getConfig();
                switch (message.getAction()) {
                    case PAGE_PREV:
                        container.setPage(container.getCurrentPage() - 1);
                        break;
                    case PAGE_NEXT:
                        container.setPage(container.getCurrentPage() + 1);
                        break;
                    case COUNT_PREV:
                        config.setPlacementCountIndex(config.getPlacementCountIndex() - 1);
                        break;
                    case COUNT_NEXT:
                        config.setPlacementCountIndex(config.getPlacementCountIndex() + 1);
                        break;
                    case SELECT_SLOT:
                        int slot = message.getValue();
                        if (slot >= 0 && slot < PlacementConfig.TOTAL_SLOTS) {
                            config.setSelectedSlot(slot);
                        }
                        break;
                }
            }
        });
        return null;
    }
}

package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOmniToolConfigHandler implements IMessageHandler<PacketOmniToolConfig, IMessage> {
    @Override
    public IMessage onMessage(PacketOmniToolConfig message, MessageContext ctx) {
        ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            for (EnumHand hand : EnumHand.values()) {
                ItemStack stack = player.getHeldItem(hand);
                if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                    ItemAdvancedMEOmniTool.setMode(stack, message.getMode());
                    ItemAdvancedMEOmniTool.setDropMode(stack, message.getDropMode());
                    ItemAdvancedMEOmniTool.setSilkTouchEnabled(stack, message.isSilkTouch());
                    ItemAdvancedMEOmniTool.setFortuneLevel(stack, Math.max(0, message.getFortune()));
                    ItemAdvancedMEOmniTool.setBlinkDistance(stack, message.getBlinkDistance());
                    ItemAdvancedMEOmniTool.setBreakCooldown(stack, Math.max(0, message.getBreakCooldown()));
                    int mask = message.getParamEnabled();
                    for (int i = 0; i < 10; i++) {
                        ItemAdvancedMEOmniTool.setParamEnabled(stack, i, (mask & (1 << i)) != 0);
                    }
                    ItemAdvancedMEOmniTool.setChaosForceKillEnabled(stack, message.isChaosForceKill());
                    ItemAdvancedMEOmniTool.setConformalCharge(stack, message.isConformalEnabled());
                    ItemAdvancedMEOmniTool.setAdvancedSilkTouchEnabled(stack, message.isAdvancedSilkTouch());
                    ItemAdvancedMEOmniTool.setWallPhaseEnabled(stack, message.isWallPhase());
                    // 强制同步NBT到客户端
                    player.setHeldItem(hand, stack);
                    break;
                }
            }
        });
        return null;
    }
}

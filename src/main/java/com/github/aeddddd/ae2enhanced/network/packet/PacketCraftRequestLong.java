package com.github.aeddddd.ae2enhanced.network.packet;

import ae2.container.implementations.ContainerCraftAmount;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 支持 long 数量下单的合成请求包。
 * AE2S 的 ContainerCraftAmount#confirm(int, boolean, boolean) 已经封装了打开确认 GUI、
 * 计划合成任务等完整逻辑，因此本包只需转发数量与 Shift 状态即可。
 */
public class PacketCraftRequestLong implements IMessage {

    private long amount;
    private boolean heldShift;

    public PacketCraftRequestLong() {
    }

    public PacketCraftRequestLong(long craftAmt, boolean shift) {
        this.amount = craftAmt;
        this.heldShift = shift;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.heldShift = buf.readBoolean();
        this.amount = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.heldShift);
        buf.writeLong(this.amount);
    }

    public static class Handler implements IMessageHandler<PacketCraftRequestLong, IMessage> {

        @Override
        public IMessage onMessage(PacketCraftRequestLong message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerCraftAmount) {
                    ContainerCraftAmount cca = (ContainerCraftAmount) player.openContainer;
                    int amt = (int) Math.min(Math.max(message.amount, 1), ContainerCraftAmount.MAX_AUTO_CRAFT_AMOUNT);
                    cca.confirm(amt, message.heldShift, false);
                }
            });
            return null;
        }
    }
}

package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.client.rts.ClientRTSState;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 选区更新同步包 —— 服务端 → 客户端。
 * 发送当前选区的全量方块坐标集合。
 */
public class PacketSelectionUpdate implements IMessage {

    private com.github.aeddddd.ae2enhanced.platform.selection.Selection selection;

    public PacketSelectionUpdate() {
        this.selection = new com.github.aeddddd.ae2enhanced.platform.selection.Selection();
    }

    public PacketSelectionUpdate(com.github.aeddddd.ae2enhanced.platform.selection.Selection selection) {
        this.selection = selection;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (this.selection == null) this.selection = new com.github.aeddddd.ae2enhanced.platform.selection.Selection();
        this.selection.readFromBuffer(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (this.selection == null) {
            buf.writeInt(0);
            return;
        }
        this.selection.writeToBuffer(buf);
    }

    public static class Handler implements IMessageHandler<PacketSelectionUpdate, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketSelectionUpdate message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.selection != null) {
                    ClientRTSState.currentSelection.clear();
                    ClientRTSState.currentSelection.addAll(message.selection.getSelectedBlocks());
                }
            });
            return null;
        }
    }
}

package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 客户端通知服务端执行放置工具 GUI 操作：翻页、切换数量、选择槽位。
 */
public class PacketPlacementGuiAction implements IMessage {

    public enum Action {
        PAGE_PREV,
        PAGE_NEXT,
        COUNT_PREV,
        COUNT_NEXT,
        SELECT_SLOT
    }

    private Action action;
    private int value;

    public PacketPlacementGuiAction() {
    }

    public PacketPlacementGuiAction(Action action) {
        this(action, 0);
    }

    public PacketPlacementGuiAction(Action action, int value) {
        this.action = action;
        this.value = value;
    }

    public Action getAction() {
        return action;
    }

    public int getValue() {
        return value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = Action.values()[buf.readByte()];
        this.value = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeInt(value);
    }
}

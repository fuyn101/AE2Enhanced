package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务端 → 客户端：同步 ME 网络存储物品列表到 RTS 底部面板。
 */
public class PacketRTSMEStorageSync implements IMessage {

    public static class Entry {
        public final ItemStack stack;
        public final long count;

        public Entry(ItemStack stack, long count) {
            this.stack = stack;
            this.count = count;
        }
    }

    private List<Entry> entries = Collections.emptyList();
    private boolean networkConnected = false;

    public PacketRTSMEStorageSync() {
    }

    public PacketRTSMEStorageSync(List<Entry> entries, boolean networkConnected) {
        this.entries = entries;
        this.networkConnected = networkConnected;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public boolean isNetworkConnected() {
        return networkConnected;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(networkConnected);
        buf.writeInt(entries.size());
        for (Entry e : entries) {
            ByteBufUtils.writeItemStack(buf, e.stack);
            buf.writeLong(e.count);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        networkConnected = buf.readBoolean();
        int count = buf.readInt();
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemStack stack = ByteBufUtils.readItemStack(buf);
            long cnt = buf.readLong();
            entries.add(new Entry(stack, cnt));
        }
    }

    public static class Handler implements IMessageHandler<PacketRTSMEStorageSync, IMessage> {
        @Override
        public IMessage onMessage(PacketRTSMEStorageSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                com.github.aeddddd.ae2enhanced.client.rts.gui.RTSMEStorageCache.update(message.getEntries(), message.isNetworkConnected());
            });
            return null;
        }
    }
}

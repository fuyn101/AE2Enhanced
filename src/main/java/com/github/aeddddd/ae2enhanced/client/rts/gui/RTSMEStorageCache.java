package com.github.aeddddd.ae2enhanced.client.rts.gui;

import com.github.aeddddd.ae2enhanced.network.packet.PacketRTSMEStorageSync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端缓存：RTS 底部面板显示的 ME 网络存储物品列表。
 *
 * <p>数据由 {@link com.github.aeddddd.ae2enhanced.network.packet.PacketRTSMEStorageSync}
 * 从服务端同步，在收到新数据时原子替换。</p>
 */
public class RTSMEStorageCache {

    private static List<PacketRTSMEStorageSync.Entry> entries = Collections.emptyList();

    public static synchronized void update(List<PacketRTSMEStorageSync.Entry> newEntries) {
        entries = new ArrayList<>(newEntries);
    }

    public static synchronized List<PacketRTSMEStorageSync.Entry> getEntries() {
        return entries;
    }

    public static synchronized void clear() {
        entries = Collections.emptyList();
    }
}

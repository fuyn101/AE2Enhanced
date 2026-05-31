package com.github.aeddddd.ae2enhanced.client.rts.gui;

import com.github.aeddddd.ae2enhanced.network.packet.PacketRTSMEStorageSync;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 客户端缓存：RTS 底部面板显示的 ME 网络存储物品列表。
 *
 * <p>数据由 {@link com.github.aeddddd.ae2enhanced.network.packet.PacketRTSMEStorageSync}
 * 从服务端同步，在收到新数据时原子替换并按当前排序模式排序。</p>
 */
public class RTSMEStorageCache {

    public enum SortMode {
        QUANTITY_DESC("数量", true),
        NAME_ASC("名称", false);

        public final String label;
        public final boolean descending;

        SortMode(String label, boolean descending) {
            this.label = label;
            this.descending = descending;
        }
    }

    private static List<PacketRTSMEStorageSync.Entry> entries = Collections.emptyList();
    private static SortMode sortMode = SortMode.QUANTITY_DESC;
    private static boolean networkConnected = false;

    public static synchronized void update(List<PacketRTSMEStorageSync.Entry> newEntries, boolean connected) {
        networkConnected = connected;
        List<PacketRTSMEStorageSync.Entry> sorted = new ArrayList<>(newEntries);
        applySort(sorted);
        entries = sorted;
    }

    public static synchronized List<PacketRTSMEStorageSync.Entry> getEntries() {
        return entries;
    }

    public static synchronized boolean isNetworkConnected() {
        return networkConnected;
    }

    public static synchronized void clear() {
        entries = Collections.emptyList();
        networkConnected = false;
    }

    public static synchronized SortMode getSortMode() {
        return sortMode;
    }

    public static synchronized void toggleSortMode() {
        sortMode = (sortMode == SortMode.QUANTITY_DESC) ? SortMode.NAME_ASC : SortMode.QUANTITY_DESC;
        List<PacketRTSMEStorageSync.Entry> sorted = new ArrayList<>(entries);
        applySort(sorted);
        entries = sorted;
    }

    private static void applySort(List<PacketRTSMEStorageSync.Entry> list) {
        if (sortMode == SortMode.QUANTITY_DESC) {
            list.sort((a, b) -> Long.compare(b.count, a.count));
        } else {
            list.sort((a, b) -> {
                String na = a.stack.getDisplayName();
                String nb = b.stack.getDisplayName();
                int cmp = na.compareToIgnoreCase(nb);
                if (cmp != 0) return cmp;
                return Long.compare(b.count, a.count);
            });
        }
    }
}

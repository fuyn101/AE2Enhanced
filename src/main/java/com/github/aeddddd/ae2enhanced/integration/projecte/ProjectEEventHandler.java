package com.github.aeddddd.ae2enhanced.integration.projecte;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * ProjectE 事件监听。
 *
 * <p>该类在 ProjectE 已加载时本应通过反射实例化并注册到 Forge 事件总线。
 * 由于 ProjectE 事件类在 AE2S 迁移期间未出现在编译类路径中，当前为存根实现，
 * 保留 API 入口以避免 TileEMCInterface 调用处编译失败。</p>
 */
public class ProjectEEventHandler {

    private static ProjectEEventHandler INSTANCE;
    private static boolean registered = false;

    private final List<WeakReference<com.github.aeddddd.ae2enhanced.tile.TileEMCInterface>> tiles = new LinkedList<>();

    public static synchronized void ensureRegistered() {
        if (!registered) {
            INSTANCE = new ProjectEEventHandler();
            // TODO: optional mod dependency — ProjectE event classes unavailable at compile time.
            // When ProjectE is reliably on the compile classpath, register INSTANCE with
            // MinecraftForge.EVENT_BUS and add typed @SubscribeEvent handlers for
            // moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent / EMCRemapEvent.
            registered = true;
        }
    }

    public static synchronized void registerTile(com.github.aeddddd.ae2enhanced.tile.TileEMCInterface tile) {
        ensureRegistered();
        if (INSTANCE == null) return;
        INSTANCE.tiles.add(new WeakReference<>(tile));
    }

    public static synchronized void unregisterTile(com.github.aeddddd.ae2enhanced.tile.TileEMCInterface tile) {
        if (INSTANCE == null) return;
        Iterator<WeakReference<com.github.aeddddd.ae2enhanced.tile.TileEMCInterface>> it = INSTANCE.tiles.iterator();
        while (it.hasNext()) {
            com.github.aeddddd.ae2enhanced.tile.TileEMCInterface t = it.next().get();
            if (t == null || t == tile) {
                it.remove();
            }
        }
    }

    // TODO: optional mod dependency — add typed event handlers once ProjectE is on classpath.
}

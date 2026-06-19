package com.github.aeddddd.ae2enhanced.recycler;

import ae2.api.networking.IGrid;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.storage.IStorageService;
import ae2.api.storage.MEStorage;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import javax.annotation.Nullable;

/**
 * 缓存并查找当前网络中的主存储视图.
 */
public class HyperStorageLink {

    private MEStorage cachedStorage;
    private long lastSearchTick;
    private static final long SEARCH_COOLDOWN = 20;

    @Nullable
    public MEStorage find(IManagedGridNode node, long currentTick) {
        if (cachedStorage != null) {
            return cachedStorage;
        }
        if (currentTick - lastSearchTick < SEARCH_COOLDOWN) {
            return null;
        }
        lastSearchTick = currentTick;

        try {
            if (!node.isReady()) return null;
            IGrid grid = node.getGrid();
            if (grid == null) return null;
            IStorageService storageService = grid.getService(IStorageService.class);
            if (storageService != null) {
                cachedStorage = storageService.getInventory();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to find network storage for recycler", e);
        }

        return cachedStorage;
    }

    public void invalidate() {
        cachedStorage = null;
        lastSearchTick = 0;
    }
}

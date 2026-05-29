package com.github.aeddddd.ae2enhanced.client.platform;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * P0 客户端平台状态缓存 —— 仅缓存能量同步数据，供 TESR/GUI 使用。
 */
public class ClientPlatformState {

    private static final Map<BlockPos, EnergySyncData> energyCache = new HashMap<>();

    public static void updateEnergy(BlockPos pos, long buffer, long capacity, long network) {
        energyCache.put(pos, new EnergySyncData(buffer, capacity, network));
    }

    public static EnergySyncData getEnergy(BlockPos pos) {
        return energyCache.get(pos);
    }

    public static class EnergySyncData {
        public final long rfBuffer;
        public final long rfCapacity;
        public final long networkStored;

        public EnergySyncData(long rfBuffer, long rfCapacity, long networkStored) {
            this.rfBuffer = rfBuffer;
            this.rfCapacity = rfCapacity;
            this.networkStored = networkStored;
        }
    }
}

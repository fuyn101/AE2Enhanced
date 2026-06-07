package com.github.aeddddd.ae2enhanced.client.platform;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P0 客户端平台状态缓存 —— 缓存能量同步数据、子网/选区列表、状态数据,供 TESR/GUI 使用.
 */
public class ClientPlatformState {

    private static final Map<BlockPos, EnergySyncData> energyCache = new HashMap<>();
    private static final Map<BlockPos, PlatformInitData> initCache = new HashMap<>();
    private static final Map<BlockPos, Map<Integer, Long>> statusCache = new HashMap<>();

    public static void updateEnergy(BlockPos pos, long buffer, long capacity, long network) {
        energyCache.put(pos, new EnergySyncData(buffer, capacity, network));
    }

    public static EnergySyncData getEnergy(BlockPos pos) {
        return energyCache.get(pos);
    }

    public static void updatePlatformInit(BlockPos pos, List<SubnetData> subnets, List<ZoneSummary> zones) {
        initCache.put(pos, new PlatformInitData(subnets, zones));
    }

    public static PlatformInitData getPlatformInit(BlockPos pos) {
        return initCache.get(pos);
    }

    public static void updatePlatformStatus(BlockPos pos, Map<Integer, Long> usage) {
        statusCache.put(pos, usage);
    }

    public static Map<Integer, Long> getPlatformStatus(BlockPos pos) {
        return statusCache.get(pos);
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

    public static class SubnetData {
        public final int id;
        public final String name;

        public SubnetData(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class ZoneSummary {
        public final int id;
        public final String name;
        public final int subnetId;
        public final int blockCount;
        public final int[] faceModes; // 6 directions, indexed by EnumFacing.ordinal()

        public ZoneSummary(int id, String name, int subnetId, int blockCount) {
            this(id, name, subnetId, blockCount, new int[6]);
        }

        public ZoneSummary(int id, String name, int subnetId, int blockCount, int[] faceModes) {
            this.id = id;
            this.name = name;
            this.subnetId = subnetId;
            this.blockCount = blockCount;
            this.faceModes = faceModes != null ? faceModes : new int[6];
        }
    }

    public static class PlatformInitData {
        public final List<SubnetData> subnets;
        public final List<ZoneSummary> zones;

        public PlatformInitData(List<SubnetData> subnets, List<ZoneSummary> zones) {
            this.subnets = subnets != null ? subnets : Collections.emptyList();
            this.zones = zones != null ? zones : Collections.emptyList();
        }
    }
}

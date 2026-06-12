package com.github.aeddddd.ae2enhanced.collector;

import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 先进 ME 收集器全局索引.
 *
 * 按维度维护所有已加载的收集器,使事件/Mixin 能快速定位到可能覆盖某位置的收集器,
 * 避免每次扫描全世界 TileEntity.
 */
public final class CollectorRegistry {

    private CollectorRegistry() {}

    private static final Map<Integer, Set<TileAdvancedMECollector>> DIMENSIONAL_COLLECTORS = new HashMap<>();

    public static void register(TileAdvancedMECollector collector) {
        if (collector.getWorld() == null) return;
        int dim = collector.getWorld().provider.getDimension();
        DIMENSIONAL_COLLECTORS.computeIfAbsent(dim, k -> Collections.newSetFromMap(new java.util.WeakHashMap<>())).add(collector);
    }

    public static void unregister(TileAdvancedMECollector collector) {
        if (collector.getWorld() == null) return;
        int dim = collector.getWorld().provider.getDimension();
        Set<TileAdvancedMECollector> set = DIMENSIONAL_COLLECTORS.get(dim);
        if (set != null) {
            set.remove(collector);
        }
    }

    /**
     * 查找能够收集指定物品实体的最佳收集器(最近优先).
     *
     * @param item 物品实体
     * @return 可收集该物品的收集器,若无则返回 null
     */
    @Nullable
    public static TileAdvancedMECollector findBestCollector(EntityItem item) {
        if (item == null || item.world == null || item.world.isRemote) return null;
        List<TileAdvancedMECollector> candidates = findCollectorsFor(item);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * 返回覆盖指定物品实体位置的所有可用收集器,按距离从近到远排序.
     */
    public static List<TileAdvancedMECollector> findCollectorsFor(EntityItem item) {
        World world = item.world;
        int dim = world.provider.getDimension();
        Set<TileAdvancedMECollector> all = DIMENSIONAL_COLLECTORS.get(dim);
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }

        List<TileAdvancedMECollector> result = new ArrayList<>();
        for (TileAdvancedMECollector collector : all) {
            if (collector.isInvalid() || collector.getWorld() != world) continue;
            if (!collector.isActive()) continue;
            AxisAlignedBB area = collector.getCollectionArea();
            if (area.contains(new Vec3d(item.posX, item.posY, item.posZ))) {
                result.add(collector);
            }
        }

        result.sort(Comparator.comparingDouble(c -> {
            BlockPos pos = c.getPos();
            double dx = pos.getX() + 0.5 - item.posX;
            double dy = pos.getY() + 0.5 - item.posY;
            double dz = pos.getZ() + 0.5 - item.posZ;
            return dx * dx + dy * dy + dz * dz;
        }));

        return result;
    }

    /**
     * 返回覆盖指定方块位置的所有可用收集器,按距离从近到远排序.
     */
    public static List<TileAdvancedMECollector> findCollectorsFor(World world, BlockPos pos) {
        int dim = world.provider.getDimension();
        Set<TileAdvancedMECollector> all = DIMENSIONAL_COLLECTORS.get(dim);
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }

        List<TileAdvancedMECollector> result = new ArrayList<>();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        for (TileAdvancedMECollector collector : all) {
            if (collector.isInvalid() || collector.getWorld() != world) continue;
            if (!collector.isActive()) continue;
            AxisAlignedBB area = collector.getCollectionArea();
            if (area.contains(new Vec3d(x, y, z))) {
                result.add(collector);
            }
        }

        result.sort(Comparator.comparingDouble(c -> {
            BlockPos cPos = c.getPos();
            double dx = cPos.getX() + 0.5 - x;
            double dy = cPos.getY() + 0.5 - y;
            double dz = cPos.getZ() + 0.5 - z;
            return dx * dx + dy * dy + dz * dz;
        }));

        return result;
    }
}

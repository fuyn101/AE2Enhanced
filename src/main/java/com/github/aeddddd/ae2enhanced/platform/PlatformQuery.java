package com.github.aeddddd.ae2enhanced.platform;

import net.minecraft.util.math.BlockPos;

/**
 * 客户端平台查询 —— 从 RTSCamera 缓存的平台数据提供只读查询
 */
public final class PlatformQuery {

    private PlatformQuery() {}

    public static BlockPos getMin() {
        return com.github.aeddddd.ae2enhanced.client.rts.RTSCamera.getPlatformMin();
    }

    public static BlockPos getMax() {
        return com.github.aeddddd.ae2enhanced.client.rts.RTSCamera.getPlatformMax();
    }

    public static int getSurfaceY() {
        return com.github.aeddddd.ae2enhanced.client.rts.RTSCamera.getPlatformSurfaceY();
    }

    public static boolean isInside(BlockPos pos) {
        BlockPos min = getMin();
        BlockPos max = getMax();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() &&
               pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public static boolean isInside(int x, int z) {
        BlockPos min = getMin();
        BlockPos max = getMax();
        return x >= min.getX() && x <= max.getX() &&
               z >= min.getZ() && z <= max.getZ();
    }
}

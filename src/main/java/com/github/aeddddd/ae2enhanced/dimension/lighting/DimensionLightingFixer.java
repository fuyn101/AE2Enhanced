package com.github.aeddddd.ae2enhanced.dimension.lighting;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;

/**
 * 个人维度光照修复工具。
 *
 * <p>由于个人维度是空世界，chunk 生成后光照数据可能不完整，
 * 玩家进入/登录/切换维度时调用本类方法对周围 chunk 执行补光。</p>
 */
public final class DimensionLightingFixer {

    private DimensionLightingFixer() {}

    /**
     * 安排一次延迟补光：立即执行一次，并在下一 tick 再执行一次以覆盖异步加载的 chunk。
     */
    public static void scheduleRelight(@Nullable MinecraftServer server, int dimId, BlockPos center) {
        if (server == null) return;
        server.addScheduledTask(() -> relightDimensionChunks(dimId, center));
        server.addScheduledTask(() -> server.addScheduledTask(() -> {
            relightDimensionChunks(dimId, center);
            WorldServer target = server.getWorld(dimId);
            if (target != null) {
                refreshSkyLight(target, center);
            }
        }));
    }

    /**
     * 对指定坐标周围 9×9 chunk 范围执行 checkLight 并标记已加载。
     */
    public static void relightDimensionChunks(int dimId, BlockPos center) {
        WorldServer world = DimensionManager.getWorld(dimId);
        if (world == null) return;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                try {
                    chunk.checkLight();
                    chunk.setLightPopulated(true);
                    chunk.markDirty();
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to relight chunk at {}, {}", cx + dx, cz + dz, e);
                }
            }
        }
    }

    /**
     * 对指定坐标周围区域刷新天空光照，缓解空世界天空光照异常。
     */
    public static void refreshSkyLight(WorldServer world, BlockPos center) {
        int floorY = AE2EnhancedConfig.personalDimension.floorY;
        int startY = Math.min(center.getY(), floorY + 2);
        for (int dx = -32; dx <= 32; dx += 4) {
            for (int dz = -32; dz <= 32; dz += 4) {
                BlockPos pos = new BlockPos(center.getX() + dx, startY, center.getZ() + dz);
                try {
                    world.checkLightFor(EnumSkyBlock.SKY, pos);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to refresh skylight at {}", pos, e);
                }
            }
        }
    }
}

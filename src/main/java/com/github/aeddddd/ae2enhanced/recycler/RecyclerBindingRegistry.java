package com.github.aeddddd.ae2enhanced.recycler;

import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局绑定注册表.
 *
 * <p>维护玩家 UUID → 回收节点位置(维度+BlockPos)的映射,用于跨维度目标绑定.</p>
 */
public final class RecyclerBindingRegistry {

    private RecyclerBindingRegistry() {}

    private static final Map<UUID, RecyclerLocation> BINDINGS = new ConcurrentHashMap<>();

    public static void setBinding(UUID playerId, int dimId, BlockPos pos) {
        BINDINGS.put(playerId, new RecyclerLocation(dimId, pos));
    }

    public static void clearBinding(UUID playerId) {
        BINDINGS.remove(playerId);
    }

    @Nullable
    public static TileMENetworkRecycler findRecycler(World world, UUID playerId) {
        RecyclerLocation loc = BINDINGS.get(playerId);
        if (loc == null) return null;
        if (loc.dimId != world.provider.getDimension()) return null;
        TileEntity te = world.getTileEntity(loc.pos);
        if (te instanceof TileMENetworkRecycler) {
            return (TileMENetworkRecycler) te;
        }
        return null;
    }

    private static final class RecyclerLocation {
        final int dimId;
        final BlockPos pos;

        RecyclerLocation(int dimId, BlockPos pos) {
            this.dimId = dimId;
            this.pos = pos;
        }
    }
}

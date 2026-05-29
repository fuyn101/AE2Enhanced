package com.github.aeddddd.ae2enhanced.platform.selection;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * 选区数据结构 —— 存储玩家在当前 RTS 会话中选中的方块集合。
 * 支持单点、框选、连锁三种选取模式。
 */
public class Selection {

    private final Set<BlockPos> selectedBlocks = new HashSet<>();
    private BlockPos anchorA = null;
    private BlockPos anchorB = null;

    public void clear() {
        selectedBlocks.clear();
        anchorA = null;
        anchorB = null;
    }

    public void addSingle(BlockPos pos) {
        selectedBlocks.clear();
        selectedBlocks.add(pos);
        anchorA = pos;
        anchorB = null;
    }

    public void setBox(BlockPos a, BlockPos b) {
        selectedBlocks.clear();
        anchorA = a;
        anchorB = b;
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int y = a.getY();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                selectedBlocks.add(new BlockPos(x, y, z));
            }
        }
    }

    public void addConnected(net.minecraft.world.World world, BlockPos origin, BlockPos min, BlockPos max) {
        selectedBlocks.clear();
        anchorA = origin;
        anchorB = null;

        net.minecraft.block.state.IBlockState originState = world.getBlockState(origin);
        if (originState.getBlock().isAir(originState, world, origin)) return;

        Set<BlockPos> visited = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        int limit = 4096; // BFS 上限
        while (!queue.isEmpty() && selectedBlocks.size() < limit) {
            BlockPos current = queue.poll();
            selectedBlocks.add(current);

            for (EnumFacing face : EnumFacing.values()) {
                if (face.getAxis() == net.minecraft.util.EnumFacing.Axis.Y) continue;
                BlockPos neighbor = current.offset(face);
                if (visited.contains(neighbor)) continue;
                if (neighbor.getX() < min.getX() || neighbor.getX() > max.getX()
                        || neighbor.getZ() < min.getZ() || neighbor.getZ() > max.getZ()) continue;
                if (neighbor.getY() != origin.getY()) continue;

                net.minecraft.block.state.IBlockState neighborState = world.getBlockState(neighbor);
                if (neighborState.getBlock().isAir(neighborState, world, neighbor)) continue;
                if (neighborState.getBlock() == originState.getBlock()
                        && neighborState.getBlock().getMetaFromState(neighborState) == originState.getBlock().getMetaFromState(originState)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
    }

    public Set<BlockPos> getSelectedBlocks() {
        return selectedBlocks;
    }

    public BlockPos getAnchorA() {
        return anchorA;
    }

    public BlockPos getAnchorB() {
        return anchorB;
    }

    public boolean isEmpty() {
        return selectedBlocks.isEmpty();
    }

    public void addBlock(BlockPos pos) {
        selectedBlocks.add(pos);
    }

    public void removeBlock(BlockPos pos) {
        selectedBlocks.remove(pos);
    }

    public void addAll(Set<BlockPos> positions) {
        selectedBlocks.addAll(positions);
    }

    /**
     * 将选区序列化为字节流，用于网络传输。
     */
    public void writeToBuffer(io.netty.buffer.ByteBuf buf) {
        buf.writeInt(selectedBlocks.size());
        for (BlockPos pos : selectedBlocks) {
            buf.writeLong(pos.toLong());
        }
    }

    /**
     * 从字节流反序列化。
     */
    public void readFromBuffer(io.netty.buffer.ByteBuf buf) {
        clear();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            selectedBlocks.add(BlockPos.fromLong(buf.readLong()));
        }
    }
}

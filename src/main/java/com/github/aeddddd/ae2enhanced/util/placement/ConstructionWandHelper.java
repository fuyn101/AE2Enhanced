package com.github.aeddddd.ae2enhanced.util.placement;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑手杖式批量放置位置计算。
 *
 * 规则：
 * 1. 从 clickedPos.offset(side) 开始。
 * 2. 只能在空气或可替换方块上放置。
 * 3. 根据玩家视线和点击面，选择线/墙/面三种扩展形态。
 * 4. 最大 512 个方块。
 * 5. 只在与起始位置相同类型的支撑面上扩展（Construction Wand 同款限制）。
 */
public final class ConstructionWandHelper {

    public static final int MAX_BLOCKS = PlacementConfig.BULK_MAX_BLOCKS;

    private ConstructionWandHelper() {}

    /**
     * 计算批量放置位置。
     *
     * @param world      世界
     * @param player     玩家
     * @param clickedPos 被点击方块位置
     * @param side       被点击面
     * @return 可放置位置列表（按铺设顺序）
     */
    public static List<BlockPos> calculatePositions(World world, EntityPlayer player,
                                                     BlockPos clickedPos, EnumFacing side) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos start = clickedPos.offset(side);
        if (!canPlaceBlockAt(world, start)) return result;

        IBlockState anchorState = world.getBlockState(clickedPos);

        // 根据视线决定主扩展方向
        Vec3d look = player.getLookVec();
        EnumFacing primaryDir = determinePrimaryDirection(side, look);
        EnumFacing secondaryDir = determineSecondaryDirection(side, primaryDir);

        WandShape shape = determineShape(side, look);

        switch (shape) {
            case LINE:
                extendLine(world, start, primaryDir, anchorState, result);
                break;
            case WALL:
                extendWall(world, start, primaryDir, secondaryDir, anchorState, result);
                break;
            case PLANE:
            default:
                extendPlane(world, start, side, anchorState, result);
                break;
        }

        return result;
    }

    private enum WandShape {
        LINE, WALL, PLANE
    }

    private static WandShape determineShape(EnumFacing side, Vec3d look) {
        double dot = Math.abs(look.x * side.getDirectionVec().getX()
                + look.y * side.getDirectionVec().getY()
                + look.z * side.getDirectionVec().getZ());

        // 视线与点击面法向接近垂直 → 玩家平行于该面看 → 线/墙
        // 视线与点击面法向接近平行 → 玩家垂直于该面看 → 面
        if (dot < 0.35) {
            // 平行于面，判断是线还是墙
            double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
            if (horiz < 0.35) {
                // 接近垂直上下看 → 墙
                return WandShape.WALL;
            }
            return WandShape.LINE;
        }
        return WandShape.PLANE;
    }

    private static EnumFacing determinePrimaryDirection(EnumFacing side, Vec3d look) {
        // 在垂直于 side 的平面内，找与视线最平行的轴方向
        EnumFacing.Axis axis = side.getAxis();
        EnumFacing[] candidates;
        if (axis == EnumFacing.Axis.Y) {
            candidates = new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
        } else if (axis == EnumFacing.Axis.X) {
            candidates = new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH};
        } else {
            candidates = new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN, EnumFacing.EAST, EnumFacing.WEST};
        }

        EnumFacing best = candidates[0];
        double bestDot = -1;
        for (EnumFacing f : candidates) {
            Vec3d dir = new Vec3d(f.getDirectionVec());
            double dot = Math.abs(look.x * dir.x + look.y * dir.y + look.z * dir.z);
            if (dot > bestDot) {
                bestDot = dot;
                best = f;
            }
        }
        return best;
    }

    private static EnumFacing determineSecondaryDirection(EnumFacing side, EnumFacing primary) {
        // 在垂直于 side 的平面内，垂直于 primary 的两个方向中返回一个
        EnumFacing.Axis axis = side.getAxis();
        if (axis == EnumFacing.Axis.Y) {
            return primary.getAxis() == EnumFacing.Axis.Z ? EnumFacing.EAST : EnumFacing.NORTH;
        } else if (axis == EnumFacing.Axis.X) {
            return primary.getAxis() == EnumFacing.Axis.Y ? EnumFacing.NORTH : EnumFacing.UP;
        } else {
            return primary.getAxis() == EnumFacing.Axis.Y ? EnumFacing.EAST : EnumFacing.UP;
        }
    }

    private static void extendLine(World world, BlockPos start, EnumFacing dir,
                                   IBlockState anchorState, List<BlockPos> result) {
        BlockPos current = start;
        while (result.size() < MAX_BLOCKS) {
            if (!canPlaceBlockAt(world, current)) break;
            result.add(current);
            current = current.offset(dir);
        }
    }

    private static void extendWall(World world, BlockPos start, EnumFacing primary, EnumFacing secondary,
                                   IBlockState anchorState, List<BlockPos> result) {
        // 先沿主轴正向、反向同时扩展，再沿次轴分层
        List<BlockPos> line = new ArrayList<>();
        BlockPos current = start;
        while (line.size() < MAX_BLOCKS) {
            if (!canPlaceBlockAt(world, current)) break;
            line.add(current);
            current = current.offset(primary);
        }

        int layer = 0;
        while (result.size() < MAX_BLOCKS && layer < MAX_BLOCKS) {
            boolean addedAny = false;
            for (BlockPos base : line) {
                BlockPos pos = base.offset(secondary, layer);
                if (result.size() >= MAX_BLOCKS) break;
                if (!canPlaceBlockAt(world, pos)) continue;
                result.add(pos);
                addedAny = true;
            }
            if (!addedAny) break;
            layer++;
        }
    }

    private static void extendPlane(World world, BlockPos start, EnumFacing side,
                                    IBlockState anchorState, List<BlockPos> result) {
        // BFS 在垂直于 side 的平面内扩展
        EnumFacing.Axis axis = side.getAxis();
        EnumFacing[] planeDirs;
        if (axis == EnumFacing.Axis.Y) {
            planeDirs = new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
        } else if (axis == EnumFacing.Axis.X) {
            planeDirs = new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH};
        } else {
            planeDirs = new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN, EnumFacing.EAST, EnumFacing.WEST};
        }

        java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        queue.offer(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < MAX_BLOCKS) {
            BlockPos current = queue.poll();
            if (!canPlaceBlockAt(world, current)) continue;
            result.add(current);

            for (EnumFacing dir : planeDirs) {
                BlockPos neighbor = current.offset(dir);
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
    }

    private static boolean canPlaceBlockAt(World world, BlockPos pos) {
        return world.isAirBlock(pos) || world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }
}

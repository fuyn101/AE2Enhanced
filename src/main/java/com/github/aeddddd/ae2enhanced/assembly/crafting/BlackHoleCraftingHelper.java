package com.github.aeddddd.ae2enhanced.assembly.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 黑洞合成辅助类（占位实现）。
 * <p>当前仅提供空方法以满足编译需求；完整的黑洞 kill/吸取/爆炸/合成逻辑
 * 将在后续里程碑中实现。</p>
 */
public final class BlackHoleCraftingHelper {

    private BlackHoleCraftingHelper() {
    }

    public static void killLivingEntities(Level level, BlockPos center) {
        // 占位
    }

    public static void suckItems(Level level, BlockPos center) {
        // 占位
    }

    public static void explode(Level level, BlockPos center) {
        // 占位
    }

    public static boolean tryCraft(Level level, BlockPos center, BlockPos outputPos, boolean consume) {
        return false;
    }

    public static void tryCraftAll(Level level, BlockPos center, BlockPos outputPos, boolean consume, int maxTicks) {
        // 占位
    }
}

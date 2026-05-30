package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRTSSelection;
import com.github.aeddddd.ae2enhanced.platform.PlatformQuery;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * 客户端选区数据 + 锚点状态机
 */
public final class RTSSelection {

    private RTSSelection() {}

    public enum Mode {
        NONE,           // 无选区
        ANCHOR_A_SET,   // 已设置第一个锚点
        BOX_SELECTED    // 已完成矩形选区（等待服务端同步确认）
    }

    private static Mode mode = Mode.NONE;
    private static BlockPos anchorA = null;
    private static BlockPos anchorB = null;
    private static final Set<BlockPos> selectedBlocks = new HashSet<>();

    public static Mode getMode() { return mode; }
    public static BlockPos getAnchorA() { return anchorA; }
    public static BlockPos getAnchorB() { return anchorB; }
    public static Set<BlockPos> getSelectedBlocks() { return selectedBlocks; }

    public static void clear() {
        mode = Mode.NONE;
        anchorA = null;
        anchorB = null;
        selectedBlocks.clear();
    }

    public static void onLeftClick(BlockPos hitPos, boolean shiftDown, boolean ctrlDown) {
        if (!shiftDown) {
            // 非 Shift：单选或清除
            clear();
            if (hitPos != null && PlatformQuery.isInside(hitPos)) {
                AE2Enhanced.network.sendToServer(new PacketRTSSelection(PacketRTSSelection.MODE_SINGLE, hitPos));
            } else {
                AE2Enhanced.network.sendToServer(new PacketRTSSelection(PacketRTSSelection.MODE_CLEAR, BlockPos.ORIGIN));
            }
        } else if (!ctrlDown) {
            // Shift：锚点逻辑
            if (hitPos == null || !PlatformQuery.isInside(hitPos)) return;
            if (mode == Mode.NONE) {
                anchorA = hitPos;
                mode = Mode.ANCHOR_A_SET;
                // 不发送网络包，仅本地显示锚点
            } else if (mode == Mode.ANCHOR_A_SET) {
                anchorB = hitPos;
                mode = Mode.BOX_SELECTED;
                AE2Enhanced.network.sendToServer(new PacketRTSSelection(PacketRTSSelection.MODE_BOX, anchorA, anchorB));
            }
        } else {
            // Ctrl+Shift：连锁选取
            if (hitPos == null || !PlatformQuery.isInside(hitPos)) return;
            clear();
            AE2Enhanced.network.sendToServer(new PacketRTSSelection(PacketRTSSelection.MODE_FLOOD, hitPos));
        }
    }

    /**
     * 从服务端同步选区数据（位图压缩）
     */
    public static void syncFromBitmap(byte[] compressedData) {
        selectedBlocks.clear();
        PacketRTSSelection.decompressToSelection(
            compressedData,
            RTSCamera.getPlatformMin(),
            RTSCamera.getPlatformMax(),
            selectedBlocks::add
        );
        // 同步完成后，如果是 BOX 模式，重置为 NONE（因为选区已确认）
        if (mode == Mode.BOX_SELECTED) {
            mode = Mode.NONE;
            anchorA = null;
            anchorB = null;
        }
    }
}

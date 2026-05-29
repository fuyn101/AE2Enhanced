package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.platform.selection.Selection;
import net.minecraft.util.math.BlockPos;

/**
 * 客户端 RTS 全局状态 —— 记录当前是否处于 RTS 模式、绑定的控制器、相机位置、选区等。
 */
public class ClientRTSState {

    public static boolean isInRTS = false;
    public static BlockPos controllerPos = null;

    // 相机目标位置（服务端指定或客户端计算）
    public static double targetCameraX, targetCameraY, targetCameraZ;
    // 相机当前位置（插值后）
    public static double currentCameraX, currentCameraY, currentCameraZ;
    // 相机朝向
    public static float cameraYaw = 180.0f; // 面向下方
    public static float cameraPitch = 90.0f;

    // 当前选区
    public static final Selection currentSelection = new Selection();

    // 框选拖拽状态
    public static boolean isDragging = false;
    public static BlockPos dragStartPos = null;

    public static void enter(BlockPos controllerPos, double camX, double camY, double camZ) {
        ClientRTSState.isInRTS = true;
        ClientRTSState.controllerPos = controllerPos;
        ClientRTSState.targetCameraX = camX;
        ClientRTSState.targetCameraY = camY;
        ClientRTSState.targetCameraZ = camZ;
        ClientRTSState.currentCameraX = camX;
        ClientRTSState.currentCameraY = camY;
        ClientRTSState.currentCameraZ = camZ;
        ClientRTSState.cameraYaw = 180.0f;
        ClientRTSState.cameraPitch = 90.0f;
        ClientRTSState.currentSelection.clear();
        ClientRTSState.isDragging = false;
        ClientRTSState.dragStartPos = null;
    }

    public static void exit() {
        isInRTS = false;
        controllerPos = null;
        currentSelection.clear();
        isDragging = false;
        dragStartPos = null;
    }
}

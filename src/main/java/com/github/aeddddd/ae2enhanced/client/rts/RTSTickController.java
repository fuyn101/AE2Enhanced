package com.github.aeddddd.ae2enhanced.client.rts;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 客户端 RTS Tick 驱动 —— 边界限制 + 飞行状态维持 + 鼠标位移累积与应用
 */
public class RTSTickController {

    // 虚拟光标位置（屏幕像素坐标）
    private static float cursorX = 0f;
    private static float cursorY = 0f;

    // 鼠标位移累积（MouseEvent 高频率累积，RenderTickEvent 低频应用）
    private static float pendingDx = 0f;
    private static float pendingDy = 0f;

    // 死区阈值：忽略小于此值的位移（过滤高回报率鼠标噪声）
    private static final float MOUSE_DEADZONE = 1.0f;

    public static float getCursorX() { return cursorX; }
    public static float getCursorY() { return cursorY; }

    public static void moveCursor(float dx, float dy) {
        Minecraft mc = Minecraft.getMinecraft();
        cursorX = MathHelper.clamp(cursorX + dx, 0, mc.displayWidth);
        cursorY = MathHelper.clamp(cursorY - dy, 0, mc.displayHeight);
    }

    /**
     * 由 MouseEvent 调用，累积鼠标位移（不直接应用）
     */
    public static void accumulateMouseDelta(float dx, float dy) {
        // 死区过滤
        if (Math.abs(dx) < MOUSE_DEADZONE) dx = 0;
        if (Math.abs(dy) < MOUSE_DEADZONE) dy = 0;
        pendingDx += dx;
        pendingDy += dy;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!RTSCamera.isActive()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // GUI 打开时不限制位置
        if (mc.currentScreen != null) return;

        // 限制玩家在平台边界内
        RTSCamera.clampPlayerPosition();

        // 维持飞行状态（防止被服务端同步覆盖）
        if (!mc.player.capabilities.isFlying) {
            mc.player.capabilities.isFlying = true;
        }
    }

    /**
     * 在 RenderTickEvent 中应用累积的鼠标位移（60Hz，与渲染同步）
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!RTSCamera.isActive()) return;
        if (Minecraft.getMinecraft().currentScreen != null) return;

        if (pendingDx == 0 && pendingDy == 0) return;

        if (RTSCamera.isRotating()) {
            RTSCamera.addYaw(pendingDx * RTSCamera.ROTATION_SENSITIVITY);
            Minecraft.getMinecraft().player.rotationYaw = RTSCamera.getYaw();
        } else {
            moveCursor(pendingDx * RTSCamera.CURSOR_SENSITIVITY, pendingDy * RTSCamera.CURSOR_SENSITIVITY);
        }

        pendingDx = 0;
        pendingDy = 0;
    }

    public static void resetCursor() {
        Minecraft mc = Minecraft.getMinecraft();
        cursorX = mc.displayWidth / 2f;
        cursorY = mc.displayHeight / 2f;
        pendingDx = 0;
        pendingDy = 0;
    }
}

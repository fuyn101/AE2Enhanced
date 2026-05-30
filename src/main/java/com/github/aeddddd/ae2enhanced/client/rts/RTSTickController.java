package com.github.aeddddd.ae2enhanced.client.rts;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/**
 * 客户端 RTS Tick 驱动 —— 虚拟光标 + 中键旋转 + 边界限制
 */
public class RTSTickController {

    private static final float CURSOR_SENSITIVITY = 1.0f;
    private static final float ROTATION_SENSITIVITY = 0.3f;

    // 虚拟光标位置（屏幕像素坐标）
    private static float cursorX = 0f;
    private static float cursorY = 0f;

    public static float getCursorX() { return cursorX; }
    public static float getCursorY() { return cursorY; }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!RTSCamera.isActive()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // GUI 打开时不更新光标和相机
        if (mc.currentScreen != null) return;

        // 读取鼠标 delta
        float dx = Mouse.getDX() * CURSOR_SENSITIVITY;
        float dy = Mouse.getDY() * CURSOR_SENSITIVITY;

        if (RTSCamera.isRotating()) {
            // 中键按住：旋转相机 yaw，光标不动
            RTSCamera.addYaw(dx * ROTATION_SENSITIVITY);
        } else {
            // 正常：移动虚拟光标
            cursorX = MathHelper.clamp(cursorX + dx, 0, mc.displayWidth);
            cursorY = MathHelper.clamp(cursorY + dy, 0, mc.displayHeight);
        }

        // 限制玩家在平台边界内
        RTSCamera.clampPlayerPosition();
    }

    public static void resetCursor() {
        Minecraft mc = Minecraft.getMinecraft();
        cursorX = mc.displayWidth / 2f;
        cursorY = mc.displayHeight / 2f;
    }
}

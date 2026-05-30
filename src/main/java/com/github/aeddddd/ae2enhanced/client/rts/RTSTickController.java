package com.github.aeddddd.ae2enhanced.client.rts;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 客户端 RTS Tick 驱动 —— 边界限制 + 飞行状态维持
 */
public class RTSTickController {

    // 虚拟光标位置（屏幕像素坐标）
    private static float cursorX = 0f;
    private static float cursorY = 0f;

    public static float getCursorX() { return cursorX; }
    public static float getCursorY() { return cursorY; }

    public static void moveCursor(float dx, float dy) {
        Minecraft mc = Minecraft.getMinecraft();
        cursorX = MathHelper.clamp(cursorX + dx, 0, mc.displayWidth);
        cursorY = MathHelper.clamp(cursorY + dy, 0, mc.displayHeight);
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

    public static void resetCursor() {
        Minecraft mc = Minecraft.getMinecraft();
        cursorX = mc.displayWidth / 2f;
        cursorY = mc.displayHeight / 2f;
    }
}

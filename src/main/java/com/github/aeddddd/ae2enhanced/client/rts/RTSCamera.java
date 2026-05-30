package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRTSCameraSync;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

/**
 * RTS 相机状态管理 —— 纯静态状态，无实体修改
 */
public final class RTSCamera {

    private RTSCamera() {}

    private static boolean active = false;

    // 平台信息（服务端下发）
    private static BlockPos platformCenter = BlockPos.ORIGIN;
    private static int platformSizeInChunks = 5;
    private static int platformSurfaceY = 64;
    private static BlockPos platformMin = BlockPos.ORIGIN;
    private static BlockPos platformMax = BlockPos.ORIGIN;

    // 相机参数
    private static float pitch = 60f;
    private static float yaw = 0f;
    private static float height = 50f;
    private static float fov = 90f;

    // 旋转状态
    private static boolean rotating = false;

    // 灵敏度配置
    public static final float CURSOR_SENSITIVITY = 2.0f;
    public static final float ROTATION_SENSITIVITY = 1.2f;
    public static final float MOVE_SPEED = 1.5f;

    // 高度/FOV 范围
    private static final float HEIGHT_MIN = 20f;
    private static final float HEIGHT_MAX = 80f;
    private static final float FOV_MIN = 70f;
    private static final float FOV_MAX = 100f;

    public static boolean isActive() { return active; }

    public static float getPitch() { return pitch; }
    public static float getYaw() { return yaw; }
    public static float getHeight() { return height; }
    public static float getFov() { return fov; }
    public static boolean isRotating() { return rotating; }

    public static BlockPos getPlatformMin() { return platformMin; }
    public static BlockPos getPlatformMax() { return platformMax; }
    public static int getPlatformSurfaceY() { return platformSurfaceY; }
    public static float getMoveSpeed() { return MOVE_SPEED; }

    public static void setRotating(boolean value) { rotating = value; }

    public static void addYaw(float delta) {
        yaw = (yaw + delta * ROTATION_SENSITIVITY) % 360f;
        if (yaw < 0) yaw += 360f;
    }

    public static void adjustHeight(int wheelDelta) {
        // 滚轮向前（负值）= 放大 = 降低高度
        // 滚轮向后（正值）= 缩小 = 升高高度
        float delta = -wheelDelta * 0.08f;
        height = Math.max(HEIGHT_MIN, Math.min(HEIGHT_MAX, height + delta));
        // FOV 联动
        fov = FOV_MIN + (height - HEIGHT_MIN) * ((FOV_MAX - FOV_MIN) / (HEIGHT_MAX - HEIGHT_MIN));
        // 同步到服务端
        syncToServer();
    }

    public static void activate(BlockPos center, int size, int surfaceY) {
        active = true;
        platformCenter = center;
        platformSizeInChunks = size;
        platformSurfaceY = surfaceY;
        int half = (size / 2) * 16;
        int total = size * 16;
        platformMin = new BlockPos(center.getX() - half, surfaceY, center.getZ() - half);
        platformMax = new BlockPos(center.getX() - half + total - 1, surfaceY, center.getZ() - half + total - 1);
        pitch = 60f;
        yaw = 0f;
        height = 50f;
        fov = 90f;
        rotating = false;
        RTSTickController.resetCursor();

        // 启用飞行 + 增加移速
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.capabilities.allowFlying = true;
            mc.player.capabilities.isFlying = true;
            mc.player.capabilities.setFlySpeed(0.1f);
            mc.player.sendPlayerAbilities();
        }
    }

    public static void deactivate() {
        active = false;
        rotating = false;

        // 恢复飞行状态（保留原状态）
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.capabilities.isFlying = false;
            mc.player.capabilities.setFlySpeed(0.05f);
            mc.player.sendPlayerAbilities();
        }
    }

    private static void syncToServer() {
        AE2Enhanced.network.sendToServer(new PacketRTSCameraSync(height, fov));
    }

    /**
     * 限制相机逻辑位置（玩家实体位置）在平台边界内
     */
    public static void clampPlayerPosition() {
        if (!active) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        double margin = 2.0;
        double minX = platformMin.getX() + margin;
        double maxX = platformMax.getX() + 1 - margin;
        double minZ = platformMin.getZ() + margin;
        double maxZ = platformMax.getZ() + 1 - margin;

        boolean moved = false;
        double px = mc.player.posX;
        double pz = mc.player.posZ;

        if (px < minX) { px = minX; moved = true; }
        if (px > maxX) { px = maxX; moved = true; }
        if (pz < minZ) { pz = minZ; moved = true; }
        if (pz > maxZ) { pz = maxZ; moved = true; }

        if (moved) {
            mc.player.setPosition(px, mc.player.posY, pz);
        }
    }
}

package com.github.aeddddd.ae2enhanced.client.rts;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Mouse;

/**
 * RTS 选取助手 —— 处理从俯视相机到地面方块的射线检测。
 */
public class RTSSelectionHelper {

    /**
     * 获取鼠标当前指向的地面方块位置。
     * 由于相机高度可能超过默认 reach distance，使用地面平面相交近似。
     */
    public static BlockPos getHoveredBlock() {
        Minecraft mc = Minecraft.getMinecraft();

        // 优先使用 mc.objectMouseOver（如果它命中了方块）
        RayTraceResult result = mc.objectMouseOver;
        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
            return result.getBlockPos();
        }

        // objectMouseOver 未命中（相机太高），使用地面平面相交
        return raycastGround();
    }

    /**
     * 从相机位置向鼠标方向发射射线，与地面平面（y = surfaceY）相交。
     */
    private static BlockPos raycastGround() {
        Minecraft mc = Minecraft.getMinecraft();
        if (ClientRTSState.controllerPos == null) return null;

        double camX = ClientRTSState.currentCameraX;
        double camY = ClientRTSState.currentCameraY;
        double camZ = ClientRTSState.currentCameraZ;
        int surfaceY = ClientRTSState.controllerPos.getY();

        int mouseX = Mouse.getX();
        int mouseY = Mouse.getY();
        int screenW = mc.displayWidth;
        int screenH = mc.displayHeight;

        if (screenW <= 0 || screenH <= 0) return null;

        // 归一化设备坐标 [-1, 1]
        double ndcX = (2.0 * mouseX / screenW) - 1.0;
        double ndcY = (2.0 * mouseY / screenH) - 1.0;

        float fov = mc.gameSettings.fovSetting;
        double aspect = (double) screenW / screenH;
        double tanHalfFov = Math.tan(Math.toRadians(fov) / 2.0);

        // 相机本地空间中的射线方向（相机朝 -Z 即朝下）
        double dirX = ndcX * tanHalfFov * aspect;
        double dirY = ndcY * tanHalfFov;
        double dirZ = -1.0;

        // 将本地方向转换到世界空间
        // yaw=180, pitch=90 时：local+X=世界-X, local+Y=世界-Z, local+Z=世界-Y
        double yawRad = Math.toRadians(ClientRTSState.cameraYaw);
        double pitchRad = Math.toRadians(ClientRTSState.cameraPitch);

        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        // 完整的旋转矩阵：先 yaw 后 pitch
        double worldDirX = dirX * cosYaw + dirZ * sinYaw * sinPitch - dirY * sinYaw * cosPitch;
        double worldDirY = dirX * 0 + dirZ * (-cosPitch) - dirY * (-sinPitch);
        double worldDirZ = dirX * (-sinYaw) + dirZ * cosYaw * sinPitch - dirY * cosYaw * cosPitch;

        // 与水平面 y = surfaceY + 0.5 相交
        double targetY = surfaceY + 0.5;
        if (Math.abs(worldDirY) < 0.0001) return null;
        double t = (targetY - camY) / worldDirY;
        if (t < 0) return null;

        double hitX = camX + worldDirX * t;
        double hitZ = camZ + worldDirZ * t;

        return new BlockPos(hitX, surfaceY, hitZ);
    }

    /**
     * 获取从相机出发、经过鼠标位置的射线起点和终点（用于长距离方块检测）。
     */
    public static RayTraceResult raycastBlocks() {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3d start = new Vec3d(ClientRTSState.currentCameraX,
                ClientRTSState.currentCameraY,
                ClientRTSState.currentCameraZ);

        // 使用相机朝向
        float yaw = ClientRTSState.cameraYaw;
        float pitch = ClientRTSState.cameraPitch;
        double yawRad = Math.toRadians(-yaw - 180);
        double pitchRad = Math.toRadians(-pitch);

        double dirX = Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3d end = start.add(dirX * 256, dirY * 256, dirZ * 256);
        return mc.world.rayTraceBlocks(start, end, false, true, false);
    }
}

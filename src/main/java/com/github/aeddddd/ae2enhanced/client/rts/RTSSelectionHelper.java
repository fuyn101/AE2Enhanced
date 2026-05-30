package com.github.aeddddd.ae2enhanced.client.rts;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Mouse;

/**
 * RTS 选取助手 —— 处理从鸟瞰相机到地面方块的射线检测。
 */
public class RTSSelectionHelper {

    /**
     * 获取鼠标当前指向的地面方块位置。
     */
    public static BlockPos getHoveredBlock() {
        Minecraft mc = Minecraft.getMinecraft();

        // 优先使用 mc.objectMouseOver（如果它命中了方块）
        RayTraceResult result = mc.objectMouseOver;
        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
            return result.getBlockPos();
        }

        // objectMouseOver 未命中（reach distance 不够），使用长距离射线
        result = raycastBlocks();
        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
            return result.getBlockPos();
        }

        // 仍未命中，使用地面平面相交
        return raycastGround();
    }

    /**
     * 从相机位置沿相机朝向发射长距离射线，检测方块。
     */
    private static RayTraceResult raycastBlocks() {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3d start = new Vec3d(ClientRTSState.currentCameraX,
                ClientRTSState.currentCameraY,
                ClientRTSState.currentCameraZ);

        Vec3d look = getCameraLookVector();
        Vec3d end = start.add(look.x * 256, look.y * 256, look.z * 256);
        return mc.world.rayTraceBlocks(start, end, false, true, false);
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

        // 获取相机朝向向量
        Vec3d look = getCameraLookVector();

        // 与水平面 y = surfaceY + 0.5 相交
        double targetY = surfaceY + 0.5;
        if (Math.abs(look.y) < 0.0001) return null;
        double t = (targetY - camY) / look.y;
        if (t < 0) return null;

        double hitX = camX + look.x * t;
        double hitZ = camZ + look.z * t;

        return new BlockPos(hitX, surfaceY, hitZ);
    }

    /**
     * 计算相机朝向向量（复刻 Entity.getVectorForRotation，该方法为 protected）。
     */
    private static Vec3d getCameraLookVector() {
        float pitch = ClientRTSState.cameraPitch;
        float yaw = ClientRTSState.cameraYaw;
        double yawRad = Math.toRadians(-yaw - 180.0);
        double pitchRad = Math.toRadians(-pitch);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }
}

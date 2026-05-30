package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * RTS 相机控制器 —— 直接修改 mc.player 的位置/角度实现鸟瞰视角。
 * 不使用 renderViewEntity，避免退出后区块渲染异常。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class RTSCameraController {

    // 进入 RTS 前保存的玩家原始状态
    private static double originalPlayerX, originalPlayerY, originalPlayerZ;
    private static double originalPlayerLastX, originalPlayerLastY, originalPlayerLastZ;
    private static float originalPlayerYaw, originalPlayerPitch;
    private static float originalPlayerPrevYaw, originalPlayerPrevPitch;

    public static void enter(double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null) return;

        originalPlayerX = p.posX;
        originalPlayerY = p.posY;
        originalPlayerZ = p.posZ;
        originalPlayerLastX = p.lastTickPosX;
        originalPlayerLastY = p.lastTickPosY;
        originalPlayerLastZ = p.lastTickPosZ;
        originalPlayerYaw = p.rotationYaw;
        originalPlayerPitch = p.rotationPitch;
        originalPlayerPrevYaw = p.prevRotationYaw;
        originalPlayerPrevPitch = p.prevRotationPitch;

        // 立即设置一次，确保第一帧渲染使用相机位置
        applyCameraToPlayer(p, camX, camY, camZ);
    }

    public static void exit() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null) return;
        restorePlayer(p);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ClientRTSState.isInRTS) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null) return;

        if (event.phase == TickEvent.Phase.START) {
            // 在 onLivingUpdate 之前恢复玩家状态，确保位置包使用原始位置
            restorePlayer(p);
        } else if (event.phase == TickEvent.Phase.END) {
            // 在渲染之前设置相机状态，确保 orientCamera 使用鸟瞰视角
            applyCameraToPlayer(p,
                    ClientRTSState.currentCameraX,
                    ClientRTSState.currentCameraY,
                    ClientRTSState.currentCameraZ);
        }
    }

    private static void applyCameraToPlayer(EntityPlayerSP p, double camX, double camY, double camZ) {
        p.posX = camX;
        p.posY = camY;
        p.posZ = camZ;
        p.lastTickPosX = camX;
        p.lastTickPosY = camY;
        p.lastTickPosZ = camZ;
        p.rotationYaw = ClientRTSState.cameraYaw;
        p.rotationPitch = ClientRTSState.cameraPitch;
        p.prevRotationYaw = ClientRTSState.cameraYaw;
        p.prevRotationPitch = ClientRTSState.cameraPitch;
    }

    private static void restorePlayer(EntityPlayerSP p) {
        p.posX = originalPlayerX;
        p.posY = originalPlayerY;
        p.posZ = originalPlayerZ;
        p.lastTickPosX = originalPlayerLastX;
        p.lastTickPosY = originalPlayerLastY;
        p.lastTickPosZ = originalPlayerLastZ;
        p.rotationYaw = originalPlayerYaw;
        p.rotationPitch = originalPlayerPitch;
        p.prevRotationYaw = originalPlayerPrevYaw;
        p.prevRotationPitch = originalPlayerPrevPitch;
    }
}

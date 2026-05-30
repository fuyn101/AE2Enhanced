package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * RTS 相机控制器 —— 通过每帧临时修改 mc.player 的位置/角度实现鸟瞰视角。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class RTSCameraController {

    private static Entity cameraEntity = null;
    private static double originalPlayerX, originalPlayerY, originalPlayerZ;
    private static double originalPlayerLastX, originalPlayerLastY, originalPlayerLastZ;
    private static float originalPlayerYaw, originalPlayerPitch;
    private static float originalPlayerPrevYaw, originalPlayerPrevPitch;

    public static void enter(double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        originalPlayerX = mc.player.posX;
        originalPlayerY = mc.player.posY;
        originalPlayerZ = mc.player.posZ;
        originalPlayerLastX = mc.player.lastTickPosX;
        originalPlayerLastY = mc.player.lastTickPosY;
        originalPlayerLastZ = mc.player.lastTickPosZ;
        originalPlayerYaw = mc.player.rotationYaw;
        originalPlayerPitch = mc.player.rotationPitch;
        originalPlayerPrevYaw = mc.player.prevRotationYaw;
        originalPlayerPrevPitch = mc.player.prevRotationPitch;

        // 创建虚拟相机实体作为 renderViewEntity（确保 getMouseOver 等使用相机位置）
        if (cameraEntity == null) {
            cameraEntity = new CameraEntity(mc.world);
        }
        cameraEntity.setPosition(camX, camY, camZ);
        cameraEntity.lastTickPosX = camX;
        cameraEntity.lastTickPosY = camY;
        cameraEntity.lastTickPosZ = camZ;
        cameraEntity.rotationYaw = ClientRTSState.cameraYaw;
        cameraEntity.rotationPitch = ClientRTSState.cameraPitch;
        cameraEntity.prevRotationYaw = ClientRTSState.cameraYaw;
        cameraEntity.prevRotationPitch = ClientRTSState.cameraPitch;
        mc.setRenderViewEntity(cameraEntity);
    }

    public static void exit() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        mc.setRenderViewEntity(mc.player);
        restorePlayerState(mc);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ClientRTSState.isInRTS) return;
        if (cameraEntity == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        // 确保 renderViewEntity 没有被重置
        if (mc.getRenderViewEntity() != cameraEntity) {
            mc.setRenderViewEntity(cameraEntity);
        }

        if (event.phase == TickEvent.Phase.START) {
            // 恢复玩家状态（防止 onLivingUpdate 发送错误位置包）
            restorePlayerState(mc);
        } else if (event.phase == TickEvent.Phase.END) {
            // 同步 lastTickPos
            cameraEntity.lastTickPosX = cameraEntity.posX;
            cameraEntity.lastTickPosY = cameraEntity.posY;
            cameraEntity.lastTickPosZ = cameraEntity.posZ;
            cameraEntity.prevRotationYaw = cameraEntity.rotationYaw;
            cameraEntity.prevRotationPitch = cameraEntity.rotationPitch;

            // 平滑插值
            double factor = 0.3;
            ClientRTSState.currentCameraX += (ClientRTSState.targetCameraX - ClientRTSState.currentCameraX) * factor;
            ClientRTSState.currentCameraY += (ClientRTSState.targetCameraY - ClientRTSState.currentCameraY) * factor;
            ClientRTSState.currentCameraZ += (ClientRTSState.targetCameraZ - ClientRTSState.currentCameraZ) * factor;

            cameraEntity.setPosition(
                    ClientRTSState.currentCameraX,
                    ClientRTSState.currentCameraY,
                    ClientRTSState.currentCameraZ
            );
            cameraEntity.rotationYaw = ClientRTSState.cameraYaw;
            cameraEntity.rotationPitch = ClientRTSState.cameraPitch;
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        if (!ClientRTSState.isInRTS) return;
        event.setYaw(ClientRTSState.cameraYaw);
        event.setPitch(ClientRTSState.cameraPitch);
        event.setRoll(0.0f);
    }

    private static void restorePlayerState(Minecraft mc) {
        mc.player.posX = originalPlayerX;
        mc.player.posY = originalPlayerY;
        mc.player.posZ = originalPlayerZ;
        mc.player.lastTickPosX = originalPlayerLastX;
        mc.player.lastTickPosY = originalPlayerLastY;
        mc.player.lastTickPosZ = originalPlayerLastZ;
        mc.player.rotationYaw = originalPlayerYaw;
        mc.player.rotationPitch = originalPlayerPitch;
        mc.player.prevRotationYaw = originalPlayerPrevYaw;
        mc.player.prevRotationPitch = originalPlayerPrevPitch;
    }

    /**
     * 纯客户端虚拟相机实体 —— 不加入世界 tick，仅作为 renderViewEntity 使用。
     */
    private static class CameraEntity extends Entity {
        CameraEntity(World world) {
            super(world);
            this.setEntityId(-9999);
            this.noClip = true;
            this.setSize(0.0f, 0.0f);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(NBTTagCompound compound) {}

        @Override
        protected void writeEntityToNBT(NBTTagCompound compound) {}
    }
}

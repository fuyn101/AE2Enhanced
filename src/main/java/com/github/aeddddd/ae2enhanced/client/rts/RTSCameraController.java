package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * RTS 相机控制器 —— 管理虚拟相机实体、位置插值、渲染视角切换。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class RTSCameraController {

    private static Entity cameraEntity = null;
    private static double originalPlayerX, originalPlayerY, originalPlayerZ;
    private static float originalPlayerYaw, originalPlayerPitch;

    public static void enter(double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        originalPlayerX = mc.player.posX;
        originalPlayerY = mc.player.posY;
        originalPlayerZ = mc.player.posZ;
        originalPlayerYaw = mc.player.rotationYaw;
        originalPlayerPitch = mc.player.rotationPitch;

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
        mc.player.rotationYaw = originalPlayerYaw;
        mc.player.rotationPitch = originalPlayerPitch;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ClientRTSState.isInRTS || event.phase != TickEvent.Phase.END) return;
        if (cameraEntity == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        // 确保 renderViewEntity 没有被其他代码重置
        if (mc.getRenderViewEntity() != cameraEntity) {
            mc.setRenderViewEntity(cameraEntity);
        }

        // 同步 lastTickPos（CameraEntity 不在世界 tick 列表中）
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

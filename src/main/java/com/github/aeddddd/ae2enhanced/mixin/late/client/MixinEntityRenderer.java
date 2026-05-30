package com.github.aeddddd.ae2enhanced.mixin.late.client;

import com.github.aeddddd.ae2enhanced.client.rts.ClientRTSState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin: 强制覆盖 EntityRenderer.orientCamera 中的相机朝向，确保 RTS 鸟瞰视角生效。
 */
@Mixin(value = EntityRenderer.class, remap = false)
public class MixinEntityRenderer {

    @Inject(method = "orientCamera", at = @At("HEAD"))
    private void onOrientCameraHead(float partialTicks, CallbackInfo ci) {
        if (!ClientRTSState.isInRTS) return;

        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) return;

        // 强制覆盖相机实体的朝向，无视任何外部修改
        entity.rotationPitch = ClientRTSState.cameraPitch;
        entity.rotationYaw = ClientRTSState.cameraYaw;
        entity.prevRotationPitch = ClientRTSState.cameraPitch;
        entity.prevRotationYaw = ClientRTSState.cameraYaw;
    }
}

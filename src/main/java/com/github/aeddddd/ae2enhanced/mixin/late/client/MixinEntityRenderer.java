package com.github.aeddddd.ae2enhanced.mixin.late.client;

import com.github.aeddddd.ae2enhanced.client.rts.RTSCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTS 相机注入 —— 在 orientCamera 的 HEAD 完整替换矩阵逻辑
 */
@Mixin(value = EntityRenderer.class, remap = false)
public class MixinEntityRenderer {

    @Inject(method = "func_78467_g", at = @At("HEAD"), cancellable = true)
    private void onOrientCamera(float partialTicks, CallbackInfo ci) {
        if (!RTSCamera.isActive()) return;

        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) return;

        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
        double y = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks;
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;

        // 完整替换模型视图矩阵，与原版第一人称路径等价
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -0.1F);
        GlStateManager.rotate(RTSCamera.getPitch(), 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(RTSCamera.getYaw() + 180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(0.0F, -RTSCamera.getHeight(), 0.0F);
        GlStateManager.translate(0.0F, 0.0F, 0.1F);

        // 更新 ActiveRenderInfo（使用反射避免方法名映射问题）
        try {
            java.lang.reflect.Method updateMethod = ActiveRenderInfo.class.getDeclaredMethod("func_74583_a");
            updateMethod.setAccessible(true);
            updateMethod.invoke(null);
        } catch (Exception e) {
            // fallback: try "update"
            try {
                java.lang.reflect.Method updateMethod = ActiveRenderInfo.class.getDeclaredMethod("update");
                updateMethod.setAccessible(true);
                updateMethod.invoke(null);
            } catch (Exception e2) {
                // ignore
            }
        }

        ci.cancel();
    }
}

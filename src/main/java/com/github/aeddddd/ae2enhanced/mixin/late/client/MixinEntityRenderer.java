package com.github.aeddddd.ae2enhanced.mixin.late.client;

import com.github.aeddddd.ae2enhanced.client.rts.RTSCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.util.glu.Project;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTS 渲染注入 —— 相机矩阵替换 + 移除手臂 + 移除默认选择框
 */
@Mixin(value = EntityRenderer.class, remap = false)
public class MixinEntityRenderer {

    @Inject(method = "func_78467_g", at = @At("HEAD"), cancellable = true)
    private void onOrientCamera(float partialTicks, CallbackInfo ci) {
        if (!RTSCamera.isActive()) return;

        EntityRenderer self = (EntityRenderer) (Object) this;
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
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

    /**
     * 移除第一人称手臂/物品渲染
     */
    @Inject(method = "func_78476_b", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(float partialTicks, int pass, CallbackInfo ci) {
        if (RTSCamera.isActive()) {
            ci.cancel();
        }
    }

    // === 多重保险：确保 RTS 模式下投影矩阵使用 RTSCamera 的 FOV ===

    @Unique
    private float ae2enhanced$originalFov = 0f;
    @Unique
    private boolean ae2enhanced$fovModified = false;

    /**
     * 保险 1：@ModifyArg 修改 gluPerspective 的第一个参数（fovy）
     */
    @ModifyArg(
        method = "func_78479_a",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V"),
        index = 0
    )
    private float modifyGluPerspectiveFov(float fovy) {
        return RTSCamera.isActive() ? RTSCamera.getFov() : fovy;
    }

    /**
     * 保险 2：在 setupCameraTransform 开头临时修改 mc.gameSettings.fovSetting
     * 这样即使 @ModifyArg 因 OptiFine 等 mod 干扰失效，getFOVModifier 也会返回正确值
     */
    @Inject(method = "func_78479_a", at = @At("HEAD"))
    private void ae2enhanced$onSetupCameraTransformHead(float partialTicks, int pass, CallbackInfo ci) {
        if (RTSCamera.isActive()) {
            Minecraft mc = Minecraft.getMinecraft();
            this.ae2enhanced$originalFov = mc.gameSettings.fovSetting;
            mc.gameSettings.fovSetting = RTSCamera.getFov();
            this.ae2enhanced$fovModified = true;
        }
    }

    /**
     * 保险 2 恢复：在 setupCameraTransform 返回时恢复原始 fovSetting
     */
    @Inject(method = "func_78479_a", at = @At("RETURN"))
    private void ae2enhanced$onSetupCameraTransformReturn(float partialTicks, int pass, CallbackInfo ci) {
        if (this.ae2enhanced$fovModified) {
            Minecraft.getMinecraft().gameSettings.fovSetting = this.ae2enhanced$originalFov;
            this.ae2enhanced$fovModified = false;
        }
    }

    /**
     * 移除原版方块选择框描边（黑色描边）
     */
    @Inject(method = "func_184048_a", at = @At("HEAD"), cancellable = true)
    private void onDrawSelectionBox(net.minecraft.entity.player.EntityPlayer player, RayTraceResult movingObjectPositionIn, int execute, float partialTicks, CallbackInfo ci) {
        if (RTSCamera.isActive()) {
            ci.cancel();
        }
    }
}

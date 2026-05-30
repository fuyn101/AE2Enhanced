package com.github.aeddddd.ae2enhanced.mixin.late.client;

import com.github.aeddddd.ae2enhanced.client.rts.RTSCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * RTS 输入注入 —— 将 player.turn() 的参数归零，冻结玩家视角
 */
@Mixin(value = Minecraft.class, remap = false)
public class MixinMinecraft {

    @ModifyVariable(
        method = "func_71407_l",
        ordinal = 0,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;func_70082_c(FF)V")
    )
    private float modifyTurnYaw(float yaw) {
        return RTSCamera.isActive() ? 0f : yaw;
    }

    @ModifyVariable(
        method = "func_71407_l",
        ordinal = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;func_70082_c(FF)V")
    )
    private float modifyTurnPitch(float pitch) {
        return RTSCamera.isActive() ? 0f : pitch;
    }
}

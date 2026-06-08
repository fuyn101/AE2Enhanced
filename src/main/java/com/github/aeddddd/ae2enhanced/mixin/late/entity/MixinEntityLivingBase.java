package com.github.aeddddd.ae2enhanced.mixin.late.entity;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 通用禁疗注入：阻止任何通过 setHealth 增加血量的行为。
 * 完全通用，不针对任何特定 mod。
 */
@Mixin(value = EntityLivingBase.class, remap = true)
public class MixinEntityLivingBase {

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void ae2e$onSetHealth(float health, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (health > self.getHealth() && ItemAdvancedMEOmniTool.hasAntiHeal(self)) {
            ci.cancel();
        }
    }
}

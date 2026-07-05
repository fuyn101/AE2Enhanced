package com.github.aeddddd.ae2enhanced.mixin.late.nuclearcraft;

import net.minecraftforge.fml.common.gameevent.TickEvent;
import nc.radiation.RadiationHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * NuclearCraft 辐射适配：玩家位于个人维度时完全跳过辐射更新。
 *
 * <p>注入 {@link RadiationHandler#updatePlayerRadiation(TickEvent.PlayerTickEvent)} 的 HEAD，
 * 当玩家处于个人维度时直接 cancel，使其不受物品栏/环境辐射影响，也不会累积 totalRads。</p>
 *
 * <p>使用反射访问 {@code PersonalDimensionManager}，避免 Mixin 预处理阶段
 * 直接引用 AE2E 自身类导致 ClassNotFoundException。</p>
 */
@Mixin(value = RadiationHandler.class, remap = false)
public class MixinRadiationHandler {

    private static volatile Boolean pdHelperAvailable;
    private static Method pdHelperMethod;

    @Inject(
            method = "updatePlayerRadiation",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ae2e$skipRadiationInPersonalDim(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (event.player != null && isPersonalDimension(event.player.dimension)) {
            ci.cancel();
        }
    }

    private static boolean isPersonalDimension(int dimId) {
        if (pdHelperAvailable == null) {
            synchronized (MixinRadiationHandler.class) {
                if (pdHelperAvailable == null) {
                    try {
                        Class<?> clazz = Class.forName(
                                "com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager",
                                false,
                                MixinRadiationHandler.class.getClassLoader());
                        pdHelperMethod = clazz.getMethod("isPersonalDimension", int.class);
                        pdHelperAvailable = true;
                    } catch (Throwable t) {
                        pdHelperAvailable = false;
                    }
                }
            }
        }
        if (!pdHelperAvailable || pdHelperMethod == null) {
            return false;
        }
        try {
            Object result = pdHelperMethod.invoke(null, dimId);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }
}

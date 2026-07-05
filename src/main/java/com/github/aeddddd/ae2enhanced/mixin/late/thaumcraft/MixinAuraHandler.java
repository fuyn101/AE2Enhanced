package com.github.aeddddd.ae2enhanced.mixin.late.thaumcraft;

import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumcraft.common.world.aura.AuraHandler;

import java.lang.reflect.Method;
import java.util.Random;

/**
 * Thaumcraft 6 Vis 适配：个人维度新 chunk 初始 vis 固定为 3000。
 *
 * <p>注入 {@link AuraHandler#generateAura(Chunk, Random)} 的 HEAD，
 * 当 chunk 所属世界为个人维度时，直接写入 base=3000、vis=3000、flux=0 并 return，
 * 覆盖 TC6 默认基于生物群系的 aura 生成逻辑。</p>
 *
 * <p>使用反射访问 {@code PersonalDimensionManager}，避免 Mixin 预处理阶段
 * 直接引用 AE2E 自身类导致 ClassNotFoundException。</p>
 */
@Mixin(value = AuraHandler.class, remap = false)
public class MixinAuraHandler {

    private static volatile Boolean pdHelperAvailable;
    private static Method pdHelperMethod;

    @Inject(
            method = "generateAura",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ae2e$setPersonalDimVis(Chunk chunk, Random rand, CallbackInfo ci) {
        if (chunk == null || chunk.getWorld() == null) return;
        int dim = chunk.getWorld().provider.getDimension();
        if (isPersonalDimension(dim)) {
            AuraHandler.addAuraChunk(dim, chunk, (short) 3000, 3000.0f, 0.0f);
            ci.cancel();
        }
    }

    private static boolean isPersonalDimension(int dimId) {
        if (pdHelperAvailable == null) {
            synchronized (MixinAuraHandler.class) {
                if (pdHelperAvailable == null) {
                    try {
                        Class<?> clazz = Class.forName(
                                "com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager",
                                false,
                                MixinAuraHandler.class.getClassLoader());
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

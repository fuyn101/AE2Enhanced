package com.github.aeddddd.ae2enhanced.mixin.late.thaumcraft;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumcraft.common.entities.EntityFluxRift;

import java.lang.reflect.Method;

/**
 * Thaumcraft 6 裂隙适配：禁止在个人维度中生成裂隙。
 *
 * <p>注入 {@link EntityFluxRift#createRift(World, BlockPos)} 的 HEAD，
 * 当世界为个人维度时直接 cancel，不消耗 flux、不生成裂隙实体。</p>
 *
 * <p>注意：使用反射访问 {@code PersonalDimensionManager}，避免 Mixin 预处理阶段
 * 直接引用该类导致 ClassNotFoundException（某些加载顺序下 AE2E 自身类尚未对 Mixin
 * 可见）。</p>
 */
@Mixin(value = EntityFluxRift.class, remap = false)
public class MixinEntityFluxRift {

    private static volatile Boolean pdHelperAvailable;
    private static Method pdHelperMethod;

    @Inject(
            method = "createRift",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ae2e$cancelRiftInPersonalDim(World world, BlockPos pos, CallbackInfo ci) {
        if (world != null && isPersonalDimension(world.provider.getDimension())) {
            ci.cancel();
        }
    }

    private static boolean isPersonalDimension(int dimId) {
        if (pdHelperAvailable == null) {
            synchronized (MixinEntityFluxRift.class) {
                if (pdHelperAvailable == null) {
                    try {
                        Class<?> clazz = Class.forName(
                                "com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager",
                                false,
                                MixinEntityFluxRift.class.getClassLoader());
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

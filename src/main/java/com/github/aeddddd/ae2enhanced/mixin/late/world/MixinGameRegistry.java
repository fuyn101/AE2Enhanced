package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁止原版/Forge 的地形生成管线在个人维度中执行。
 *
 * <p>参考 PersonalWorlds：个人维度使用自定义 ChunkGenerator，但某些模组或 Forge
 * 事件仍可能通过 {@link GameRegistry#generateWorld} 触发装饰/结构生成，导致空世界
 * 出现异常或不必要的计算。此处直接取消个人维度的 generateWorld 调用。</p>
 */
@Mixin(value = GameRegistry.class, remap = false)
public class MixinGameRegistry {

    @Inject(method = "generateWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ae2enhanced$cancelPersonalDimGeneration(int chunkX, int chunkZ, World world,
                                                                 IChunkGenerator chunkGenerator,
                                                                 IChunkProvider chunkProvider,
                                                                 CallbackInfo ci) {
        if (world != null && world.provider != null
                && PersonalDimensionManager.isPersonalDimension(world.provider.getDimension())) {
            ci.cancel();
        }
    }
}

package com.github.aeddddd.ae2enhanced.client.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.shaders.Uniform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 装配枢纽黑洞后处理渲染器。
 * <p>参考 GregTechCEu Modern 的 black_hole.fsh 思路，在屏幕空间对成形装配枢纽进行
 * raymarching 全屏后处理，实现事件视界、引力透镜与吸积盘效果。</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AE2EnhancedPostProcessor {

    private AE2EnhancedPostProcessor() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!AE2EnhancedConfig.CLIENT.enableAssemblyPostProcessing.get()
                || AE2EnhancedConfig.CLIENT.forceCompatibilityMode.get()) {
            return;
        }
        if (!AE2EnhancedShaders.isAssemblyBlackHolePostLoaded()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        if (!(level instanceof ClientLevel) || player == null) {
            return;
        }

        Vec3 eye = event.getCamera().getPosition();
        double renderDist = AE2EnhancedConfig.CLIENT.renderDistance.get();
        int chunkRadius = (int) Math.ceil(renderDist / 16.0);
        ChunkPos playerChunk = player.chunkPosition();

        List<Vec3> targets = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunkAccess = level.getChunk(playerChunk.x + dx, playerChunk.z + dz);
                if (!(chunkAccess instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof AssemblyControllerBlockEntity controller && controller.isFormed()) {
                        Vec3 target = Vec3.atCenterOf(be.getBlockPos());
                        if (target.distanceToSqr(eye) <= renderDist * renderDist) {
                            targets.add(target);
                        }
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        ShaderInstance shader = AE2EnhancedShaders.getAssemblyBlackHolePost();
        if (shader == null) {
            return;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float time = level.getGameTime() + event.getPartialTick();
        float intensity = Mth.clamp(AE2EnhancedConfig.CLIENT.dynamicRenderIntensity.get().floatValue(), 0.0f, 2.0f);
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        int textureId = mc.getMainRenderTarget().getColorTextureId();

        RenderSystem.setShaderTexture(0, textureId);

        for (Vec3 target : targets) {
            renderBlackHole(shader, eye, target, time, intensity, width, height);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void renderBlackHole(ShaderInstance shader, Vec3 eye, Vec3 target, float time, float intensity,
            int width, int height) {
        RenderSystem.setShader(() -> shader);

        Uniform uTime = shader.getUniform("u_time");
        Uniform uResolution = shader.getUniform("u_resolution");
        Uniform uIntensity = shader.getUniform("u_intensity");
        Uniform eyeUniform = shader.getUniform("eye");
        Uniform targetUniform = shader.getUniform("target");

        if (uTime != null) {
            uTime.set(time * 0.05f);
            uTime.upload();
        }
        if (uResolution != null) {
            uResolution.set((float) width, (float) height);
            uResolution.upload();
        }
        if (uIntensity != null) {
            uIntensity.set(intensity);
            uIntensity.upload();
        }
        if (eyeUniform != null) {
            eyeUniform.set((float) eye.x, (float) eye.y, (float) eye.z);
            eyeUniform.upload();
        }
        if (targetUniform != null) {
            targetUniform.set((float) target.x, (float) target.y, (float) target.z);
            targetUniform.upload();
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(-1.0, -1.0, 0.0).endVertex();
        builder.vertex(1.0, -1.0, 0.0).endVertex();
        builder.vertex(1.0, 1.0, 0.0).endVertex();
        builder.vertex(-1.0, 1.0, 0.0).endVertex();
        tesselator.end();
    }
}

package com.github.aeddddd.ae2enhanced.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.shaders.Uniform;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock;
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

    /**
     * 装配枢纽结构中心相对于控制器方块中心的偏移（NORTH 为基准）。
     * <p>来自 assembly_new.json 中 controller 所在坐标 (25, -7, 13) 的反向，
     * 即结构中心 = 控制器中心 - controllerOffset。</p>
     */
    private static final Vec3 CENTER_OFFSET_NORTH = new Vec3(-25.0, 7.0, -13.0);

    private record TargetInfo(Vec3 worldPos, float radius, Direction facing) {
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

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        double renderDist = AE2EnhancedConfig.CLIENT.renderDistance.get();
        int chunkRadius = (int) Math.ceil(renderDist / 16.0);
        ChunkPos playerChunk = player.chunkPosition();

        List<TargetInfo> targets = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunkAccess = level.getChunk(playerChunk.x + dx, playerChunk.z + dz);
                if (!(chunkAccess instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof AssemblyControllerBlockEntity controller && controller.isFormed()) {
                        TargetInfo info = buildTargetInfo(controller, level);
                        if (info != null && info.worldPos.distanceToSqr(eye) <= renderDist * renderDist) {
                            targets.add(info);
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

        float time = level.getGameTime() + event.getPartialTick();
        float intensity = Mth.clamp(AE2EnhancedConfig.CLIENT.dynamicRenderIntensity.get().floatValue(), 0.0f, 2.0f);
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        int textureId = mc.getMainRenderTarget().getColorTextureId();

        for (TargetInfo info : targets) {
            Vector3f screenPos = project(info.worldPos, camera, width, height);
            if (screenPos == null || screenPos.z < 0.0f) {
                continue;
            }
            renderBlackHole(shader, eye, info.worldPos, screenPos, info.radius, time, intensity, width, height, textureId);
        }
    }

    private static TargetInfo buildTargetInfo(AssemblyControllerBlockEntity controller, Level level) {
        BlockPos pos = controller.getBlockPos();
        Direction facing = Direction.NORTH;
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(MultiblockControllerBlock.FACING)) {
            facing = state.getValue(MultiblockControllerBlock.FACING);
        }

        Vec3 centerOffset = getStructureCenterOffset(facing);
        Vec3 worldPos = Vec3.atCenterOf(pos).add(centerOffset);
        return new TargetInfo(worldPos, 9.5f, facing);
    }

    private static Vec3 getStructureCenterOffset(Direction facing) {
        return switch (facing) {
            case NORTH -> CENTER_OFFSET_NORTH;
            case SOUTH -> new Vec3(25.0, 7.0, 13.0);
            case EAST -> new Vec3(13.0, 7.0, -25.0);
            case WEST -> new Vec3(-13.0, 7.0, 25.0);
            default -> CENTER_OFFSET_NORTH;
        };
    }

    /**
     * 将世界坐标投影到屏幕像素坐标。
     * <p>使用相机四元数把向量转到相机局部空间，再做透视投影。
     * 返回的 z 分量为正表示目标在相机前方。</p>
     */
    private static Vector3f project(Vec3 worldPos, Camera camera, int width, int height) {
        Vec3 eye = camera.getPosition();
        Vec3 toTarget = worldPos.subtract(eye);

        Quaternionf camRotInv = camera.rotation().invert(new Quaternionf());
        Vector3f viewSpace = new Vector3f((float) toTarget.x, (float) toTarget.y, (float) toTarget.z);
        camRotInv.transform(viewSpace);

        // 相机局部空间：-z 为前方，+y 为上方，+x 为右方
        float distance = -viewSpace.z;
        if (distance <= 0.01f) {
            return new Vector3f(0.0f, 0.0f, -1.0f);
        }

        float fov = Minecraft.getInstance().options.fov().get().floatValue();
        float f = (float) (height / (2.0 * Math.tan(Math.toRadians(fov) / 2.0)));

        float screenX = viewSpace.x / distance * f + width / 2.0f;
        float screenY = viewSpace.y / distance * f + height / 2.0f;

        return new Vector3f(screenX, screenY, distance);
    }

    private static void renderBlackHole(ShaderInstance shader, Vec3 eye, Vec3 target, Vector3f targetScreen, float size,
            float time, float intensity, int width, int height, int textureId) {
        RenderSystem.setShader(() -> shader);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().identity(), VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderTexture(0, textureId);

        Uniform uTime = shader.getUniform("u_time");
        Uniform uResolution = shader.getUniform("u_resolution");
        Uniform uIntensity = shader.getUniform("u_intensity");
        Uniform uSize = shader.getUniform("u_size");
        Uniform uTargetScreen = shader.getUniform("u_targetScreen");
        Uniform eyeUniform = shader.getUniform("eye");
        Uniform targetUniform = shader.getUniform("target");

        if (uTime != null) {
            uTime.set(time * 0.05f);
        }
        if (uResolution != null) {
            uResolution.set((float) width, (float) height);
        }
        if (uIntensity != null) {
            uIntensity.set(intensity);
        }
        if (uSize != null) {
            uSize.set(size);
        }
        if (uTargetScreen != null) {
            uTargetScreen.set(targetScreen.x, targetScreen.y);
        }
        if (eyeUniform != null) {
            eyeUniform.set((float) eye.x, (float) eye.y, (float) eye.z);
        }
        if (targetUniform != null) {
            targetUniform.set((float) target.x, (float) target.y, (float) target.z);
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(-1.0, -1.0, 0.0).endVertex();
        builder.vertex(1.0, -1.0, 0.0).endVertex();
        builder.vertex(1.0, 1.0, 0.0).endVertex();
        builder.vertex(-1.0, 1.0, 0.0).endVertex();
        tesselator.end();

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }
}

package com.github.aeddddd.ae2enhanced.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.shaders.Uniform;

import org.lwjgl.opengl.GL11;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
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

    private static TextureTarget intermediateTarget;

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
        // 先把主渲染目标颜色复制到中间纹理，避免 shader 同时读写同一纹理产生 feedback loop / 撕裂
        int textureId = copyMainToIntermediate(mc.getMainRenderTarget(), width, height);
        // Minecraft options.fov() 已经是垂直 FOV，shader 中 rayDirection 直接使用垂直 FOV
        float fov = mc.options.fov().get().floatValue();

        for (TargetInfo info : targets) {
            Vector3f screenPos = project(info.worldPos, camera, width, height);
            if (screenPos == null || screenPos.z < 0.0f) {
                continue;
            }
            // 只有当黑洞效果完全在屏幕外时才剔除，允许效果在屏幕边缘附近仍被渲染
            double distance = eye.distanceTo(info.worldPos);
            double worldRadius = info.radius * 10.0;
            double focalLength = height / (2.0 * Math.tan(Math.toRadians(fov) / 2.0));
            double screenRadius = (worldRadius / distance) * focalLength;
            if (screenPos.x + screenRadius < 0 || screenPos.x - screenRadius > width
                    || screenPos.y + screenRadius < 0 || screenPos.y - screenRadius > height) {
                continue;
            }
            // 遮挡检测：视线被方块挡住时不渲染
            if (isOccluded(level, eye, info.worldPos, player)) {
                continue;
            }
            renderBlackHole(shader, eye, info.worldPos, screenPos, info.radius, time, intensity, fov, width, height, textureId);
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
        return new TargetInfo(worldPos, 1.2f, facing);
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
     * 将主渲染目标颜色复制到中间纹理，避免 shader 同时读写同一纹理产生 feedback loop / 撕裂。
     */
    private static int copyMainToIntermediate(RenderTarget mainTarget, int width, int height) {
        if (intermediateTarget == null || intermediateTarget.width != width || intermediateTarget.height != height) {
            if (intermediateTarget != null) {
                intermediateTarget.destroyBuffers();
            }
            intermediateTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            intermediateTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            intermediateTarget.clear(Minecraft.ON_OSX);
        }
        // 确保中间纹理已分配存储
        intermediateTarget.bindWrite(false);
        intermediateTarget.unbindWrite();

        mainTarget.bindRead();
        RenderSystem.bindTexture(intermediateTarget.getColorTextureId());
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        mainTarget.unbindRead();

        return intermediateTarget.getColorTextureId();
    }

    /**
     * 检查从 eye 到 target 的视线是否被方块遮挡。
     */
    private static boolean isOccluded(Level level, Vec3 eye, Vec3 target, Player player) {
        double distToTarget = eye.distanceToSqr(target);
        if (distToTarget < 0.01) {
            return false;
        }
        Vec3 direction = target.subtract(eye);
        Vec3 end = target.subtract(direction.normalize().scale(0.15));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation().distanceToSqr(eye) < distToTarget - 0.01;
        }
        return false;
    }

    /**
     * 将世界坐标投影到屏幕像素坐标。
     * <p>手动构建 viewMatrix = R * T，其中 R 是相机旋转的逆，T 是 -eye 平移；
     * 然后 viewProj = projection * viewMatrix，使用 JOML project 得到屏幕坐标。</p>
     */
    private static Vector3f project(Vec3 worldPos, Camera camera, int width, int height) {
        Vec3 eye = camera.getPosition();

        Matrix4f viewMatrix = new Matrix4f().identity()
                .rotate(camera.rotation().invert(new Quaternionf()))
                .translate((float) -eye.x, (float) -eye.y, (float) -eye.z);

        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();
        Matrix4f viewProj = new Matrix4f(projectionMatrix).mul(viewMatrix);

        Vector3f src = new Vector3f((float) worldPos.x, (float) worldPos.y, (float) worldPos.z);
        Vector3f dest = new Vector3f();
        viewProj.project(src, new int[]{0, 0, width, height}, dest);
        return dest;
    }

    private static void renderBlackHole(ShaderInstance shader, Vec3 eye, Vec3 target, Vector3f targetScreen, float size,
            float time, float intensity, float fov, int width, int height, int textureId) {
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
        Uniform uFov = shader.getUniform("u_fov");
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
        if (uFov != null) {
            uFov.set(fov);
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

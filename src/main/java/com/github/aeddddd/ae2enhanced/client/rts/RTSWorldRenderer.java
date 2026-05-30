package com.github.aeddddd.ae2enhanced.client.rts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.Set;

/**
 * RTS 世界渲染 —— 选区线框 + 锚点 + 平台边界 + 区块网格 + 悬停高亮
 */
public class RTSWorldRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!RTSCamera.isActive()) return;

        // 每帧更新射线检测
        RTSInputHandler.updateRaycast();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        double px = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * event.getPartialTicks();
        double py = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * event.getPartialTicks();
        double pz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.glLineWidth(2.0f);

        // 1. 绘制平台边界（红色）
        drawPlatformBounds();

        // 2. 绘制锚点高亮
        if (RTSSelection.getMode() == RTSSelection.Mode.ANCHOR_A_SET && RTSSelection.getAnchorA() != null) {
            drawAnchorPulse(RTSSelection.getAnchorA(), event.getPartialTicks());
        }

        // 4. 绘制悬停方块高亮（绿色）
        if (RTSInputHandler.isLastHitValid() && RTSInputHandler.getLastHitPos() != null) {
            GlStateManager.color(0.3f, 1.0f, 0.3f, 1.0f);
            GlStateManager.glLineWidth(2.5f);
            drawBlockOutline(RTSInputHandler.getLastHitPos());
        }

        // 5. 绘制选中方块线框（金黄色）
        drawSelection(RTSSelection.getSelectedBlocks());

        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void drawPlatformBounds() {
        BlockPos min = RTSCamera.getPlatformMin();
        BlockPos max = RTSCamera.getPlatformMax();
        int surfaceY = RTSCamera.getPlatformSurfaceY();

        GlStateManager.color(1.0f, 0.0f, 0.0f, 1.0f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION);

        // 底部矩形（表面 Y）
        buf.pos(min.getX(), surfaceY + 0.05, min.getZ()).endVertex();
        buf.pos(max.getX() + 1, surfaceY + 0.05, min.getZ()).endVertex();
        buf.pos(max.getX() + 1, surfaceY + 0.05, max.getZ() + 1).endVertex();
        buf.pos(min.getX(), surfaceY + 0.05, max.getZ() + 1).endVertex();
        tess.draw();
    }

    private void drawAnchorPulse(BlockPos pos, float partialTicks) {
        float pulse = (float) Math.sin((Minecraft.getMinecraft().world.getTotalWorldTime() + partialTicks) * 0.3) * 0.3f + 0.7f;
        GlStateManager.color(1.0f, 1.0f, 1.0f, pulse);
        GlStateManager.glLineWidth(3.0f);
        drawBlockOutline(pos);
    }

    private void drawSelection(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) return;

        GlStateManager.color(1.0f, 0.85f, 0.2f, 1.0f);
        GlStateManager.glLineWidth(2.0f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);

        for (BlockPos pos : blocks) {
            addBlockOutlineVertices(buf, pos);
        }

        tess.draw();
    }

    private void drawBlockOutline(BlockPos pos) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        addBlockOutlineVertices(buf, pos);
        tess.draw();
    }

    private void addBlockOutlineVertices(BufferBuilder buf, BlockPos pos) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        // 底面
        buf.pos(x, y, z).endVertex(); buf.pos(x + 1, y, z).endVertex();
        buf.pos(x + 1, y, z).endVertex(); buf.pos(x + 1, y, z + 1).endVertex();
        buf.pos(x + 1, y, z + 1).endVertex(); buf.pos(x, y, z + 1).endVertex();
        buf.pos(x, y, z + 1).endVertex(); buf.pos(x, y, z).endVertex();

        // 顶面
        buf.pos(x, y + 1, z).endVertex(); buf.pos(x + 1, y + 1, z).endVertex();
        buf.pos(x + 1, y + 1, z).endVertex(); buf.pos(x + 1, y + 1, z + 1).endVertex();
        buf.pos(x + 1, y + 1, z + 1).endVertex(); buf.pos(x, y + 1, z + 1).endVertex();
        buf.pos(x, y + 1, z + 1).endVertex(); buf.pos(x, y + 1, z).endVertex();

        // 竖线
        buf.pos(x, y, z).endVertex(); buf.pos(x, y + 1, z).endVertex();
        buf.pos(x + 1, y, z).endVertex(); buf.pos(x + 1, y + 1, z).endVertex();
        buf.pos(x + 1, y, z + 1).endVertex(); buf.pos(x + 1, y + 1, z + 1).endVertex();
        buf.pos(x, y, z + 1).endVertex(); buf.pos(x, y + 1, z + 1).endVertex();
    }
}

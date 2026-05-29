package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.Set;

/**
 * RTS 选区线框渲染器 —— 在 RenderWorldLastEvent 中绘制选中方块的高亮线框。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class RTSSelectionRenderer {

    private static final float LINE_R = 0.0f;
    private static final float LINE_G = 1.0f;
    private static final float LINE_B = 1.0f;
    private static final float LINE_ALPHA = 0.8f;
    private static final float LINE_WIDTH = 2.0f;

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ClientRTSState.isInRTS) return;
        if (ClientRTSState.currentSelection.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // 获取渲染偏移（相对玩家位置）
        double renderX = mc.getRenderManager().viewerPosX;
        double renderY = mc.getRenderManager().viewerPosY;
        double renderZ = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-renderX, -renderY, -renderZ);

        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.glLineWidth(LINE_WIDTH);
        GlStateManager.color(LINE_R, LINE_G, LINE_B, LINE_ALPHA);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);

        Set<BlockPos> blocks = ClientRTSState.currentSelection.getSelectedBlocks();
        for (BlockPos pos : blocks) {
            drawBlockOutline(buffer, pos);
        }

        tessellator.draw();

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.glLineWidth(1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawBlockOutline(BufferBuilder buffer, BlockPos pos) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double x1 = x;
        double y1 = y;
        double z1 = z;
        double x2 = x + 1.0;
        double y2 = y + 1.0;
        double z2 = z + 1.0;

        // 12 条边
        // 底面
        addLine(buffer, x1, y1, z1, x2, y1, z1);
        addLine(buffer, x2, y1, z1, x2, y1, z2);
        addLine(buffer, x2, y1, z2, x1, y1, z2);
        addLine(buffer, x1, y1, z2, x1, y1, z1);
        // 顶面
        addLine(buffer, x1, y2, z1, x2, y2, z1);
        addLine(buffer, x2, y2, z1, x2, y2, z2);
        addLine(buffer, x2, y2, z2, x1, y2, z2);
        addLine(buffer, x1, y2, z2, x1, y2, z1);
        // 竖边
        addLine(buffer, x1, y1, z1, x1, y2, z1);
        addLine(buffer, x2, y1, z1, x2, y2, z1);
        addLine(buffer, x2, y1, z2, x2, y2, z2);
        addLine(buffer, x1, y1, z2, x1, y2, z2);
    }

    private static void addLine(BufferBuilder buffer, double x1, double y1, double z1,
                                 double x2, double y2, double z2) {
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
    }
}

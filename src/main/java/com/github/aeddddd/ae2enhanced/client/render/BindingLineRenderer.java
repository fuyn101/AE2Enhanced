package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 客户端绑定高亮渲染器。
 *
 * 当玩家主手持有 Universal Memory Card 且内存卡记录了绑定源时，
 * 为 Central ME Interface 和目标机器渲染高亮描边边框：
 * - 中枢 ME 接口：青色描边
 * - 目标机器：橙色描边
 */
public class BindingLineRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        ItemStack held = player.getHeldItemMainhand();
        if (!(held.getItem() instanceof ItemUniversalMemoryCard)) return;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.glLineWidth(2.5f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        boolean any = false;
        for (net.minecraft.tileentity.TileEntity te : player.world.loadedTileEntityList) {
            if (!(te instanceof TileCentralMEInterface)) continue;
            TileCentralMEInterface source = (TileCentralMEInterface) te;
            if (source.getPos().distanceSq(player.posX, player.posY, player.posZ) > 32.0 * 32.0) continue;

            List<TargetBinding> bindings = source.getInterfaceDuality().getBindings();
            if (bindings.isEmpty()) continue;

            any = true;
            AxisAlignedBB sourceAabb = new AxisAlignedBB(source.getPos()).grow(0.002);
            drawBoxOutline(buf, sourceAabb, 0.0f, 1.0f, 1.0f, 0.85f);

            for (TargetBinding target : bindings) {
                AxisAlignedBB targetAabb = new AxisAlignedBB(target.pos).grow(0.002);
                drawBoxOutline(buf, targetAabb, 1.0f, 0.65f, 0.0f, 0.85f);
            }
        }

        if (any) {
            tess.draw();
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawBoxOutline(BufferBuilder buf, AxisAlignedBB aabb, float r, float g, float b, float a) {
        double minX = aabb.minX, minY = aabb.minY, minZ = aabb.minZ;
        double maxX = aabb.maxX, maxY = aabb.maxY, maxZ = aabb.maxZ;

        // 底面 4 条边
        buf.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, minZ).color(r, g, b, a).endVertex();

        // 顶面 4 条边
        buf.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();

        // 侧面 4 条边
        buf.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
    }
}

package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * E2a：为 ItemGasDrop 提供内置物品渲染器。
 * 绘制纯色 quad，颜色由气体名称哈希生成。
 */
public class GasItemRenderer extends TileEntityItemStackRenderer {

    public static final GasItemRenderer INSTANCE = new GasItemRenderer();

    private GasItemRenderer() {
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        String gasName = ItemGasDrop.getGasName(stack);
        if (gasName == null) return;

        // 由气体名称生成确定性颜色
        int hash = gasName.hashCode();
        float r = ((hash >> 16) & 0xFF) / 255.0f;
        float g = ((hash >> 8) & 0xFF) / 255.0f;
        float b = (hash & 0xFF) / 255.0f;
        float a = 0.9f;

        GlStateManager.pushMatrix();
        Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.color(r, g, b, a);

        Tessellator tess = Tessellator.getInstance();
        tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);

        tess.getBuffer().pos(0.0, 1.0, 0.0).tex(0.0, 1.0)
                .color((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255)).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(1.0, 1.0, 0.0).tex(1.0, 1.0)
                .color((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255)).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(1.0, 0.0, 0.0).tex(1.0, 0.0)
                .color((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255)).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(0.0, 0.0, 0.0).tex(0.0, 0.0)
                .color((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255)).normal(0, 0, 1).endVertex();

        tess.draw();

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}

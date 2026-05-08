package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

/**
 * E2a：为 ItemFluidDrop 提供内置物品渲染器。
 * 从 TextureMap 获取流体的实际 still sprite 并渲染，而非纯色 quad。
 */
public class FluidItemRenderer extends TileEntityItemStackRenderer {

    public static final FluidItemRenderer INSTANCE = new FluidItemRenderer();

    private FluidItemRenderer() {
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
        if (fluid == null || fluid.getFluid() == null) return;

        TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite sprite = textureMap.getAtlasSprite(fluid.getFluid().getStill(fluid).toString());
        if (sprite == null) {
            // fallback to missing texture sprite
            sprite = textureMap.getMissingSprite();
        }

        int color = fluid.getFluid().getColor(fluid);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a <= 0) a = 1.0f;

        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();

        GlStateManager.pushMatrix();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.color(r, g, b, a);

        Tessellator tess = Tessellator.getInstance();
        tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);

        tess.getBuffer().pos(0.0, 1.0, 0.0).tex(minU, maxV)
                .color((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255)).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(1.0, 1.0, 0.0).tex(maxU, maxV)
                .color((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255)).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(1.0, 0.0, 0.0).tex(maxU, minV)
                .color((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255)).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(0.0, 0.0, 0.0).tex(minU, minV)
                .color((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255)).normal(0, 0, 1).endVertex();

        tess.draw();

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}

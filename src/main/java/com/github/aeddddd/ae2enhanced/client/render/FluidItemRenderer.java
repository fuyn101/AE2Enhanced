package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

/**
 * E2a：为 ItemFluidDrop 提供内置物品渲染器。
 * 绘制纯色 quad，颜色取自 FluidStack 的流体颜色。
 */
public class FluidItemRenderer extends TileEntityItemStackRenderer {

    public static final FluidItemRenderer INSTANCE = new FluidItemRenderer();

    private FluidItemRenderer() {
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
        if (fluid == null) return;

        int color = fluid.getFluid().getColor(fluid);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a <= 0) a = 1.0f;

        GlStateManager.pushMatrix();
        Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.color(r, g, b, a);

        Tessellator tess = Tessellator.getInstance();
        tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);

        // 使用白色 sprite 的 UV（TextureMap 中默认的 missing 或任意 sprite 的 UV 都可以，因为我们只关心颜色）
        // 使用 (0,0)-(1,1) 的 UV，绑定 atlas 后 atlas 的左上角
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

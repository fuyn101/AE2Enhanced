package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.client.gui.widgets.ITooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/**
 * E1a：通用总线模式切换按钮。
 * 显示当前调度模式的缩写文本，点击循环切换。
 */
public class GuiBusModeButton extends GuiButton implements ITooltip {

    private static final int[] COLORS = {
            0x00AAFF, // SEQUENTIAL - 蓝
            0xFFAA00, // ROUND_ROBIN - 橙
            0xAA00FF, // RANDOM - 紫
            0x00FF66  // GREEDY - 绿
    };

    private static final String[] ABBREVS = { "SEQ", "RR", "RND", "ALL" };
    private static final String[] NAMES = {
            "Sequential", "Round Robin", "Random", "Greedy"
    };

    private int modeIndex = 0;

    public GuiBusModeButton(int x, int y) {
        super(0, x, y, 16, 16, "");
    }

    public void setMode(int index) {
        if (index >= 0 && index < ABBREVS.length) {
            this.modeIndex = index;
        }
    }

    public int getModeIndex() {
        return this.modeIndex;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        // 绘制按钮背景
        mc.getTextureManager().bindTexture(GuiButton.BUTTON_TEXTURES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        int i = this.getHoverState(this.hovered);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        this.drawTexturedModalRect(this.x, this.y, 0, 46 + i * 20, this.width / 2, this.height);
        this.drawTexturedModalRect(this.x + this.width / 2, this.y, 200 - this.width / 2, 46 + i * 20, this.width / 2, this.height);

        // 绘制颜色标识（小方块）
        int color = COLORS[this.modeIndex];
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        GlStateManager.color(r, g, b, 1.0f);
        drawRect(this.x + 2, this.y + 2, this.x + this.width - 2, this.y + this.height - 2, color | 0xFF000000);

        // 绘制文本缩写
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        String text = ABBREVS[this.modeIndex];
        int textColor = 0xFFFFFF;
        if (i == 2) { // 悬停
            textColor = 0xFFFFA0;
        }
        mc.fontRenderer.drawStringWithShadow(text, this.x + (this.width - mc.fontRenderer.getStringWidth(text)) / 2, this.y + 4, textColor);
    }

    @Override
    public String getMessage() {
        return "Bus Mode\n" + NAMES[this.modeIndex];
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }
}

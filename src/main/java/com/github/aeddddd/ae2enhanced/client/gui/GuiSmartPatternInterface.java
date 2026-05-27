package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 智能样板接口的客户端 GUI。
 *
 * 基于 UV 分析报告 (smart_pattern_gui_uv_analysis.md) 绘制：
 * - 主面板背景 (0, 0, 195, 224)
 * - 标题栏与按钮区 (Y: 17-35)
 * - 上部配方网格 9x5 (Y: 37-125)
 * - 右侧滚动条 (174, 37, 10, 88)
 * - 玩家背包 (Y: 141-177) + 快捷栏 (Y: 199)
 */
public class GuiSmartPatternInterface extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("ae2enhanced", "textures/gui/smart_pattern_interface_gui.png");

    // 配方网格坐标（UV像素坐标，用于 drawTexturedModalRect）
    private static final int[] COL_X = {8, 26, 44, 62, 80, 98, 116, 134, 152};
    private static final int[] ROW_Y = {37, 55, 73, 91, 109};

    // 按钮坐标
    private static final int BTN_PREV_X = 116;
    private static final int BTN_ENCODE_X = 134;
    private static final int BTN_NEXT_X = 152;
    private static final int BTN_Y = 20;
    private static final int BTN_SIZE = 16;

    // 滚动条坐标
    private static final int SCROLL_X = 174;
    private static final int SCROLL_Y = 37;
    private static final int SCROLL_W = 10;
    private static final int SCROLL_H = 88;

    private final TileSmartPatternInterface tile;

    public GuiSmartPatternInterface(InventoryPlayer inventoryPlayer, TileSmartPatternInterface tile) {
        super(new ContainerSmartPatternInterface(inventoryPlayer, tile));
        this.tile = tile;
        this.xSize = 195;
        this.ySize = 224;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        // 绘制主面板背景 (0, 0, 195, 224)
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        String title = I18n.format("gui.ae2enhanced.smart_pattern_interface.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        // 绑定状态
        if (tile.isBound()) {
            String boundText = I18n.format("gui.ae2enhanced.smart_pattern_interface.bound_to",
                    tile.getBoundBlockId());
            this.fontRenderer.drawString(boundText, 8, 21, 0x404040);
        } else {
            String noTarget = I18n.format("gui.ae2enhanced.smart_pattern_interface.no_target");
            this.fontRenderer.drawString(noTarget, 8, 21, 0xAA0000);
        }

        // 玩家背包标签
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 129, 0x404040);

        // 按钮提示（暂定，后续添加实际按钮交互）
        // encode 按钮 hover 提示
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;
        if (isInEncodeButton(relX, relY)) {
            this.drawHoveringText(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_tooltip"), mouseX - this.guiLeft, mouseY - this.guiTop);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    private boolean isInEncodeButton(int x, int y) {
        return x >= BTN_ENCODE_X && x < BTN_ENCODE_X + BTN_SIZE
            && y >= BTN_Y && y < BTN_Y + BTN_SIZE;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 编码按钮点击
        if (isInEncodeButton(relX, relY) && mouseButton == 0) {
            // TODO: 发送编码请求网络包
            return;
        }

        // 配方槽位点击 (禁用/启用切换)
        // TODO: Phase 5 实现配方槽位点击交互
    }
}

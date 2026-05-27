package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 智能样板接口的客户端 GUI。
 *
 * 基于 UV 分析报告绘制：
 * - 主面板背景 (0, 0, 195, 224)
 * - 标题栏 (Y: 17-35)
 * - 输入槽 (116, 20) / 编码按钮 (134, 20) / 输出槽 (152, 20)
 * - 上部配方网格 9x5 (Y: 37-125)
 * - 禁用配方覆盖 X 标记（从纹理 (8, 37, 16, 15) 提取）
 * - 右侧滚动条 (174, 37, 10, 88)
 * - 玩家背包 (Y: 141-177) + 快捷栏 (Y: 199)
 */
public class GuiSmartPatternInterface extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("ae2enhanced", "textures/gui/smart_pattern_interface_gui.png");

    // 配方网格坐标
    private static final int[] COL_X = {8, 26, 44, 62, 80, 98, 116, 134, 152};
    private static final int[] ROW_Y = {37, 55, 73, 91, 109};

    // 编码按钮
    private static final int BTN_ENCODE_X = 134;
    private static final int BTN_ENCODE_Y = 20;
    private static final int BTN_SIZE = 16;

    // X 标记纹理坐标（Row0-Col0 的禁用标记）
    private static final int MARK_X_U = 8;
    private static final int MARK_Y_V = 37;
    private static final int MARK_W = 16;
    private static final int MARK_H = 15;

    private final TileSmartPatternInterface tile;
    private int scrollOffset = 0;

    public GuiSmartPatternInterface(InventoryPlayer inventoryPlayer, TileSmartPatternInterface tile) {
        super(new ContainerSmartPatternInterface(inventoryPlayer, tile));
        this.tile = tile;
        this.xSize = 195;
        this.ySize = 224;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        // 主面板背景
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

        // 绘制禁用标记 X
        drawDisabledMarkers();

        // 按钮 hover 提示
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;
        if (isInEncodeButton(relX, relY)) {
            this.drawHoveringText(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_tooltip"),
                    relX, relY);
        }
    }

    /**
     * 在禁用的配方槽位上绘制 X 标记。
     */
    private void drawDisabledMarkers() {
        SmartPatternData data = tile.getPatternData();
        if (data == null) return;

        this.mc.getTextureManager().bindTexture(TEXTURE);
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int recipeIndex = scrollOffset * 9 + slotIndex;
                if (data.isDisabled(recipeIndex)) {
                    int x = COL_X[col];
                    int y = ROW_Y[row];
                    // 绘制 X 标记覆盖
                    this.drawTexturedModalRect(x, y, MARK_X_U, MARK_Y_V, MARK_W, MARK_H);
                }
                slotIndex++;
            }
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
            && y >= BTN_ENCODE_Y && y < BTN_ENCODE_Y + BTN_SIZE;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 编码按钮点击
        if (isInEncodeButton(relX, relY) && mouseButton == 0) {
            // TODO: Phase 5 发送编码请求网络包
            return;
        }

        // 配方槽位点击 (禁用/启用切换)
        if (mouseButton == 0) {
            int clickedSlot = getRecipeSlotAt(relX, relY);
            if (clickedSlot >= 0) {
                int recipeIndex = scrollOffset * 9 + clickedSlot;
                // TODO: Phase 5 发送禁用/启用切换网络包
            }
        }
    }

    /**
     * 根据鼠标坐标获取配方槽位索引 (0~44)，若无则返回 -1。
     */
    private int getRecipeSlotAt(int x, int y) {
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int sx = COL_X[col];
                int sy = ROW_Y[row];
                if (x >= sx && x < sx + 16 && y >= sy && y < sy + 15) {
                    return slotIndex;
                }
                slotIndex++;
            }
        }
        return -1;
    }
}

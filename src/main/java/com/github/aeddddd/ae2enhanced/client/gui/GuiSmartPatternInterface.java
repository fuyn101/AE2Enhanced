package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternEncode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternScroll;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternToggle;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    // 滚动条区域
    private static final int SCROLL_X = 174;
    private static final int SCROLL_Y = 37;
    private static final int SCROLL_W = 10;
    private static final int SCROLL_H = 88;
    private static final int THUMB_HEIGHT = 17;

    private final TileSmartPatternInterface tile;
    private boolean isScrolling = false;

    // 点击闪烁反馈
    private int flashSlot = -1;
    private int flashTicks = 0;
    private static final int FLASH_DURATION = 8;

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

        // 绘制滚动条滑块
        drawScrollThumb();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        String title = I18n.format("gui.ae2enhanced.smart_pattern_interface.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        // 绑定状态（去掉 @meta 后缀用于显示）
        if (tile.isBound()) {
            String rawId = tile.getBoundBlockId();
            String displayId = rawId.contains("@") ? rawId.substring(0, rawId.indexOf('@')) : rawId;
            String boundText = I18n.format("gui.ae2enhanced.smart_pattern_interface.bound_to", displayId);
            this.fontRenderer.drawString(boundText, 8, 21, 0x404040);
        } else {
            String noTarget = I18n.format("gui.ae2enhanced.smart_pattern_interface.no_target");
            this.fontRenderer.drawString(noTarget, 8, 21, 0xAA0000);
        }

        // 玩家背包标签
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 129, 0x404040);

        // 绘制禁用标记 X
        drawDisabledMarkers();

        // 绘制冲突标记（红色背景覆盖）
        drawConflictMarkers();

        // 绘制点击闪烁反馈
        drawFlashEffect();
    }

    /**
     * 绘制点击后的白色闪烁反馈。
     */
    private void drawFlashEffect() {
        if (flashSlot < 0 || flashTicks <= 0) return;
        int row = flashSlot / 9;
        int col = flashSlot % 9;
        if (row < ROW_Y.length && col < COL_X.length) {
            int x = COL_X[col];
            int y = ROW_Y[row];
            int alpha = (int) (0x55 * flashTicks / FLASH_DURATION);
            drawRect(x, y, x + 16, y + 15, (alpha << 24) | 0xFFFFFF);
        }
    }

    /**
     * 绘制滚动条滑块。
     */
    private void drawScrollThumb() {
        SmartPatternData data = tile.getPatternData();
        if (data == null || data.getRecipeCount() <= 45) return;

        int maxOffset = Math.max(0, (data.getRecipeCount() - 1) / 9 - 4);
        if (maxOffset <= 0) return;

        int scrollOffset = tile.getScrollOffset();
        int trackHeight = SCROLL_H - THUMB_HEIGHT;
        int thumbY = SCROLL_Y + (scrollOffset * trackHeight / maxOffset);

        this.mc.getTextureManager().bindTexture(TEXTURE);
        // 滑块纹理在 GUI 纹理中的位置，使用 (195, 0) 作为滑块 UV（需在纹理中预留）
        // 若纹理中无此区域，则使用纯色填充
        this.drawTexturedModalRect(guiLeft + SCROLL_X, guiTop + thumbY, 195, 0, SCROLL_W, THUMB_HEIGHT);
    }

    /**
     * 在禁用的配方槽位上绘制 X 标记。
     */
    private void drawDisabledMarkers() {
        SmartPatternData data = tile.getPatternData();
        if (data == null) return;

        int scrollOffset = tile.getScrollOffset();
        this.mc.getTextureManager().bindTexture(TEXTURE);
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int sortedIndex = scrollOffset * 9 + slotIndex;
                if (data.isDisabled(sortedIndex)) {
                    int x = COL_X[col];
                    int y = ROW_Y[row];
                    this.drawTexturedModalRect(x, y, MARK_X_U, MARK_Y_V, MARK_W, MARK_H);
                }
                slotIndex++;
            }
        }
    }

    /**
     * 在冲突的配方槽位上绘制高亮红色覆盖和边框。
     */
    private void drawConflictMarkers() {
        SmartPatternData data = tile.getPatternData();
        if (data == null) return;

        int scrollOffset = tile.getScrollOffset();
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int sortedIndex = scrollOffset * 9 + slotIndex;
                if (sortedIndex < data.getRecipeCount() && data.isConflict(sortedIndex)) {
                    int x = COL_X[col];
                    int y = ROW_Y[row];
                    // 半透明红色填充
                    drawRect(x, y, x + 16, y + 15, 0x66FF0000);
                    // 红色边框
                    drawRect(x, y, x + 16, y + 1, 0xFFFF0000);
                    drawRect(x, y + 14, x + 16, y + 15, 0xFFFF0000);
                    drawRect(x, y, x + 1, y + 15, 0xFFFF0000);
                    drawRect(x + 15, y, x + 16, y + 15, 0xFFFF0000);
                }
                slotIndex++;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (flashTicks > 0) flashTicks--;
        if (flashTicks <= 0) flashSlot = -1;
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 编码按钮 tooltip
        if (isInEncodeButton(relX, relY)) {
            List<String> lines = getEncodeButtonTooltip();
            this.drawHoveringText(lines, mouseX, mouseY);
            return;
        }

        // 配方槽位 tooltip（禁用/冲突信息）
        int slot = getRecipeSlotAt(relX, relY);
        if (slot >= 0) {
            List<String> lines = getRecipeSlotTooltip(slot);
            if (!lines.isEmpty()) {
                this.drawHoveringText(lines, mouseX, mouseY);
                return;
            }
        }

        // 滚动条 tooltip：显示配方数量
        if (isInScrollBar(relX, relY)) {
            SmartPatternData data = tile.getPatternData();
            if (data != null) {
                int enabled = data.getEnabledCount();
                int total = data.getRecipeCount();
                String text = I18n.format("gui.ae2enhanced.smart_pattern_interface.recipe_count", enabled, total);
                this.drawHoveringText(java.util.Collections.singletonList(text), mouseX, mouseY);
                return;
            }
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private List<String> getEncodeButtonTooltip() {
        List<String> lines = new ArrayList<>();
        SmartPatternData data = tile.getPatternData();

        if (data == null) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_disabled_no_data"));
            return lines;
        }

        if (data.hasConflicts()) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_disabled_conflict"));
            return lines;
        }

        ItemStack input = tile.getInventory().getStackInSlot(0);
        if (input.isEmpty() || !(input.getItem() instanceof ItemSmartBlankPattern)) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_disabled_no_blank"));
            return lines;
        }

        if (!tile.getInventory().getStackInSlot(1).isEmpty()) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_disabled_output_full"));
            return lines;
        }

        lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.encode_tooltip"));
        return lines;
    }

    private List<String> getRecipeSlotTooltip(int slot) {
        List<String> lines = new ArrayList<>();
        SmartPatternData data = tile.getPatternData();
        if (data == null) return lines;

        int sortedIndex = tile.getScrollOffset() * 9 + slot;
        if (sortedIndex >= data.getRecipeCount()) return lines;

        if (data.isConflict(sortedIndex)) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.conflict_marker"));
        }
        if (data.isDisabled(sortedIndex)) {
            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.disabled_marker"));
        }
        return lines;
    }

    private boolean isInEncodeButton(int x, int y) {
        return x >= BTN_ENCODE_X && x < BTN_ENCODE_X + BTN_SIZE
            && y >= BTN_ENCODE_Y && y < BTN_ENCODE_Y + BTN_SIZE;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 编码按钮点击
        if (isInEncodeButton(relX, relY) && mouseButton == 0) {
            SmartPatternData data = tile.getPatternData();
            if (data != null && !data.hasConflicts()
                    && !tile.getInventory().getStackInSlot(0).isEmpty()
                    && tile.getInventory().getStackInSlot(1).isEmpty()) {
                AE2Enhanced.network.sendToServer(new PacketSmartPatternEncode(tile.getPos()));
            }
            return;
        }

        // 配方槽位点击 (禁用/启用切换)
        if (mouseButton == 0) {
            int clickedSlot = getRecipeSlotAt(relX, relY);
            if (clickedSlot >= 0) {
                int sortedIndex = tile.getScrollOffset() * 9 + clickedSlot;
                AE2Enhanced.network.sendToServer(new PacketSmartPatternToggle(tile.getPos(), sortedIndex));
                flashSlot = clickedSlot;
                flashTicks = FLASH_DURATION;
                return;
            }
        }

        // 滚动条点击/拖动开始
        if (mouseButton == 0 && isInScrollBar(relX, relY)) {
            SmartPatternData data = tile.getPatternData();
            if (data != null && data.getRecipeCount() > 45) {
                this.isScrolling = true;
                updateScrollFromMouse(relY);
            }
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.isScrolling && clickedMouseButton == 0) {
            int relY = mouseY - this.guiTop;
            updateScrollFromMouse(relY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.isScrolling) {
            this.isScrolling = false;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            SmartPatternData data = tile.getPatternData();
            if (data != null && data.getRecipeCount() > 45) {
                int maxOffset = Math.max(0, (data.getRecipeCount() - 1) / 9 - 4);
                int newOffset = tile.getScrollOffset() + (wheel > 0 ? -1 : 1);
                newOffset = Math.max(0, Math.min(maxOffset, newOffset));
                if (newOffset != tile.getScrollOffset()) {
                    AE2Enhanced.network.sendToServer(new PacketSmartPatternScroll(tile.getPos(), newOffset));
                }
            }
        }
    }

    /**
     * 根据鼠标 Y 坐标更新滚动偏移。
     */
    private void updateScrollFromMouse(int relY) {
        SmartPatternData data = tile.getPatternData();
        if (data == null) return;

        int maxOffset = Math.max(0, (data.getRecipeCount() - 1) / 9 - 4);
        if (maxOffset <= 0) return;

        int trackHeight = SCROLL_H - THUMB_HEIGHT;
        int relativeY = relY - SCROLL_Y - THUMB_HEIGHT / 2;
        int newOffset = relativeY * maxOffset / trackHeight;
        newOffset = Math.max(0, Math.min(maxOffset, newOffset));

        if (newOffset != tile.getScrollOffset()) {
            AE2Enhanced.network.sendToServer(new PacketSmartPatternScroll(tile.getPos(), newOffset));
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

    private boolean isInScrollBar(int x, int y) {
        return x >= SCROLL_X && x < SCROLL_X + SCROLL_W
            && y >= SCROLL_Y && y < SCROLL_Y + SCROLL_H;
    }
}

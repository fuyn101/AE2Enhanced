package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternEncode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternModify;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternScroll;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternToggle;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 智能样板接口的客户端 GUI v2。
 *
 * <p>核心变更：</p>
 * <ul>
 *   <li>主配方网格取消滚动条，改为翻页（上一页/下一页按钮）</li>
 *   <li>右侧新增 MiniGUI 面板：3×3 输入 + 大向下箭头 + 3×3 输出</li>
 *   <li>Shift+左键配方槽位 = 锁定/解锁该配方，MiniGUI 固定显示其输入输出</li>
 *   <li>操作按钮：只保留主产物、翻倍</li>
 *   <li>配方 hover tooltip 改为原版物品 tooltip</li>
 * </ul>
 */
public class GuiSmartPatternInterface extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("ae2enhanced", "textures/gui/smartpatterninterface.png");

    // 主面板配方网格坐标（v2）
    private static final int[] COL_X = {8, 26, 44, 62, 80, 98, 116, 134, 152};
    private static final int[] ROW_Y = {36, 54, 72, 90, 108};

    // 编码按钮
    private static final int BTN_ENCODE_X = 134;
    private static final int BTN_ENCODE_Y = 20;
    private static final int BTN_SIZE = 16;

    // 翻页按钮
    private static final int BTN_PREV_X = 116;
    private static final int BTN_PREV_Y = 126;
    private static final int BTN_NEXT_X = 152;
    private static final int BTN_NEXT_Y = 126;

    // 操作按钮（右侧面板底部）
    private static final int BTN_KEEP_PRIMARY_X = 178;
    private static final int BTN_KEEP_PRIMARY_Y = 146;
    private static final int BTN_DOUBLE_X = 214;
    private static final int BTN_DOUBLE_Y = 146;

    // X 标记纹理坐标
    private static final int MARK_X_U = 8;
    private static final int MARK_Y_V = 37;
    private static final int MARK_W = 16;
    private static final int MARK_H = 15;

    private final TileSmartPatternInterface tile;

    // 点击闪烁反馈
    private int flashSlot = -1;
    private int flashTicks = 0;
    private static final int FLASH_DURATION = 8;

    // 按钮悬停高亮
    private int hoverButton = 0; // 0=none, 1=prev, 2=next, 3=encode, 4=keepPrimary, 5=double

    public GuiSmartPatternInterface(InventoryPlayer inventoryPlayer, TileSmartPatternInterface tile) {
        super(new ContainerSmartPatternInterface(inventoryPlayer, tile));
        this.tile = tile;
        this.xSize = 242;  // 主面板 175 + 右侧面板 67（纹理坐标 175+67=242）
        this.ySize = 224;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);

        // 主面板背景 (0, 0, 176, 224)
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, 176, this.ySize);

        // 右侧面板 (175, 0, 67, 162)
        this.drawTexturedModalRect(this.guiLeft + 175, this.guiTop, 175, 0, 67, 162);

        // 大向下箭头 (196, 76, 16, 23)
        this.drawTexturedModalRect(this.guiLeft + 196, this.guiTop + 76, 196, 76, 16, 23);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 标题
        String title = I18n.format("gui.ae2enhanced.smart_pattern_interface.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        // 绑定状态
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

        // 页码（翻页按钮中间）
        drawPageIndicator();

        // 禁用标记 X
        drawDisabledMarkers();

        // 冲突标记（红色覆盖）
        drawConflictMarkers();

        // 锁定标记（蓝色边框）
        drawLockMarkers();

        // 点击闪烁反馈
        drawFlashEffect();

        // 按钮高亮与文本
        drawButtons(relX, relY);
    }

    /**
     * 绘制翻页按钮和页码指示器。
     */
    private void drawPageIndicator() {
        SmartPatternData data = tile.getPatternData();
        if (data == null || data.getRecipeCount() <= 45) return;

        int currentPage = tile.getScrollOffset();
        int maxPage = Math.max(0, (data.getRecipeCount() - 1) / 45);
        String pageText = (currentPage + 1) + " / " + (maxPage + 1);
        int textWidth = this.fontRenderer.getStringWidth(pageText);
        this.fontRenderer.drawString(pageText, BTN_PREV_X + (BTN_NEXT_X + BTN_SIZE - BTN_PREV_X - textWidth) / 2, BTN_PREV_Y + 5, 0x404040);
    }

    /**
     * 绘制按钮背景和高亮。
     */
    private void drawButtons(int relX, int relY) {
        // 上一页按钮
        int color = isInPrevButton(relX, relY) ? 0xFF777777 : 0xFF555555;
        drawRect(BTN_PREV_X, BTN_PREV_Y, BTN_PREV_X + BTN_SIZE, BTN_PREV_Y + BTN_SIZE, color);
        this.fontRenderer.drawString("<", BTN_PREV_X + 5, BTN_PREV_Y + 4, 0xFFFFFF);

        // 下一页按钮
        color = isInNextButton(relX, relY) ? 0xFF777777 : 0xFF555555;
        drawRect(BTN_NEXT_X, BTN_NEXT_Y, BTN_NEXT_X + BTN_SIZE, BTN_NEXT_Y + BTN_SIZE, color);
        this.fontRenderer.drawString(">", BTN_NEXT_X + 5, BTN_NEXT_Y + 4, 0xFFFFFF);

        // 操作按钮（仅当锁定配方时显示）
        if (tile.getLockedRecipeIndex() >= 0) {
            // 只保留主产物
            color = isInKeepPrimaryButton(relX, relY) ? 0xFF777777 : 0xFF555555;
            drawRect(BTN_KEEP_PRIMARY_X, BTN_KEEP_PRIMARY_Y, BTN_KEEP_PRIMARY_X + BTN_SIZE, BTN_KEEP_PRIMARY_Y + BTN_SIZE, color);
            this.fontRenderer.drawString("P", BTN_KEEP_PRIMARY_X + 5, BTN_KEEP_PRIMARY_Y + 4, 0xFFFFFF);

            // 翻倍
            color = isInDoubleButton(relX, relY) ? 0xFF777777 : 0xFF555555;
            drawRect(BTN_DOUBLE_X, BTN_DOUBLE_Y, BTN_DOUBLE_X + BTN_SIZE, BTN_DOUBLE_Y + BTN_SIZE, color);
            this.fontRenderer.drawString("×2", BTN_DOUBLE_X + 2, BTN_DOUBLE_Y + 4, 0xFFFFFF);
        }
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
     * 在禁用的配方槽位上绘制 X 标记。
     */
    private void drawDisabledMarkers() {
        SmartPatternData data = tile.getPatternData();
        if (data == null) return;

        int pageStart = tile.getScrollOffset() * 45;
        this.mc.getTextureManager().bindTexture(TEXTURE);
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int sortedIndex = pageStart + slotIndex;
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

        int pageStart = tile.getScrollOffset() * 45;
        int slotIndex = 0;
        for (int row = 0; row < ROW_Y.length; row++) {
            for (int col = 0; col < COL_X.length; col++) {
                int sortedIndex = pageStart + slotIndex;
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

    /**
     * 在锁定的配方槽位上绘制蓝色边框。
     */
    private void drawLockMarkers() {
        int lockedIndex = tile.getLockedRecipeIndex();
        if (lockedIndex < 0) return;

        int pageStart = tile.getScrollOffset() * 45;
        int slotIndex = lockedIndex - pageStart;
        if (slotIndex < 0 || slotIndex >= 45) return;

        int row = slotIndex / 9;
        int col = slotIndex % 9;
        if (row < ROW_Y.length && col < COL_X.length) {
            int x = COL_X[col];
            int y = ROW_Y[row];
            // 蓝色边框
            drawRect(x, y, x + 16, y + 1, 0xFF00AAFF);
            drawRect(x, y + 14, x + 16, y + 15, 0xFF00AAFF);
            drawRect(x, y, x + 1, y + 15, 0xFF00AAFF);
            drawRect(x + 15, y, x + 16, y + 15, 0xFF00AAFF);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (flashTicks > 0) flashTicks--;
        if (flashTicks <= 0) flashSlot = -1;

        // 滚轮翻页（在 drawScreen 中检测 Mouse.getDWheel）
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int relX = mouseX - this.guiLeft;
            int relY = mouseY - this.guiTop;
            if (isInRecipeGrid(relX, relY)) {
                SmartPatternData data = tile.getPatternData();
                if (data != null && data.getRecipeCount() > 45) {
                    int maxPage = Math.max(0, (data.getRecipeCount() - 1) / 45);
                    int newPage = tile.getScrollOffset() + (wheel > 0 ? -1 : 1);
                    newPage = Math.max(0, Math.min(maxPage, newPage));
                    if (newPage != tile.getScrollOffset()) {
                        AE2Enhanced.network.sendToServer(new PacketSmartPatternScroll(tile.getPos(), newPage));
                    }
                }
            }
        }

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

        // 翻页按钮 tooltip
        if (isInPrevButton(relX, relY)) {
            this.drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.smart_pattern_interface.prev_page")), mouseX, mouseY);
            return;
        }
        if (isInNextButton(relX, relY)) {
            this.drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.smart_pattern_interface.next_page")), mouseX, mouseY);
            return;
        }

        // 操作按钮 tooltip
        if (tile.getLockedRecipeIndex() >= 0) {
            if (isInKeepPrimaryButton(relX, relY)) {
                this.drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.smart_pattern_interface.keep_primary")), mouseX, mouseY);
                return;
            }
            if (isInDoubleButton(relX, relY)) {
                this.drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.smart_pattern_interface.double_amounts")), mouseX, mouseY);
                return;
            }
        }

        // 配方槽位状态 tooltip（仅当 slot 为空时，否则让 super 显示物品 tooltip）
        int slot = getRecipeSlotAt(relX, relY);
        if (slot >= 0) {
            Slot recipeSlot = this.inventorySlots.getSlot(slot);
            if (!recipeSlot.getHasStack()) {
                SmartPatternData data = tile.getPatternData();
                if (data != null) {
                    int sortedIndex = tile.getScrollOffset() * 45 + slot;
                    if (sortedIndex < data.getRecipeCount()) {
                        List<String> lines = new ArrayList<>();
                        if (data.isConflict(sortedIndex)) {
                            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.conflict_marker"));
                        }
                        if (data.isDisabled(sortedIndex)) {
                            lines.add(I18n.format("gui.ae2enhanced.smart_pattern_interface.disabled_marker"));
                        }
                        if (!lines.isEmpty()) {
                            this.drawHoveringText(lines, mouseX, mouseY);
                            return;
                        }
                    }
                }
            }
        }

        // 默认：super 处理物品 tooltip（包括配方槽位和 MiniGUI slot）
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

        // 翻页按钮
        if (mouseButton == 0) {
            if (isInPrevButton(relX, relY)) {
                int newPage = tile.getScrollOffset() - 1;
                if (newPage >= 0) {
                    AE2Enhanced.network.sendToServer(new PacketSmartPatternScroll(tile.getPos(), newPage));
                }
                return;
            }
            if (isInNextButton(relX, relY)) {
                SmartPatternData data = tile.getPatternData();
                if (data != null) {
                    int maxPage = Math.max(0, (data.getRecipeCount() - 1) / 45);
                    int newPage = tile.getScrollOffset() + 1;
                    if (newPage <= maxPage) {
                        AE2Enhanced.network.sendToServer(new PacketSmartPatternScroll(tile.getPos(), newPage));
                    }
                }
                return;
            }
        }

        // 操作按钮
        if (mouseButton == 0 && tile.getLockedRecipeIndex() >= 0) {
            if (isInKeepPrimaryButton(relX, relY)) {
                AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), "keepPrimary"));
                return;
            }
            if (isInDoubleButton(relX, relY)) {
                AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), "doubleAmounts"));
                return;
            }
        }

        // 配方槽位点击
        if (mouseButton == 0) {
            int clickedSlot = getRecipeSlotAt(relX, relY);
            if (clickedSlot >= 0) {
                int sortedIndex = tile.getScrollOffset() * 45 + clickedSlot;
                boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                if (shift) {
                    // Shift+左键 = 锁定/解锁
                    if (tile.isRecipeLocked(sortedIndex)) {
                        AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), "unlock"));
                    } else {
                        AE2Enhanced.network.sendToServer(new PacketSmartPatternModify(tile.getPos(), "lock", sortedIndex));
                    }
                } else {
                    // 左键 = 切换禁用
                    AE2Enhanced.network.sendToServer(new PacketSmartPatternToggle(tile.getPos(), sortedIndex));
                }
                flashSlot = clickedSlot;
                flashTicks = FLASH_DURATION;
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    // ---- 碰撞检测 ----

    private boolean isInEncodeButton(int x, int y) {
        return x >= BTN_ENCODE_X && x < BTN_ENCODE_X + BTN_SIZE
            && y >= BTN_ENCODE_Y && y < BTN_ENCODE_Y + BTN_SIZE;
    }

    private boolean isInPrevButton(int x, int y) {
        return x >= BTN_PREV_X && x < BTN_PREV_X + BTN_SIZE
            && y >= BTN_PREV_Y && y < BTN_PREV_Y + BTN_SIZE;
    }

    private boolean isInNextButton(int x, int y) {
        return x >= BTN_NEXT_X && x < BTN_NEXT_X + BTN_SIZE
            && y >= BTN_NEXT_Y && y < BTN_NEXT_Y + BTN_SIZE;
    }

    private boolean isInKeepPrimaryButton(int x, int y) {
        return x >= BTN_KEEP_PRIMARY_X && x < BTN_KEEP_PRIMARY_X + BTN_SIZE
            && y >= BTN_KEEP_PRIMARY_Y && y < BTN_KEEP_PRIMARY_Y + BTN_SIZE;
    }

    private boolean isInDoubleButton(int x, int y) {
        return x >= BTN_DOUBLE_X && x < BTN_DOUBLE_X + BTN_SIZE
            && y >= BTN_DOUBLE_Y && y < BTN_DOUBLE_Y + BTN_SIZE;
    }

    private boolean isInRecipeGrid(int x, int y) {
        return x >= COL_X[0] && x < COL_X[8] + 16
            && y >= ROW_Y[0] && y < ROW_Y[4] + 15;
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

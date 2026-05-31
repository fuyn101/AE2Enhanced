package com.github.aeddddd.ae2enhanced.client.rts.gui;

import com.github.aeddddd.ae2enhanced.network.packet.PacketRTSMEStorageSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * RTS 模式底部面板：ME 存储 / 收藏 / 历史。
 *
 * <p>特性：</p>
 * <ul>
 *   <li>左侧 50%：ME 网络存储物品多行网格（来自服务端同步，3 行）</li>
 *   <li>中间 25%：收藏栏（客户端本地）</li>
 *   <li>右侧 25%：历史记录（客户端本地，自动记录使用过的物品）</li>
 *   <li>支持鼠标点击选中 ME 物品和排序切换</li>
 *   <li>物品数量缩写：K / M / G / T / P（缩小字体显示）</li>
 * </ul>
 */
public class RTSBottomPanel {

    // 面板高度（像素）：标题 16 + 3 行 × 18 + 底部边距 = 约 78，取 84
    public static final int PANEL_HEIGHT = 84;
    public static final int SLOT_SIZE = 18;
    public static final int ICON_SIZE = 16;
    public static final int MARGIN_X = 6;
    public static final int MARGIN_Y = 16;

    private static ItemStack currentPlacementItem = ItemStack.EMPTY;
    private static int selectedSlot = -1; // ME 存储区域的选中槽位，-1 表示未选中

    // 收藏与历史（客户端本地）
    private static final List<ItemStack> favorites = new ArrayList<>();
    private static final LinkedList<ItemStack> history = new LinkedList<>();
    private static final int MAX_HISTORY = 18;

    public static ItemStack getCurrentPlacementItem() {
        return currentPlacementItem;
    }

    public static void setCurrentPlacementItem(ItemStack stack) {
        currentPlacementItem = stack != null ? stack : ItemStack.EMPTY;
    }

    public static int getSelectedSlot() {
        return selectedSlot;
    }

    /**
     * 通过索引选择 ME 存储区域的物品。
     * @param slot 0-based slot index（按当前排序模式后的列表索引）
     */
    public static void selectSlot(int slot) {
        List<PacketRTSMEStorageSync.Entry> entries = RTSMEStorageCache.getEntries();
        if (slot >= 0 && slot < entries.size()) {
            selectedSlot = slot;
            currentPlacementItem = entries.get(slot).stack.copy();
        } else {
            selectedSlot = -1;
            currentPlacementItem = ItemStack.EMPTY;
        }
    }

    /**
     * 将物品加入历史记录（去重，移到最前，限制数量）。
     */
    public static void addToHistory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        history.removeIf(h -> ItemStack.areItemsEqual(h, copy) && ItemStack.areItemStackTagsEqual(h, copy));
        history.addFirst(copy);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    /**
     * 判断鼠标是否在底部面板区域内。
     */
    public static boolean isMouseOverPanel(int mouseX, int mouseY) {
        if (!com.github.aeddddd.ae2enhanced.client.rts.RTSCamera.isActive()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int screenH = sr.getScaledHeight();
        int panelY = screenH - PANEL_HEIGHT;
        return mouseY >= panelY && mouseY < screenH;
    }

    /**
     * 处理鼠标点击事件。调用方应已通过 {@link #isMouseOverPanel} 确认鼠标在面板内。
     * 返回 true 表示点击被面板内的交互元素消费。
     */
    public static boolean handleMouseClick(int mouseX, int mouseY, int button) {
        if (!com.github.aeddddd.ae2enhanced.client.rts.RTSCamera.isActive()) return false;
        if (button != 0) return false; // 只处理左键点击

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();
        int panelY = screenH - PANEL_HEIGHT;
        int meWidth = screenW / 2;

        // 检测排序按钮点击
        String title = "\u00a77ME \u00a7f\u5b58\u50a8";
        String sortLabel = " \u00a7e[" + RTSMEStorageCache.getSortMode().label
                + (RTSMEStorageCache.getSortMode().descending ? "\u2193" : "\u2191") + "]";
        int titleWidth = mc.fontRenderer.getStringWidth(title);
        int sortLabelWidth = mc.fontRenderer.getStringWidth(sortLabel);
        int sortButtonX = MARGIN_X + titleWidth;
        int sortButtonY = panelY + 4;
        if (mouseX >= sortButtonX && mouseX < sortButtonX + sortLabelWidth &&
            mouseY >= sortButtonY && mouseY < sortButtonY + mc.fontRenderer.FONT_HEIGHT) {
            RTSMEStorageCache.toggleSortMode();
            return true;
        }

        // 检测 ME 网格点击
        int gridX = MARGIN_X;
        int gridY = panelY + MARGIN_Y;
        int gridW = meWidth - MARGIN_X * 2;
        int slotsPerRow = Math.max(1, gridW / SLOT_SIZE);
        int relX = mouseX - gridX;
        int relY = mouseY - gridY;
        if (relX >= 0 && relY >= 0) {
            int col = relX / SLOT_SIZE;
            int row = relY / SLOT_SIZE;
            if (col < slotsPerRow) {
                int slot = row * slotsPerRow + col;
                List<PacketRTSMEStorageSync.Entry> entries = RTSMEStorageCache.getEntries();
                if (slot >= 0 && slot < entries.size()) {
                    selectSlot(slot);
                    return true;
                }
            }
        }

        return false; // 在面板内但没有点中交互元素
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!com.github.aeddddd.ae2enhanced.client.rts.RTSCamera.isActive()) {
            return;
        }
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = event.getResolution();
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        int panelY = screenH - PANEL_HEIGHT;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 500); // 提高 GUI 图层，避免被其他 overlay 遮挡

        // 绘制背景
        drawBackground(screenW, panelY);

        int meWidth = screenW / 2;
        int favWidth = screenW / 4;
        int histWidth = screenW - meWidth - favWidth;

        // 绘制标题
        String meTitle = "\u00a77ME \u00a7f\u5b58\u50a8";
        String sortLabel = " \u00a7e[" + RTSMEStorageCache.getSortMode().label
                + (RTSMEStorageCache.getSortMode().descending ? "\u2193" : "\u2191") + "]";
        mc.fontRenderer.drawStringWithShadow(meTitle + sortLabel, MARGIN_X, panelY + 4, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow("\u00a77\u6536\u85cf", meWidth + MARGIN_X, panelY + 4, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow("\u00a77\u5386\u53f2", meWidth + favWidth + MARGIN_X, panelY + 4, 0xFFFFFFFF);

        // 绘制分隔线
        net.minecraft.client.gui.Gui.drawRect(meWidth, panelY + 2, meWidth + 1, screenH - 2, 0x40FFFFFF);
        net.minecraft.client.gui.Gui.drawRect(meWidth + favWidth, panelY + 2, meWidth + favWidth + 1, screenH - 2, 0x40FFFFFF);

        // 渲染 ME 存储物品
        List<PacketRTSMEStorageSync.Entry> meEntries = RTSMEStorageCache.getEntries();
        boolean connected = RTSMEStorageCache.isNetworkConnected();
        renderMEGrid(mc, meEntries, connected, MARGIN_X, panelY + MARGIN_Y, meWidth - MARGIN_X * 2, selectedSlot);

        // 渲染收藏
        renderItemGrid(mc, favorites, meWidth + MARGIN_X, panelY + MARGIN_Y, favWidth - MARGIN_X * 2, 1);

        // 渲染历史
        renderItemGrid(mc, history, meWidth + favWidth + MARGIN_X, panelY + MARGIN_Y, histWidth - MARGIN_X * 2, 1);

        GlStateManager.popMatrix();
    }

    private void drawBackground(int screenW, int panelY) {
        net.minecraft.client.gui.Gui.drawRect(0, panelY, screenW, panelY + PANEL_HEIGHT, 0xCC000000);
        net.minecraft.client.gui.Gui.drawRect(0, panelY, screenW, panelY + 1, 0x80FFFFFF);
    }

    private void renderMEGrid(Minecraft mc, List<PacketRTSMEStorageSync.Entry> entries, boolean connected,
                              int x, int y, int maxWidth, int selectedSlot) {
        if (!connected) {
            String text = "\u00a7c\u672a\u8fde\u63a5\u7f51\u7edc";
            mc.fontRenderer.drawStringWithShadow(text, x, y + 2, 0xFFFFFFFF);
            return;
        }
        if (entries.isEmpty()) {
            String text = "\u00a78\u7f51\u7edc\u4e2d\u65e0\u7269\u54c1";
            mc.fontRenderer.drawStringWithShadow(text, x, y + 2, 0xFFFFFFFF);
            return;
        }

        int slotsPerRow = Math.max(1, maxWidth / SLOT_SIZE);
        int maxRows = Math.max(1, (PANEL_HEIGHT - MARGIN_Y - 4) / SLOT_SIZE);
        int maxSlots = slotsPerRow * maxRows;
        int visibleCount = Math.min(entries.size(), maxSlots);

        RenderItem renderItem = mc.getRenderItem();

        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderHelper.enableGUIStandardItemLighting();

        for (int i = 0; i < visibleCount; i++) {
            PacketRTSMEStorageSync.Entry entry = entries.get(i);
            int col = i % slotsPerRow;
            int row = i / slotsPerRow;
            int slotX = x + col * SLOT_SIZE;
            int slotY = y + row * SLOT_SIZE;

            int bgColor = (i == selectedSlot) ? 0x80FFFF00 : 0x40FFFFFF;
            net.minecraft.client.gui.Gui.drawRect(slotX, slotY, slotX + ICON_SIZE, slotY + ICON_SIZE, bgColor);

            ItemStack stack = entry.stack;
            if (!stack.isEmpty()) {
                renderItem.renderItemAndEffectIntoGUI(stack, slotX, slotY);

                // 自定义缩小数量文字，避免遮挡图标
                String countStr = formatCount(entry.count);
                if (!countStr.equals("1")) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(slotX, slotY, 300);
                    float scale = 0.6f;
                    GlStateManager.scale(scale, scale, 1.0f);
                    int textX = (int) ((ICON_SIZE - 2) / scale) - mc.fontRenderer.getStringWidth(countStr);
                    int textY = (int) ((ICON_SIZE - 2) / scale) - mc.fontRenderer.FONT_HEIGHT + 1;
                    mc.fontRenderer.drawStringWithShadow(countStr, textX, textY, 0xFFFFFFFF);
                    GlStateManager.popMatrix();
                }
            }
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void renderItemGrid(Minecraft mc, List<ItemStack> items, int x, int y, int maxWidth, int maxRows) {
        if (items.isEmpty()) {
            return;
        }

        int slotsPerRow = Math.max(1, maxWidth / SLOT_SIZE);
        int maxSlots = slotsPerRow * Math.max(1, maxRows);
        int visibleCount = Math.min(items.size(), maxSlots);

        RenderItem renderItem = mc.getRenderItem();

        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderHelper.enableGUIStandardItemLighting();

        for (int i = 0; i < visibleCount; i++) {
            ItemStack stack = items.get(i);
            int col = i % slotsPerRow;
            int row = i / slotsPerRow;
            int slotX = x + col * SLOT_SIZE;
            int slotY = y + row * SLOT_SIZE;

            net.minecraft.client.gui.Gui.drawRect(slotX, slotY, slotX + ICON_SIZE, slotY + ICON_SIZE, 0x40FFFFFF);

            if (!stack.isEmpty()) {
                renderItem.renderItemAndEffectIntoGUI(stack, slotX, slotY);
                renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, stack, slotX, slotY, null);
            }
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000_000_000_000L) {
            return String.format("%.1fP", count / 1_000_000_000_000_000.0);
        }
        if (count >= 1_000_000_000_000L) {
            return String.format("%.1fT", count / 1_000_000_000_000.0);
        }
        if (count >= 1_000_000_000L) {
            return String.format("%.1fG", count / 1_000_000_000.0);
        }
        if (count >= 1_000_000L) {
            return String.format("%.1fM", count / 1_000_000.0);
        }
        if (count >= 1000L) {
            return String.format("%.1fK", count / 1000.0);
        }
        return String.valueOf(count);
    }
}

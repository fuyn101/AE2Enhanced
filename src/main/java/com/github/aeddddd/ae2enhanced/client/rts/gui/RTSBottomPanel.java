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
 * <p>当前实现为简化版：</p>
 * <ul>
 *   <li>左侧 50%：ME 网络存储物品网格（来自服务端同步）</li>
 *   <li>中间 25%：收藏栏（客户端本地，占位）</li>
 *   <li>右侧 25%：历史记录（客户端本地，占位）</li>
 * </ul>
 */
public class RTSBottomPanel {

    // 面板高度（像素）
    public static final int PANEL_HEIGHT = 56;
    // 物品图标大小
    public static final int SLOT_SIZE = 18;
    // 物品实际渲染大小
    public static final int ICON_SIZE = 16;
    // 左/右边距
    public static final int MARGIN_X = 6;
    // 顶部边距（面板内部）
    public static final int MARGIN_Y = 18;

    // 当前选中的放置物品
    private static ItemStack currentPlacementItem = ItemStack.EMPTY;
    private static int selectedSlot = -1; // ME 存储区域的选中槽位，-1 表示未选中

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
     * 通过数字键选择 ME 存储区域的前 N 个物品。
     * @param slot 0-based slot index
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

    // 收藏与历史（客户端本地）
    private static final List<ItemStack> favorites = new ArrayList<>();
    private static final LinkedList<ItemStack> history = new LinkedList<>();

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

        // 绘制背景
        drawBackground(screenW, panelY);

        // 计算三个区域宽度
        int meWidth = screenW / 2;
        int favWidth = screenW / 4;
        int histWidth = screenW - meWidth - favWidth;

        // 绘制标题
        mc.fontRenderer.drawStringWithShadow("\u00a77ME \u00a7f\u5b58\u50a8", MARGIN_X, panelY + 4, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow("\u00a77\u6536\u85cf", meWidth + MARGIN_X, panelY + 4, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow("\u00a77\u5386\u53f2", meWidth + favWidth + MARGIN_X, panelY + 4, 0xFFFFFFFF);

        // 绘制分隔线
        net.minecraft.client.gui.Gui.drawRect(meWidth, panelY + 2, meWidth + 1, screenH - 2, 0x40FFFFFF);
        net.minecraft.client.gui.Gui.drawRect(meWidth + favWidth, panelY + 2, meWidth + favWidth + 1, screenH - 2, 0x40FFFFFF);

        // 渲染 ME 存储物品
        List<PacketRTSMEStorageSync.Entry> meEntries = RTSMEStorageCache.getEntries();
        renderMEGrid(mc, meEntries, MARGIN_X, panelY + MARGIN_Y, meWidth - MARGIN_X * 2, selectedSlot);

        // 渲染收藏（占位）
        renderItemRow(mc, favorites, meWidth + MARGIN_X, panelY + MARGIN_Y, favWidth - MARGIN_X * 2);

        // 渲染历史（占位）
        renderItemRow(mc, history, meWidth + favWidth + MARGIN_X, panelY + MARGIN_Y, histWidth - MARGIN_X * 2);
    }

    private void drawBackground(int screenW, int panelY) {
        // 半透明黑色背景
        net.minecraft.client.gui.Gui.drawRect(0, panelY, screenW, panelY + PANEL_HEIGHT, 0xCC000000);
        // 顶部细线
        net.minecraft.client.gui.Gui.drawRect(0, panelY, screenW, panelY + 1, 0x80FFFFFF);
    }

    private void renderMEGrid(Minecraft mc, List<PacketRTSMEStorageSync.Entry> entries, int x, int y, int maxWidth, int selectedSlot) {
        if (entries.isEmpty()) {
            String text = "\u00a78\u672a\u8fde\u63a5";
            mc.fontRenderer.drawStringWithShadow(text, x, y + 2, 0xFFFFFFFF);
            return;
        }

        int slotsPerRow = Math.max(1, maxWidth / SLOT_SIZE);
        int visibleCount = Math.min(entries.size(), slotsPerRow);

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
            int slotX = x + i * SLOT_SIZE;
            int slotY = y;

            // 选中槽位高亮
            int bgColor = (i == selectedSlot) ? 0x80FFFF00 : 0x40FFFFFF;
            net.minecraft.client.gui.Gui.drawRect(slotX, slotY, slotX + ICON_SIZE, slotY + ICON_SIZE, bgColor);

            ItemStack stack = entry.stack;
            if (!stack.isEmpty()) {
                renderItem.renderItemAndEffectIntoGUI(stack, slotX, slotY);
                String countStr = formatCount(entry.count);
                renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, stack, slotX, slotY, countStr);
            }
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void renderItemRow(Minecraft mc, List<ItemStack> items, int x, int y, int maxWidth) {
        if (items.isEmpty()) {
            return;
        }

        int slotsPerRow = Math.max(1, maxWidth / SLOT_SIZE);
        int visibleCount = Math.min(items.size(), slotsPerRow);

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
            int slotX = x + i * SLOT_SIZE;
            int slotY = y;

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
        if (count >= 1_000_000_000L) {
            return String.format("%.1fB", count / 1_000_000_000.0);
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

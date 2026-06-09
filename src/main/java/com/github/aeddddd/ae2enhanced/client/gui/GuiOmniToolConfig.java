package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 先进ME工具配置GUI —— 严格按照 docs/design/omni_gui_uv_document.md 布局实现.
 *
 * 整体布局（195×221）:
 *   y=0~24   : 标题栏
 *   y=25~100 : 左按钮(75×17) + 竖条(33×75) + 右按钮(75×17)
 *   y=102~119: 中间长条1 (188×17) — 开关条
 *   y=122~139: 中间长条2 (188×17) — 滑块条
 *   y=160~178: 底部按钮区 — 左大按钮(75×17) + 中高亮按钮(75×17) + 右小按钮(12×18)
 *   y=182~199: 大高亮条 (188×17)
 *   y=202~219: 蓝色条 (188×17)
 */
public class GuiOmniToolConfig extends GuiScreen {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui.png");

    // GUI 外框
    private static final int GUI_W = 195;
    private static final int GUI_H = 221;

    // ==================== 纹理 UV 坐标（严格来自文档）====================

    // --- 顶部左按钮 ---
    private static final int BTN_LEFT_OUT_U = 4,  BTN_LEFT_OUT_V = 25;
    private static final int BTN_LEFT_OUT_W = 75, BTN_LEFT_OUT_H = 17;
    private static final int BTN_LEFT_IN_U  = 6,  BTN_LEFT_IN_V  = 27;
    private static final int BTN_LEFT_IN_W  = 71, BTN_LEFT_IN_H  = 11;

    // --- 顶部右按钮 ---
    private static final int BTN_RIGHT_OUT_U = 116, BTN_RIGHT_OUT_V = 25;
    private static final int BTN_RIGHT_OUT_W = 75,  BTN_RIGHT_OUT_H = 17;
    private static final int BTN_RIGHT_IN_U  = 118, BTN_RIGHT_IN_V  = 27;
    private static final int BTN_RIGHT_IN_W  = 71,  BTN_RIGHT_IN_H  = 11;

    // --- 中间竖条 ---
    private static final int BAR_VERT_OUT_U = 81,  BAR_VERT_OUT_V = 25;
    private static final int BAR_VERT_OUT_W = 33,  BAR_VERT_OUT_H = 75;
    private static final int BAR_VERT_IN_U  = 83,  BAR_VERT_IN_V  = 27;
    private static final int BAR_VERT_IN_W  = 29,  BAR_VERT_IN_H  = 71;

    // --- 中间长条1（开关条背景）---
    private static final int BAR1_OUT_U = 4,  BAR1_OUT_V = 102;
    private static final int BAR1_OUT_W = 188, BAR1_OUT_H = 17;
    private static final int BAR1_IN_U  = 6,  BAR1_IN_V  = 104;
    private static final int BAR1_IN_W  = 184, BAR1_IN_H  = 11;

    // --- 中间长条2（滑块条背景）---
    private static final int BAR2_OUT_U = 4,  BAR2_OUT_V = 122;
    private static final int BAR2_OUT_W = 187, BAR2_OUT_H = 16; // (191-4)=187, (138-122)=16
    private static final int BAR2_IN_U  = 5,  BAR2_IN_V  = 123;
    private static final int BAR2_IN_W  = 185, BAR2_IN_H  = 14; // (190-5)=185, (137-123)=14

    // --- 底部小按钮 ---
    private static final int SMOL_NORM_U = 0,   SMOL_NORM_V = 221;
    private static final int SMOL_HL_U   = 75,  SMOL_HL_V   = 221;
    private static final int SMOL_W      = 12,  SMOL_H      = 17; // 与长条/按钮同高
    private static final int KNOB_U      = 150, KNOB_V      = 221;
    private static final int KNOB_W      = 12,  KNOB_H      = 17;

    // --- 底部高亮大按钮 ---
    private static final int BIG_HL_U = 0,  BIG_HL_V = 238;
    private static final int BIG_HL_W = 188, BIG_HL_H = 17; // 纹理y=238~255

    // ==================== 屏幕坐标（严格按文档布局图示）====================

    // 外框左上角
    private int guiLeft, guiTop;

    // 顶部区域（y相对于 guiTop）
    private static final int TOP_Y = 25;
    // 左按钮 x=4（相对于 guiLeft）
    private static final int TOP_LEFT_X = 4;
    // 竖条 x=81
    private static final int TOP_VERT_X = 81;
    // 右按钮 x=116
    private static final int TOP_RIGHT_X = 116;

    // 中间长条区域
    private static final int BAR1_Y = 102;
    private static final int BAR2_Y = 122;

    // 底部按钮区（估算，保持与上方元素的合理间距）
    private static final int BOTTOM_Y = 160;
    // 底部左按钮 x=4
    private static final int BOT_LEFT_X = 4;
    // 底部中按钮 x=83（间距4px: 4+75+4=83）
    private static final int BOT_MID_X = 83;
    // 底部右小按钮 x=166（间距8px: 83+75+8=166）
    private static final int BOT_SMALL_X = 166;

    // 大高亮条
    private static final int BIG_HL_Y = 182;
    // 蓝色条（复用大按钮UV或纯色矩形，文档未给出独立蓝色条UV）
    private static final int BLUE_BAR_Y = 202;

    // ==================== 数据 ====================

    private final EntityPlayer player;
    private ItemStack toolStack = ItemStack.EMPTY;

    // 本地配置缓存
    private int currentMode;
    private int currentDropMode;
    private boolean currentSilk;
    private int currentFortune;
    private double currentBlinkDist;
    private int currentCooldown;

    // 滑条
    private int draggingSlider = -1;
    private final int[] sliderValues = new int[3];
    private final int[] sliderMin = {0, 1, 0};
    private final int[] sliderMax = {20, 256, 100};

    // 悬停/点击状态（用于高亮反馈）
    private boolean hoverTopLeft, hoverTopRight;
    private boolean hoverBotLeft, hoverBotMid, hoverBotSmall;
    private boolean hoverBar1, hoverBigHl, hoverBlueBar;

    public GuiOmniToolConfig(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public void initGui() {
        super.initGui();
        guiLeft = (width - GUI_W) / 2;
        guiTop = (height - GUI_H) / 2;
        refreshStack();
    }

    private void refreshStack() {
        this.toolStack = ItemStack.EMPTY;
        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                this.toolStack = stack;
                break;
            }
        }
        if (toolStack.isEmpty()) {
            mc.displayGuiScreen(null);
            return;
        }
        currentMode = ItemAdvancedMEOmniTool.getMode(toolStack);
        currentDropMode = ItemAdvancedMEOmniTool.getDropMode(toolStack);
        currentSilk = ItemAdvancedMEOmniTool.isSilkTouchEnabled(toolStack);
        currentFortune = ItemAdvancedMEOmniTool.getFortuneLevel(toolStack);
        currentBlinkDist = ItemAdvancedMEOmniTool.getBlinkDistance(toolStack);
        currentCooldown = ItemAdvancedMEOmniTool.getBreakCooldown(toolStack);

        sliderValues[0] = currentFortune;
        sliderValues[1] = (int) currentBlinkDist;
        sliderValues[2] = currentCooldown;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        guiLeft = (width - GUI_W) / 2;
        guiTop = (height - GUI_H) / 2;

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1f, 1f, 1f, 1f);

        // ---- 1. 顶部区域：左按钮 + 竖条 + 右按钮 ----
        // 左按钮外框
        drawTexturedModalRect(guiLeft + TOP_LEFT_X, guiTop + TOP_Y,
                BTN_LEFT_OUT_U, BTN_LEFT_OUT_V, BTN_LEFT_OUT_W, BTN_LEFT_OUT_H);
        // 左按钮内部
        drawTexturedModalRect(guiLeft + TOP_LEFT_X + 2, guiTop + TOP_Y + 2,
                BTN_LEFT_IN_U, BTN_LEFT_IN_V, BTN_LEFT_IN_W, BTN_LEFT_IN_H);

        // 竖条外框
        drawTexturedModalRect(guiLeft + TOP_VERT_X, guiTop + TOP_Y,
                BAR_VERT_OUT_U, BAR_VERT_OUT_V, BAR_VERT_OUT_W, BAR_VERT_OUT_H);
        // 竖条内部
        drawTexturedModalRect(guiLeft + TOP_VERT_X + 2, guiTop + TOP_Y + 2,
                BAR_VERT_IN_U, BAR_VERT_IN_V, BAR_VERT_IN_W, BAR_VERT_IN_H);

        // 右按钮外框
        drawTexturedModalRect(guiLeft + TOP_RIGHT_X, guiTop + TOP_Y,
                BTN_RIGHT_OUT_U, BTN_RIGHT_OUT_V, BTN_RIGHT_OUT_W, BTN_RIGHT_OUT_H);
        // 右按钮内部
        drawTexturedModalRect(guiLeft + TOP_RIGHT_X + 2, guiTop + TOP_Y + 2,
                BTN_RIGHT_IN_U, BTN_RIGHT_IN_V, BTN_RIGHT_IN_W, BTN_RIGHT_IN_H);

        // ---- 2. 中间长条1（开关条）----
        drawTexturedModalRect(guiLeft + 4, guiTop + BAR1_Y,
                BAR1_OUT_U, BAR1_OUT_V, BAR1_OUT_W, BAR1_OUT_H);
        drawTexturedModalRect(guiLeft + 6, guiTop + BAR1_Y + 2,
                BAR1_IN_U, BAR1_IN_V, BAR1_IN_W, BAR1_IN_H);

        // ---- 3. 中间长条2（滑块条）----
        drawTexturedModalRect(guiLeft + 4, guiTop + BAR2_Y,
                BAR2_OUT_U, BAR2_OUT_V, BAR2_OUT_W, BAR2_OUT_H);
        drawTexturedModalRect(guiLeft + 5, guiTop + BAR2_Y + 1,
                BAR2_IN_U, BAR2_IN_V, BAR2_IN_W, BAR2_IN_H);

        // ---- 4. 底部按钮区 ----
        // 底部左按钮（复用顶部左按钮UV）
        int botLeftU = hoverBotLeft ? BTN_LEFT_IN_U : BTN_LEFT_OUT_U;
        int botLeftV = hoverBotLeft ? BTN_LEFT_IN_V : BTN_LEFT_OUT_V;
        drawTexturedModalRect(guiLeft + BOT_LEFT_X, guiTop + BOTTOM_Y,
                botLeftU, botLeftV, BTN_LEFT_OUT_W, BTN_LEFT_OUT_H);

        // 底部中高亮按钮（复用顶部右按钮UV，或自定义高亮）
        int botMidU = hoverBotMid ? BTN_RIGHT_IN_U : BTN_RIGHT_OUT_U;
        int botMidV = hoverBotMid ? BTN_RIGHT_IN_V : BTN_RIGHT_OUT_V;
        drawTexturedModalRect(guiLeft + BOT_MID_X, guiTop + BOTTOM_Y,
                botMidU, botMidV, BTN_RIGHT_OUT_W, BTN_RIGHT_OUT_H);

        // 底部右小按钮
        int smallU = hoverBotSmall ? SMOL_HL_U : SMOL_NORM_U;
        int smallV = hoverBotSmall ? SMOL_HL_V : SMOL_NORM_V;
        drawTexturedModalRect(guiLeft + BOT_SMALL_X, guiTop + BOTTOM_Y,
                smallU, smallV, SMOL_W, SMOL_H);

        // ---- 5. 大高亮条 ----
        drawTexturedModalRect(guiLeft + 4, guiTop + BIG_HL_Y,
                BIG_HL_U, BIG_HL_V, BIG_HL_W, BIG_HL_H);

        // ---- 6. 蓝色条（文档未给出独立UV，使用纯色或复用其他蓝色纹理）----
        // 使用文档颜色对照表中的"蓝色按钮"颜色 (112,140,186)
        drawRect(guiLeft + 4, guiTop + BLUE_BAR_Y,
                guiLeft + 4 + 188, guiTop + BLUE_BAR_Y + 17, 0xFF708CBA);

        // ---- 文字标签 ----
        String title = I18n.format("gui.ae2enhanced.omni_tool_config.title");
        drawCenteredString(fontRenderer, title, guiLeft + GUI_W / 2, guiTop + 6, 0x333333);

        // 长条1文字：丝绸触摸
        String bar1Label = I18n.format("gui.ae2enhanced.omni_tool_config.silk_touch");
        fontRenderer.drawString(bar1Label, guiLeft + 12, guiTop + BAR1_Y + 4, 0x333333);
        // 长条1开关状态（用小按钮纹理在右侧表示）
        int swX = guiLeft + 4 + BAR1_OUT_W - 16;
        int swY = guiTop + BAR1_Y;
        drawTexturedModalRect(swX, swY,
                currentSilk ? SMOL_HL_U : SMOL_NORM_U,
                currentSilk ? SMOL_HL_V : SMOL_NORM_V,
                SMOL_W, SMOL_H);

        // 长条2文字 + 滑块 + 数值（时运）
        drawSlider(guiTop + BAR2_Y, "gui.ae2enhanced.omni_tool_config.fortune", 0);

        // ---- 更新悬停状态 ----
        hoverTopLeft  = isInside(mouseX, mouseY, guiLeft + TOP_LEFT_X, guiTop + TOP_Y, BTN_LEFT_OUT_W, BTN_LEFT_OUT_H);
        hoverTopRight = isInside(mouseX, mouseY, guiLeft + TOP_RIGHT_X, guiTop + TOP_Y, BTN_RIGHT_OUT_W, BTN_RIGHT_OUT_H);
        hoverBotLeft  = isInside(mouseX, mouseY, guiLeft + BOT_LEFT_X, guiTop + BOTTOM_Y, BTN_LEFT_OUT_W, BTN_LEFT_OUT_H);
        hoverBotMid   = isInside(mouseX, mouseY, guiLeft + BOT_MID_X, guiTop + BOTTOM_Y, BTN_RIGHT_OUT_W, BTN_RIGHT_OUT_H);
        hoverBotSmall = isInside(mouseX, mouseY, guiLeft + BOT_SMALL_X, guiTop + BOTTOM_Y, SMOL_W, SMOL_H);
        hoverBar1     = isInside(mouseX, mouseY, guiLeft + 4, guiTop + BAR1_Y, BAR1_OUT_W, BAR1_OUT_H);
        hoverBigHl    = isInside(mouseX, mouseY, guiLeft + 4, guiTop + BIG_HL_Y, BIG_HL_W, BIG_HL_H);
        hoverBlueBar  = isInside(mouseX, mouseY, guiLeft + 4, guiTop + BLUE_BAR_Y, 188, 17);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSlider(int y, String labelKey, int idx) {
        String label = I18n.format(labelKey);
        fontRenderer.drawString(label, guiLeft + 12, y + 4, 0x333333);

        // 滑块轨道在长条2内部右侧
        int trackX = guiLeft + 70;
        int trackW = 110;
        float ratio = (sliderValues[idx] - sliderMin[idx]) / (float) (sliderMax[idx] - sliderMin[idx]);
        int knobX = trackX + Math.round(ratio * (trackW - KNOB_W));

        // 绘制滑块
        drawTexturedModalRect(knobX, y, KNOB_U, KNOB_V, KNOB_W, KNOB_H);

        // 数值
        String valStr = String.valueOf(sliderValues[idx]);
        fontRenderer.drawString(valStr, guiLeft + 4 + BAR2_OUT_W - 22, y + 4, 0x333333);
    }

    // ==================== 鼠标交互 ====================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // 顶部左按钮
        if (isInside(mouseX, mouseY, guiLeft + TOP_LEFT_X, guiTop + TOP_Y, BTN_LEFT_OUT_W, BTN_LEFT_OUT_H)) {
            // TODO: 待用户指定功能
            return;
        }
        // 顶部右按钮
        if (isInside(mouseX, mouseY, guiLeft + TOP_RIGHT_X, guiTop + TOP_Y, BTN_RIGHT_OUT_W, BTN_RIGHT_OUT_H)) {
            // TODO: 待用户指定功能
            return;
        }
        // 长条1（丝绸触摸开关区域）
        if (isInside(mouseX, mouseY, guiLeft + 4, guiTop + BAR1_Y, BAR1_OUT_W, BAR1_OUT_H)) {
            currentSilk = !currentSilk;
            return;
        }
        // 长条2（时运滑块区域）
        if (isInside(mouseX, mouseY, guiLeft + 70, guiTop + BAR2_Y, 110, BAR2_OUT_H)) {
            draggingSlider = 0;
            updateSlider(0, mouseX, guiLeft + 70, 110);
            return;
        }
        // 底部左按钮
        if (isInside(mouseX, mouseY, guiLeft + BOT_LEFT_X, guiTop + BOTTOM_Y, BTN_LEFT_OUT_W, BTN_LEFT_OUT_H)) {
            // TODO: 待用户指定功能
            return;
        }
        // 底部中按钮
        if (isInside(mouseX, mouseY, guiLeft + BOT_MID_X, guiTop + BOTTOM_Y, BTN_RIGHT_OUT_W, BTN_RIGHT_OUT_H)) {
            // TODO: 待用户指定功能
            return;
        }
        // 底部右小按钮
        if (isInside(mouseX, mouseY, guiLeft + BOT_SMALL_X, guiTop + BOTTOM_Y, SMOL_W, SMOL_H)) {
            // TODO: 待用户指定功能
            return;
        }
        // 大高亮条
        if (isInside(mouseX, mouseY, guiLeft + 4, guiTop + BIG_HL_Y, BIG_HL_W, BIG_HL_H)) {
            sendConfigToServer();
            mc.displayGuiScreen(null);
            return;
        }
        // 蓝色条
        if (isInside(mouseX, mouseY, guiLeft + 4, guiTop + BLUE_BAR_Y, 188, 17)) {
            // TODO: 待用户指定功能
            return;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        draggingSlider = -1;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (draggingSlider >= 0) {
            updateSlider(draggingSlider, mouseX, guiLeft + 70, 110);
        }
    }

    private void updateSlider(int idx, int mouseX, int trackX, int trackW) {
        float ratio = MathHelper.clamp((mouseX - trackX) / (float) (trackW - KNOB_W), 0f, 1f);
        sliderValues[idx] = sliderMin[idx] + Math.round(ratio * (sliderMax[idx] - sliderMin[idx]));
        switch (idx) {
            case 0: currentFortune = sliderValues[0]; break;
            case 1: currentBlinkDist = sliderValues[1]; break;
            case 2: currentCooldown = sliderValues[2]; break;
        }
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void sendConfigToServer() {
        AE2Enhanced.network.sendToServer(new PacketOmniToolConfig(
                currentMode, currentDropMode, currentSilk,
                currentFortune, currentBlinkDist, currentCooldown));
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}

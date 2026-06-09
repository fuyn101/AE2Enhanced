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

/**
 * 先进ME工具配置GUI —— 使用 me_omni_tool_gui.png 纹理图集.
 * 按照 docs/design/omni_gui_uv_document.md 的UV坐标绘制.
 */
public class GuiOmniToolConfig extends GuiScreen {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui.png");

    // GUI 外框尺寸
    private static final int GUI_W = 195;
    private static final int GUI_H = 221;

    // 颜色（来自UV文档颜色对照表）
    private static final int COLOR_BG = 0xFFCBCCD4;      // 背景浅灰 (203,204,212)
    private static final int COLOR_BORDER = 0xFFF2F2F2;  // 白色边框 (242,242,242)

    // ==================== 纹理 UV 坐标 ====================
    // 左按钮外框
    private static final int BTN_L_OUT_U = 4, BTN_L_OUT_V = 25;
    private static final int BTN_L_OUT_W = 75, BTN_L_OUT_H = 17;
    // 左按钮内部
    private static final int BTN_L_IN_U = 6, BTN_L_IN_V = 27;
    private static final int BTN_L_IN_W = 71, BTN_L_IN_H = 11;
    // 右按钮外框
    private static final int BTN_R_OUT_U = 116, BTN_R_OUT_V = 25;
    private static final int BTN_R_OUT_W = 75, BTN_R_OUT_H = 17;
    // 右按钮内部
    private static final int BTN_R_IN_U = 118, BTN_R_IN_V = 27;
    private static final int BTN_R_IN_W = 71, BTN_R_IN_H = 11;
    // 长条1 外框（开关条背景）
    private static final int BAR1_OUT_U = 4, BAR1_OUT_V = 102;
    private static final int BAR1_OUT_W = 188, BAR1_OUT_H = 17;
    // 长条1 内部
    private static final int BAR1_IN_U = 6, BAR1_IN_V = 104;
    private static final int BAR1_IN_W = 184, BAR1_IN_H = 11;
    // 长条2 外框（滑块条背景）
    private static final int BAR2_OUT_U = 4, BAR2_OUT_V = 122;
    private static final int BAR2_OUT_W = 188, BAR2_OUT_H = 17;
    // 长条2 内部
    private static final int BAR2_IN_U = 5, BAR2_IN_V = 123;
    private static final int BAR2_IN_W = 186, BAR2_IN_H = 15;
    // 普通小按钮
    private static final int SMOL_U = 0, SMOL_V = 221;
    private static final int SMOL_W = 12, SMOL_H = 17;
    // 高亮小按钮
    private static final int SMOL_HL_U = 75, SMOL_HL_V = 221;
    private static final int SMOL_HL_W = 12, SMOL_HL_H = 17;
    // 滑块
    private static final int KNOB_U = 150, KNOB_V = 221;
    private static final int KNOB_W = 12, KNOB_H = 17;
    // 高亮大按钮
    private static final int BIG_HL_U = 0, BIG_HL_V = 238;
    private static final int BIG_HL_W = 188, BIG_HL_H = 17;

    // ==================== 屏幕布局（相对于 guiLeft, guiTop）====================
    private static final int TITLE_Y = 6;
    // 模式选择行
    private static final int MODE_Y = 20;
    // 掉落模式行
    private static final int DROP_Y = 42;
    // 长条1 (丝绸触摸)
    private static final int BAR1_Y = 66;
    // 长条2 (时运)
    private static final int SLIDER_1_Y = 92;
    // 长条2 (闪烁)
    private static final int SLIDER_2_Y = 118;
    // 长条2 (冷却)
    private static final int SLIDER_3_Y = 144;
    // 确认按钮
    private static final int CONFIRM_Y = 190;

    private final EntityPlayer player;
    private ItemStack toolStack = ItemStack.EMPTY;

    // 当前配置值（本地缓存）
    private int currentMode;
    private int currentDropMode;
    private boolean currentSilk;
    private int currentFortune;
    private double currentBlinkDist;
    private int currentCooldown;

    // 滑条状态
    private int draggingSlider = -1;
    private final int[] sliderValues = new int[3];
    private final int[] sliderMin = {0, 1, 0};
    private final int[] sliderMax = {20, 256, 100};

    private int guiLeft, guiTop;

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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        guiLeft = (width - GUI_W) / 2;
        guiTop = (height - GUI_H) / 2;

        // ---- 绘制外框背景与边框 ----
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, COLOR_BG);
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + 1, COLOR_BORDER);
        drawRect(guiLeft, guiTop + GUI_H - 1, guiLeft + GUI_W, guiTop + GUI_H, COLOR_BORDER);
        drawRect(guiLeft, guiTop, guiLeft + 1, guiTop + GUI_H, COLOR_BORDER);
        drawRect(guiLeft + GUI_W - 1, guiTop, guiLeft + GUI_W, guiTop + GUI_H, COLOR_BORDER);

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1f, 1f, 1f, 1f);

        // ---- 标题 ----
        String title = I18n.format("gui.ae2enhanced.omni_tool_config.title");
        drawCenteredString(fontRenderer, title, guiLeft + GUI_W / 2, guiTop + TITLE_Y, 0x333333);

        // ---- 模式选择（4个小按钮）----
        String[] modeLabels = {"U", "W", "R", "T"}; // Universal / Wrench / Rotate / Travel
        int modeTotalW = 4 * SMOL_W + 3 * 6;
        int modeStartX = guiLeft + (GUI_W - modeTotalW) / 2;
        for (int i = 0; i < 4; i++) {
            int bx = modeStartX + i * (SMOL_W + 6);
            int by = guiTop + MODE_Y;
            boolean selected = (currentMode == i);
            drawTexturedModalRect(bx, by,
                    selected ? SMOL_HL_U : SMOL_U,
                    selected ? SMOL_HL_V : SMOL_V,
                    SMOL_W, SMOL_H);
            fontRenderer.drawString(modeLabels[i],
                    bx + (SMOL_W - fontRenderer.getStringWidth(modeLabels[i])) / 2,
                    by + 4,
                    selected ? 0xFFFFFF : 0x333333);
        }

        // ---- 掉落模式（3个小按钮）----
        String[] dropLabels = {"N", "I", "A"}; // Normal / Inventory / AE
        int dropTotalW = 3 * SMOL_W + 2 * 10;
        int dropStartX = guiLeft + (GUI_W - dropTotalW) / 2;
        for (int i = 0; i < 3; i++) {
            int bx = dropStartX + i * (SMOL_W + 10);
            int by = guiTop + DROP_Y;
            boolean selected = (currentDropMode == i);
            drawTexturedModalRect(bx, by,
                    selected ? SMOL_HL_U : SMOL_U,
                    selected ? SMOL_HL_V : SMOL_V,
                    SMOL_W, SMOL_H);
            fontRenderer.drawString(dropLabels[i],
                    bx + (SMOL_W - fontRenderer.getStringWidth(dropLabels[i])) / 2,
                    by + 4,
                    selected ? 0xFFFFFF : 0x333333);
        }

        // ---- 丝绸触摸（长条1 + 开关小按钮）----
        drawTexturedModalRect(guiLeft + 3, guiTop + BAR1_Y,
                BAR1_OUT_U, BAR1_OUT_V, BAR1_OUT_W, BAR1_OUT_H);
        String silkLabel = I18n.format("gui.ae2enhanced.omni_tool_config.silk_touch");
        fontRenderer.drawString(silkLabel, guiLeft + 10, guiTop + BAR1_Y + 4, 0x333333);
        int silkBtnX = guiLeft + GUI_W - 10 - SMOL_W;
        int silkBtnY = guiTop + BAR1_Y;
        drawTexturedModalRect(silkBtnX, silkBtnY,
                currentSilk ? SMOL_HL_U : SMOL_U,
                currentSilk ? SMOL_HL_V : SMOL_V,
                SMOL_W, SMOL_H);

        // ---- 三个滑条（长条2 + 滑块）----
        boolean hasFortune = !toolStack.isEmpty() && ItemAdvancedMEOmniTool.getFortuneLevel(toolStack) >= 0;
        boolean hasTravel = !toolStack.isEmpty() && ItemAdvancedMEOmniTool.hasTravelStaff(toolStack);
        drawSlider(SLIDER_1_Y, "gui.ae2enhanced.omni_tool_config.fortune", 0, hasFortune);
        drawSlider(SLIDER_2_Y, "gui.ae2enhanced.omni_tool_config.blink_dist", 1, hasTravel);
        drawSlider(SLIDER_3_Y, "gui.ae2enhanced.omni_tool_config.break_cooldown", 2, true);

        // ---- 确认按钮（高亮大按钮）----
        int cfx = guiLeft + (GUI_W - BIG_HL_W) / 2;
        int cfy = guiTop + CONFIRM_Y;
        drawTexturedModalRect(cfx, cfy, BIG_HL_U, BIG_HL_V, BIG_HL_W, BIG_HL_H);
        String confirmText = I18n.format("gui.done");
        drawCenteredString(fontRenderer, confirmText, guiLeft + GUI_W / 2, cfy + 4, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * 绘制单个滑条（长条2背景 + 滑块 + 标签 + 数值）
     */
    private void drawSlider(int y, String labelKey, int idx, boolean enabled) {
        int barX = guiLeft + 3;
        drawTexturedModalRect(barX, guiTop + y,
                BAR2_OUT_U, BAR2_OUT_V, BAR2_OUT_W, BAR2_OUT_H);

        String label = I18n.format(labelKey);
        int textColor = enabled ? 0x333333 : 0x888888;
        fontRenderer.drawString(label, barX + 8, guiTop + y + 4, textColor);

        if (!enabled) {
            String disabled = I18n.format("gui.ae2enhanced.omni_tool_config.not_installed");
            int dw = fontRenderer.getStringWidth(disabled);
            fontRenderer.drawString(disabled, barX + BAR2_OUT_W - 8 - dw, guiTop + y + 4, 0x888888);
            return;
        }

        // 滑块轨道：长条2内部右侧区域
        int trackX = barX + 70;
        int trackW = BAR2_OUT_W - 90; // 留给标签和数值的空间
        float ratio = (sliderValues[idx] - sliderMin[idx]) / (float) (sliderMax[idx] - sliderMin[idx]);
        int knobX = trackX + Math.round(ratio * (trackW - KNOB_W));

        // 绘制滑块
        drawTexturedModalRect(knobX, guiTop + y, KNOB_U, KNOB_V, KNOB_W, KNOB_H);

        // 数值
        String valStr = String.valueOf(sliderValues[idx]);
        fontRenderer.drawString(valStr, barX + BAR2_OUT_W - 22, guiTop + y + 4, 0x333333);
    }

    // ==================== 鼠标交互 ====================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // 模式按钮检测
        int modeTotalW = 4 * SMOL_W + 3 * 6;
        int modeStartX = guiLeft + (GUI_W - modeTotalW) / 2;
        for (int i = 0; i < 4; i++) {
            int bx = modeStartX + i * (SMOL_W + 6);
            int by = guiTop + MODE_Y;
            if (isInside(mouseX, mouseY, bx, by, SMOL_W, SMOL_H)) {
                currentMode = i;
                return;
            }
        }

        // 掉落模式按钮检测
        int dropTotalW = 3 * SMOL_W + 2 * 10;
        int dropStartX = guiLeft + (GUI_W - dropTotalW) / 2;
        for (int i = 0; i < 3; i++) {
            int bx = dropStartX + i * (SMOL_W + 10);
            int by = guiTop + DROP_Y;
            if (isInside(mouseX, mouseY, bx, by, SMOL_W, SMOL_H)) {
                currentDropMode = i;
                return;
            }
        }

        // 丝绸触摸开关检测
        int silkBtnX = guiLeft + GUI_W - 10 - SMOL_W;
        int silkBtnY = guiTop + BAR1_Y;
        if (isInside(mouseX, mouseY, silkBtnX, silkBtnY, SMOL_W, SMOL_H)) {
            currentSilk = !currentSilk;
            return;
        }

        // 滑条检测
        int[] sliderYs = {SLIDER_1_Y, SLIDER_2_Y, SLIDER_3_Y};
        for (int i = 0; i < 3; i++) {
            int barX = guiLeft + 3;
            int trackX = barX + 70;
            int trackW = BAR2_OUT_W - 90;
            int by = guiTop + sliderYs[i];
            if (isInside(mouseX, mouseY, trackX, by, trackW, BAR2_OUT_H)) {
                draggingSlider = i;
                updateSliderValue(i, mouseX, trackX, trackW);
                return;
            }
        }

        // 确认按钮检测
        int cfx = guiLeft + (GUI_W - BIG_HL_W) / 2;
        int cfy = guiTop + CONFIRM_Y;
        if (isInside(mouseX, mouseY, cfx, cfy, BIG_HL_W, BIG_HL_H)) {
            sendConfigToServer();
            mc.displayGuiScreen(null);
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
            int barX = guiLeft + 3;
            int trackX = barX + 70;
            int trackW = BAR2_OUT_W - 90;
            updateSliderValue(draggingSlider, mouseX, trackX, trackW);
        }
    }

    private void updateSliderValue(int idx, int mouseX, int trackX, int trackW) {
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
                currentMode,
                currentDropMode,
                currentSilk,
                currentFortune,
                currentBlinkDist,
                currentCooldown
        ));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

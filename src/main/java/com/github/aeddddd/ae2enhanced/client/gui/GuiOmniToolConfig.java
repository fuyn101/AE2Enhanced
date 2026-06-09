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
 * 先进ME工具配置GUI —— 严格按照 UV 文档布局实现.
 *
 * 纹理结构说明:
 *   y=0~220  : 主要纹理元素（按钮/长条/竖条的正常状态）
 *   y=221~237: 小按钮状态纹理（普通/高亮/滑块旋钮）
 *   y=238~255: 大按钮/长条的高亮/按下状态纹理
 *
 * 交互架构:
 *   顶部左右区域: 参数选择小按钮（每边最多4个，间距2px，超过8个分页）
 *   长条1: 当前选中参数的布尔开关（ON/悬停时用 y=238 高亮纹理）
 *   长条2: 当前选中参数的数值滑块（悬停/拖拽时用 y=238 高亮纹理）
 *   底部按钮: 复制纹理 / 改变状态（复用顶部按钮纹理）
 *   蓝色条: 颜色装饰（文档颜色表蓝色 112,140,186）
 */
public class GuiOmniToolConfig extends GuiScreen {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui.png");

    private static final int GUI_W = 195;
    private static final int GUI_H = 221;

    // ==================== 纹理 UV（主纹理 y=0~220）====================
    private static final int BTN_L_OUT_U = 4,  BTN_L_OUT_V = 25,  BTN_L_OUT_W = 75, BTN_L_OUT_H = 17;
    private static final int BTN_L_IN_U  = 6,  BTN_L_IN_V  = 27,  BTN_L_IN_W  = 71, BTN_L_IN_H  = 11;
    private static final int BTN_R_OUT_U = 116, BTN_R_OUT_V = 25,  BTN_R_OUT_W = 75, BTN_R_OUT_H = 17;
    private static final int BTN_R_IN_U  = 118, BTN_R_IN_V  = 27,  BTN_R_IN_W  = 71, BTN_R_IN_H  = 11;
    private static final int BAR_VERT_OUT_U = 81, BAR_VERT_OUT_V = 25, BAR_VERT_OUT_W = 33, BAR_VERT_OUT_H = 75;
    private static final int BAR_VERT_IN_U  = 83, BAR_VERT_IN_V  = 27, BAR_VERT_IN_W  = 29, BAR_VERT_IN_H  = 71;
    private static final int BAR1_OUT_U = 4,  BAR1_OUT_V = 102, BAR1_OUT_W = 188, BAR1_OUT_H = 17;
    private static final int BAR1_IN_U  = 6,  BAR1_IN_V  = 104, BAR1_IN_W  = 184, BAR1_IN_H  = 11;
    private static final int BAR2_OUT_U = 4,  BAR2_OUT_V = 122, BAR2_OUT_W = 187, BAR2_OUT_H = 16;
    private static final int BAR2_IN_U  = 5,  BAR2_IN_V  = 123, BAR2_IN_W  = 185, BAR2_IN_H  = 14;

    // ==================== 纹理 UV（状态纹理 y=221~255）====================
    // 小按钮正常 / 高亮 / 滑块（y=221~237）
    private static final int SMOL_NORM_U = 0,   SMOL_NORM_V = 221, SMOL_W = 12, SMOL_H = 17;
    private static final int SMOL_HL_U   = 75,  SMOL_HL_V   = 221;
    private static final int KNOB_U = 150, KNOB_V = 221, KNOB_W = 12, KNOB_H = 17;
    // 大按钮/长条高亮状态（y=238~255）— 前面按钮的不同状态
    private static final int BIG_HL_U = 0, BIG_HL_V = 238, BIG_HL_W = 188, BIG_HL_H = 17;

    // ==================== 屏幕坐标（严格按文档布局）====================
    private static final int TOP_Y = 25;
    private static final int TOP_LEFT_X = 4;
    private static final int TOP_VERT_X = 81;
    private static final int TOP_RIGHT_X = 116;
    private static final int BAR1_Y = 102;
    private static final int BAR2_Y = 122;
    private static final int BOTTOM_Y = 160;
    private static final int BOT_LEFT_X = 4;
    private static final int BOT_MID_X = 83;
    private static final int BOT_SMALL_X = 166;
    private static final int BLUE_BAR_Y = 202;

    private static final int SMALL_BTN_W = 12;
    private static final int SMALL_BTN_GAP = 2;
    private static final int SMALL_BTNS_PER_SIDE = 4;
    private static final int SMALL_BTNS_TOTAL_WIDTH = SMALL_BTNS_PER_SIDE * SMALL_BTN_W + (SMALL_BTNS_PER_SIDE - 1) * SMALL_BTN_GAP;

    // ==================== 参数定义 ====================
    private static final int TYPE_ENUM = 0;
    private static final int TYPE_BOOL = 1;
    private static final int TYPE_INT  = 2;

    private static class ParamDef {
        final String key;
        final int type;
        final int min, max;
        int value;
        ParamDef(String key, int type, int min, int max) {
            this.key = key; this.type = type; this.min = min; this.max = max;
        }
    }

    private final List<ParamDef> params = new ArrayList<>();
    private int selectedParam = 0;
    private int pageOffset = 0;

    private final EntityPlayer player;
    private ItemStack toolStack = ItemStack.EMPTY;
    private int guiLeft, guiTop;

    // 悬停/交互状态
    private boolean hoverTopLeft, hoverTopRight, hoverBotLeft, hoverBotMid, hoverBotSmall;
    private boolean hoverBar1, hoverBar2;
    private int draggingSlider = -1;

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
        toolStack = ItemStack.EMPTY;
        for (EnumHand hand : EnumHand.values()) {
            ItemStack s = player.getHeldItem(hand);
            if (!s.isEmpty() && s.getItem() instanceof ItemAdvancedMEOmniTool) {
                toolStack = s;
                break;
            }
        }
        if (toolStack.isEmpty()) {
            mc.displayGuiScreen(null);
            return;
        }

        params.clear();
        params.add(new ParamDef("mode", TYPE_ENUM, 0, 3));
        params.get(0).value = ItemAdvancedMEOmniTool.getMode(toolStack);
        params.add(new ParamDef("drop_mode", TYPE_ENUM, 0, 2));
        params.get(1).value = ItemAdvancedMEOmniTool.getDropMode(toolStack);
        params.add(new ParamDef("silk_touch", TYPE_BOOL, 0, 1));
        params.get(2).value = ItemAdvancedMEOmniTool.isSilkTouchEnabled(toolStack) ? 1 : 0;
        int fortune = ItemAdvancedMEOmniTool.getFortuneLevel(toolStack);
        params.add(new ParamDef("fortune", TYPE_INT, 0, 20));
        params.get(3).value = Math.max(0, fortune);
        params.add(new ParamDef("blink_dist", TYPE_INT, 1, 256));
        params.get(4).value = (int) ItemAdvancedMEOmniTool.getBlinkDistance(toolStack);
        params.add(new ParamDef("break_cooldown", TYPE_INT, 0, 100));
        params.get(5).value = ItemAdvancedMEOmniTool.getBreakCooldown(toolStack);

        selectedParam = 0;
        pageOffset = 0;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        guiLeft = (width - GUI_W) / 2;
        guiTop = (height - GUI_H) / 2;

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1f, 1f, 1f, 1f);

        // ---- 顶部：左按钮框 + 竖条 + 右按钮框 ----
        drawTexturedModalRect(guiLeft + TOP_LEFT_X, guiTop + TOP_Y,
                BTN_L_OUT_U, BTN_L_OUT_V, BTN_L_OUT_W, BTN_L_OUT_H);
        drawTexturedModalRect(guiLeft + TOP_LEFT_X + 2, guiTop + TOP_Y + 2,
                BTN_L_IN_U, BTN_L_IN_V, BTN_L_IN_W, BTN_L_IN_H);

        drawTexturedModalRect(guiLeft + TOP_VERT_X, guiTop + TOP_Y,
                BAR_VERT_OUT_U, BAR_VERT_OUT_V, BAR_VERT_OUT_W, BAR_VERT_OUT_H);
        drawTexturedModalRect(guiLeft + TOP_VERT_X + 2, guiTop + TOP_Y + 2,
                BAR_VERT_IN_U, BAR_VERT_IN_V, BAR_VERT_IN_W, BAR_VERT_IN_H);

        drawTexturedModalRect(guiLeft + TOP_RIGHT_X, guiTop + TOP_Y,
                BTN_R_OUT_U, BTN_R_OUT_V, BTN_R_OUT_W, BTN_R_OUT_H);
        drawTexturedModalRect(guiLeft + TOP_RIGHT_X + 2, guiTop + TOP_Y + 2,
                BTN_R_IN_U, BTN_R_IN_V, BTN_R_IN_W, BTN_R_IN_H);

        // ---- 顶部小按钮（参数选择）----
        int leftStartX = guiLeft + TOP_LEFT_X + (BTN_L_OUT_W - SMALL_BTNS_TOTAL_WIDTH) / 2;
        for (int i = 0; i < SMALL_BTNS_PER_SIDE; i++) {
            int gx = pageOffset * 8 + i;
            if (gx >= params.size()) break;
            int bx = leftStartX + i * (SMALL_BTN_W + SMALL_BTN_GAP);
            int by = guiTop + TOP_Y;
            boolean selected = (selectedParam == gx);
            drawTexturedModalRect(bx, by,
                    selected ? SMOL_HL_U : SMOL_NORM_U,
                    selected ? SMOL_HL_V : SMOL_NORM_V,
                    SMALL_BTN_W, SMOL_H);
            String num = String.valueOf(gx + 1);
            fontRenderer.drawString(num,
                    bx + (SMALL_BTN_W - fontRenderer.getStringWidth(num)) / 2,
                    by + 4, selected ? 0xFFFFFF : 0x333333);
        }
        int rightStartX = guiLeft + TOP_RIGHT_X + (BTN_R_OUT_W - SMALL_BTNS_TOTAL_WIDTH) / 2;
        for (int i = 0; i < SMALL_BTNS_PER_SIDE; i++) {
            int gx = pageOffset * 8 + SMALL_BTNS_PER_SIDE + i;
            if (gx >= params.size()) {
                if (params.size() > 8 && i == 3 && pageOffset == 0) {
                    drawPageButton(rightStartX + i * (SMALL_BTN_W + SMALL_BTN_GAP), guiTop + TOP_Y, false);
                }
                break;
            }
            int bx = rightStartX + i * (SMALL_BTN_W + SMALL_BTN_GAP);
            int by = guiTop + TOP_Y;
            boolean selected = (selectedParam == gx);
            drawTexturedModalRect(bx, by,
                    selected ? SMOL_HL_U : SMOL_NORM_U,
                    selected ? SMOL_HL_V : SMOL_NORM_V,
                    SMALL_BTN_W, SMOL_H);
            String num = String.valueOf(gx + 1);
            fontRenderer.drawString(num,
                    bx + (SMALL_BTN_W - fontRenderer.getStringWidth(num)) / 2,
                    by + 4, selected ? 0xFFFFFF : 0x333333);
        }
        if (pageOffset > 0) {
            drawPageButton(leftStartX, guiTop + TOP_Y, true);
        }

        // ---- 长条1：布尔开关（悬停/ON时用 y=238 高亮纹理）----
        ParamDef p = getSelectedParam();
        boolean bar1Active = p != null && p.value > 0;
        if (hoverBar1 || bar1Active) {
            // 使用 y=238 的高亮大按钮纹理 — 前面按钮的不同状态
            drawTexturedModalRect(guiLeft + 4, guiTop + BAR1_Y,
                    BIG_HL_U, BIG_HL_V, BIG_HL_W, BIG_HL_H);
        } else {
            drawTexturedModalRect(guiLeft + 4, guiTop + BAR1_Y,
                    BAR1_OUT_U, BAR1_OUT_V, BAR1_OUT_W, BAR1_OUT_H);
            drawTexturedModalRect(guiLeft + 6, guiTop + BAR1_Y + 2,
                    BAR1_IN_U, BAR1_IN_V, BAR1_IN_W, BAR1_IN_H);
        }
        if (p != null) {
            String bar1Label = I18n.format("gui.ae2enhanced.omni_tool_config." + p.key);
            fontRenderer.drawString(bar1Label, guiLeft + 12, guiTop + BAR1_Y + 4, 0x333333);
        }

        // ---- 长条2：滑块（悬停/拖拽时用 y=238 高亮纹理）----
        if (hoverBar2 || draggingSlider >= 0) {
            drawTexturedModalRect(guiLeft + 4, guiTop + BAR2_Y,
                    BIG_HL_U, BIG_HL_V, BIG_HL_W, BIG_HL_H);
        } else {
            drawTexturedModalRect(guiLeft + 4, guiTop + BAR2_Y,
                    BAR2_OUT_U, BAR2_OUT_V, BAR2_OUT_W, BAR2_OUT_H);
            drawTexturedModalRect(guiLeft + 5, guiTop + BAR2_Y + 1,
                    BAR2_IN_U, BAR2_IN_V, BAR2_IN_W, BAR2_IN_H);
        }
        if (p != null) {
            String bar2Label = I18n.format("gui.ae2enhanced.omni_tool_config." + p.key);
            fontRenderer.drawString(bar2Label, guiLeft + 12, guiTop + BAR2_Y + 3, 0x333333);

            int trackX = guiLeft + 70;
            int trackW = 95;
            float ratio = (p.value - p.min) / (float) (p.max - p.min);
            int knobX = trackX + Math.round(ratio * (trackW - KNOB_W));
            drawTexturedModalRect(knobX, guiTop + BAR2_Y, KNOB_U, KNOB_V, KNOB_W, KNOB_H);

            String valStr = String.valueOf(p.value);
            fontRenderer.drawString(valStr, guiLeft + 4 + BAR2_OUT_W - 22, guiTop + BAR2_Y + 3, 0x333333);
        }

        // ---- 底部按钮区（复用顶部按钮纹理）----
        // 左按钮
        int botLeftU = hoverBotLeft ? BTN_L_IN_U : BTN_L_OUT_U;
        int botLeftV = hoverBotLeft ? BTN_L_IN_V : BTN_L_OUT_V;
        drawTexturedModalRect(guiLeft + BOT_LEFT_X, guiTop + BOTTOM_Y,
                botLeftU, botLeftV, BTN_L_OUT_W, BTN_L_OUT_H);
        // 中按钮
        int botMidU = hoverBotMid ? BTN_R_IN_U : BTN_R_OUT_U;
        int botMidV = hoverBotMid ? BTN_R_IN_V : BTN_R_OUT_V;
        drawTexturedModalRect(guiLeft + BOT_MID_X, guiTop + BOTTOM_Y,
                botMidU, botMidV, BTN_R_OUT_W, BTN_R_OUT_H);
        // 右小按钮
        int smallU = hoverBotSmall ? SMOL_HL_U : SMOL_NORM_U;
        int smallV = hoverBotSmall ? SMOL_HL_V : SMOL_NORM_V;
        drawTexturedModalRect(guiLeft + BOT_SMALL_X, guiTop + BOTTOM_Y,
                smallU, smallV, SMOL_W, SMOL_H);

        // ---- 蓝色条（颜色装饰，文档颜色表蓝色 112,140,186）----
        drawRect(guiLeft + 4, guiTop + BLUE_BAR_Y,
                guiLeft + 4 + 188, guiTop + BLUE_BAR_Y + 17, 0xFF708CBA);

        // ---- 标题 ----
        String title = I18n.format("gui.ae2enhanced.omni_tool_config.title");
        drawCenteredString(fontRenderer, title, guiLeft + GUI_W / 2, guiTop + 6, 0x333333);

        updateHover(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPageButton(int x, int y, boolean isPrev) {
        drawTexturedModalRect(x, y, SMOL_NORM_U, SMOL_NORM_V, SMOL_W, SMOL_H);
        String arrow = isPrev ? "<" : ">";
        fontRenderer.drawString(arrow,
                x + (SMOL_W - fontRenderer.getStringWidth(arrow)) / 2,
                y + 4, 0x333333);
    }

    private ParamDef getSelectedParam() {
        if (selectedParam < 0 || selectedParam >= params.size()) return null;
        return params.get(selectedParam);
    }

    private void updateHover(int mx, int my) {
        hoverTopLeft  = in(mx, my, guiLeft + TOP_LEFT_X, guiTop + TOP_Y, BTN_L_OUT_W, BTN_L_OUT_H);
        hoverTopRight = in(mx, my, guiLeft + TOP_RIGHT_X, guiTop + TOP_Y, BTN_R_OUT_W, BTN_R_OUT_H);
        hoverBotLeft  = in(mx, my, guiLeft + BOT_LEFT_X, guiTop + BOTTOM_Y, BTN_L_OUT_W, BTN_L_OUT_H);
        hoverBotMid   = in(mx, my, guiLeft + BOT_MID_X, guiTop + BOTTOM_Y, BTN_R_OUT_W, BTN_R_OUT_H);
        hoverBotSmall = in(mx, my, guiLeft + BOT_SMALL_X, guiTop + BOTTOM_Y, SMOL_W, SMOL_H);
        hoverBar1     = in(mx, my, guiLeft + 4, guiTop + BAR1_Y, BAR1_OUT_W, BAR1_OUT_H);
        hoverBar2     = in(mx, my, guiLeft + 4, guiTop + BAR2_Y, BAR2_OUT_W, BAR2_OUT_H);
    }

    // ==================== 鼠标交互 ====================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // 顶部左边小按钮
        int leftStartX = guiLeft + TOP_LEFT_X + (BTN_L_OUT_W - SMALL_BTNS_TOTAL_WIDTH) / 2;
        for (int i = 0; i < SMALL_BTNS_PER_SIDE; i++) {
            int gx = pageOffset * 8 + i;
            if (gx >= params.size()) break;
            int bx = leftStartX + i * (SMALL_BTN_W + SMALL_BTN_GAP);
            if (in(mouseX, mouseY, bx, guiTop + TOP_Y, SMALL_BTN_W, SMOL_H)) {
                selectedParam = gx;
                return;
            }
        }

        // 顶部右边小按钮
        int rightStartX = guiLeft + TOP_RIGHT_X + (BTN_R_OUT_W - SMALL_BTNS_TOTAL_WIDTH) / 2;
        for (int i = 0; i < SMALL_BTNS_PER_SIDE; i++) {
            int gx = pageOffset * 8 + SMALL_BTNS_PER_SIDE + i;
            if (gx >= params.size()) {
                if (params.size() > 8 && i == 3 && pageOffset == 0) {
                    int bx = rightStartX + i * (SMALL_BTN_W + SMALL_BTN_GAP);
                    if (in(mouseX, mouseY, bx, guiTop + TOP_Y, SMALL_BTN_W, SMOL_H)) {
                        pageOffset++;
                        selectedParam = Math.min(selectedParam, params.size() - 1);
                        return;
                    }
                }
                break;
            }
            int bx = rightStartX + i * (SMALL_BTN_W + SMALL_BTN_GAP);
            if (in(mouseX, mouseY, bx, guiTop + TOP_Y, SMALL_BTN_W, SMOL_H)) {
                selectedParam = gx;
                return;
            }
        }
        if (pageOffset > 0) {
            int bx = leftStartX;
            if (in(mouseX, mouseY, bx, guiTop + TOP_Y, SMALL_BTN_W, SMOL_H)) {
                pageOffset--;
                return;
            }
        }

        // 长条1（开关切换）
        if (in(mouseX, mouseY, guiLeft + 4, guiTop + BAR1_Y, BAR1_OUT_W, BAR1_OUT_H)) {
            ParamDef pd = getSelectedParam();
            if (pd != null) {
                if (pd.type == TYPE_BOOL) {
                    pd.value = (pd.value > 0) ? 0 : 1;
                } else {
                    pd.value = (pd.value > pd.min) ? pd.min : pd.max;
                }
            }
            return;
        }

        // 长条2（滑块拖拽开始）
        if (in(mouseX, mouseY, guiLeft + 70, guiTop + BAR2_Y, 95, BAR2_OUT_H)) {
            draggingSlider = 0;
            updateSlider(mouseX, guiLeft + 70, 95);
            return;
        }

        // 底部左按钮 — 复制纹理
        if (in(mouseX, mouseY, guiLeft + BOT_LEFT_X, guiTop + BOTTOM_Y, BTN_L_OUT_W, BTN_L_OUT_H)) {
            // 复制纹理功能 — 待用户指定具体逻辑
            return;
        }
        // 底部中按钮 — 改变按钮状态
        if (in(mouseX, mouseY, guiLeft + BOT_MID_X, guiTop + BOTTOM_Y, BTN_R_OUT_W, BTN_R_OUT_H)) {
            // 改变状态功能 — 待用户指定具体逻辑
            return;
        }
        // 底部右小按钮
        if (in(mouseX, mouseY, guiLeft + BOT_SMALL_X, guiTop + BOTTOM_Y, SMOL_W, SMOL_H)) {
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
            updateSlider(mouseX, guiLeft + 70, 95);
        }
    }

    private void updateSlider(int mouseX, int trackX, int trackW) {
        ParamDef pd = getSelectedParam();
        if (pd == null) return;
        float ratio = MathHelper.clamp((mouseX - trackX) / (float) (trackW - KNOB_W), 0f, 1f);
        pd.value = pd.min + Math.round(ratio * (pd.max - pd.min));
    }

    /**
     * GUI 关闭时自动同步配置到服务端.
     */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        applyConfig();
    }

    private void applyConfig() {
        if (toolStack.isEmpty() || params.isEmpty()) return;
        int mode = params.get(0).value;
        int drop = params.get(1).value;
        boolean silk = params.get(2).value > 0;
        int fortune = params.get(3).value;
        double blink = params.get(4).value;
        int cooldown = params.get(5).value;

        ItemAdvancedMEOmniTool.setMode(toolStack, mode);
        ItemAdvancedMEOmniTool.setDropMode(toolStack, drop);
        ItemAdvancedMEOmniTool.setSilkTouchEnabled(toolStack, silk);
        if (ItemAdvancedMEOmniTool.getFortuneLevel(toolStack) >= 0) {
            ItemAdvancedMEOmniTool.setFortuneLevel(toolStack, fortune);
        }
        if (ItemAdvancedMEOmniTool.hasTravelStaff(toolStack)) {
            ItemAdvancedMEOmniTool.setBlinkDistance(toolStack, blink);
        }
        ItemAdvancedMEOmniTool.setBreakCooldown(toolStack, cooldown);

        AE2Enhanced.network.sendToServer(new PacketOmniToolConfig(
                mode, drop, silk, fortune, blink, cooldown));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}

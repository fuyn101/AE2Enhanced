package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniToolConfig;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolConfig;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;

/**
 * 先进ME工具配置GUI —— 严格遵循 me_omni_tool_gui.png UV文档.
 * 纹理 (0,0)->(195,221) 为背景，交互状态通过 y=221/y=238 区域复制纹理实现.
 */
public class GuiOmniToolConfig extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui.png");

    // GUI尺寸
    private static final int GUI_W = 195;
    private static final int GUI_H = 221;

    // ---- UV坐标：顶部按钮区（背景已包含默认槽位，此处仅记录文档值） ----
    private static final int LEFT_BTN_X = 4;   // 左侧按钮外框 x
    private static final int RIGHT_BTN_X = 116; // 右侧按钮外框 x
    private static final int BTN_W = 75;
    private static final int BTN_H = 17;
    private static final int BTN_Y0 = 25;       // 第一个按钮 y
    private static final int BTN_GAP = 2;       // 按钮间距
    private static final int BTN_STEP = BTN_H + BTN_GAP; // 19

    // ---- UV坐标：y=221 纹理复制区 ----
    private static final int TEX_NORMAL_BTN_U = 0;
    private static final int TEX_NORMAL_BTN_V = 221; // 普通小按钮 75x17
    private static final int TEX_HIGHLIGHT_BTN_U = 75;
    private static final int TEX_HIGHLIGHT_BTN_V = 221; // 高亮小按钮 75x17
    private static final int TEX_KNOB_U = 150;
    private static final int TEX_KNOB_V = 221; // 滑块 12x17
    private static final int KNOB_W = 12;
    private static final int KNOB_H = 17;

    // ---- UV坐标：y=238 高亮大条 ----
    private static final int TEX_HIGHLIGHT_BAR_U = 0;
    private static final int TEX_HIGHLIGHT_BAR_V = 238; // 高亮大按钮 188x17
    private static final int BAR_W = 188;
    private static final int BAR_H = 17;

    // ---- 中间长条坐标 ----
    private static final int BAR1_X = 4;
    private static final int BAR1_Y = 102;
    private static final int BAR2_X = 4;
    private static final int BAR2_Y = 122;

    // ---- 参数定义 ----
    private static final int PARAM_COUNT = 6;
    private static final String[] PARAM_LANG_KEYS = {
        "gui.ae2enhanced.omni_tool_config.mode",
        "gui.ae2enhanced.omni_tool_config.drop_mode",
        "gui.ae2enhanced.omni_tool_config.silk_touch",
        "gui.ae2enhanced.omni_tool_config.fortune",
        "gui.ae2enhanced.omni_tool_config.blink_dist",
        "gui.ae2enhanced.omni_tool_config.break_cooldown"
    };
    private static final int[] PARAM_MIN = {0, 0, 0, 0, 1, 0};
    private static final int[] PARAM_MAX = {3, 2, 1, 3, 256, 100};

    private final EntityPlayer player;
    private ItemStack toolStack = ItemStack.EMPTY;

    private int selParam = 0;
    private final int[] values = new int[PARAM_COUNT];
    private int paramEnabledMask = 0x3F; // 默认全部启用
    private int dragParam = -1;

    public GuiOmniToolConfig(EntityPlayer player, ContainerOmniToolConfig container) {
        super(container);
        this.player = player;
        this.xSize = GUI_W;
        this.ySize = GUI_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        reload();
    }

    private void reload() {
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

        values[0] = ItemAdvancedMEOmniTool.getMode(toolStack);
        values[1] = ItemAdvancedMEOmniTool.getDropMode(toolStack);
        values[2] = ItemAdvancedMEOmniTool.isSilkTouchEnabled(toolStack) ? 1 : 0;
        values[3] = Math.max(0, ItemAdvancedMEOmniTool.getFortuneLevel(toolStack));
        values[4] = (int) ItemAdvancedMEOmniTool.getBlinkDistance(toolStack);
        values[5] = ItemAdvancedMEOmniTool.getBreakCooldown(toolStack);

        paramEnabledMask = 0;
        for (int i = 0; i < PARAM_COUNT; i++) {
            if (ItemAdvancedMEOmniTool.isParamEnabled(toolStack, i)) {
                paramEnabledMask |= (1 << i);
            }
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);

        // 1. 完整背景
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_W, GUI_H);

        // 2. 顶部参数按钮 — 默认覆盖普通小按钮，选中时覆盖高亮小按钮
        for (int i = 0; i < PARAM_COUNT; i++) {
            int bx = (i < 3) ? LEFT_BTN_X : RIGHT_BTN_X;
            int by = BTN_Y0 + (i % 3) * BTN_STEP;
            int absX = this.guiLeft + bx;
            int absY = this.guiTop + by;
            boolean selected = (selParam == i);

            if (selected) {
                this.drawTexturedModalRect(absX, absY,
                        TEX_HIGHLIGHT_BTN_U, TEX_HIGHLIGHT_BTN_V, BTN_W, BTN_H);
            } else {
                this.drawTexturedModalRect(absX, absY,
                        TEX_NORMAL_BTN_U, TEX_NORMAL_BTN_V, BTN_W, BTN_H);
            }
        }

        // 3. Bar1 — 启用时叠加高亮大条
        if (isParamEnabled(selParam)) {
            this.drawTexturedModalRect(this.guiLeft + BAR1_X, this.guiTop + BAR1_Y,
                    TEX_HIGHLIGHT_BAR_U, TEX_HIGHLIGHT_BAR_V, BAR_W, BAR_H);
        }

        // 4. Bar2 — 滑块旋钮
        int knobX = computeKnobX(selParam);
        this.drawTexturedModalRect(knobX, this.guiTop + BAR2_Y,
                TEX_KNOB_U, TEX_KNOB_V, KNOB_W, KNOB_H);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        String title = I18n.format("gui.ae2enhanced.omni_tool_config.title");
        fontRenderer.drawString(title,
                GUI_W / 2 - fontRenderer.getStringWidth(title) / 2, 6, 0x333333);

        // 顶部按钮文字 — 参数名居中
        for (int i = 0; i < PARAM_COUNT; i++) {
            int bx = (i < 3) ? LEFT_BTN_X : RIGHT_BTN_X;
            int by = BTN_Y0 + (i % 3) * BTN_STEP;
            String name = I18n.format(PARAM_LANG_KEYS[i]);
            int tx = bx + BTN_W / 2 - fontRenderer.getStringWidth(name) / 2;
            int ty = by + (BTN_H - fontRenderer.FONT_HEIGHT) / 2 + 1;
            fontRenderer.drawString(name, tx, ty, 0x333333);
        }

        // Bar1 文字 — 参数名 + ON/OFF
        String bar1Name = I18n.format(PARAM_LANG_KEYS[selParam]);
        String bar1State = isParamEnabled(selParam) ? "ON" : "OFF";
        fontRenderer.drawString(bar1Name, BAR1_X + 6, BAR1_Y + 4, 0x333333);
        fontRenderer.drawString(bar1State, BAR1_X + BAR_W - 6 - fontRenderer.getStringWidth(bar1State), BAR1_Y + 4, 0x333333);

        // Bar2 文字 — 当前值
        String valStr = formatValue(selParam, values[selParam]);
        fontRenderer.drawString(valStr,
                BAR2_X + BAR_W - 6 - fontRenderer.getStringWidth(valStr), BAR2_Y + 4, 0x333333);
    }

    private String formatValue(int param, int value) {
        switch (param) {
            case 0:
                return I18n.format(ItemAdvancedMEOmniTool.getModeNameKey(value));
            case 1:
                return I18n.format(ItemAdvancedMEOmniTool.getDropModeNameKey(value));
            case 2:
                return value > 0 ? "ON" : "OFF";
            default:
                return String.valueOf(value);
        }
    }

    private int computeKnobX(int param) {
        int min = PARAM_MIN[param];
        int max = PARAM_MAX[param];
        float ratio = (values[param] - min) / (float) (max - min);
        int trackX = this.guiLeft + BAR2_X;
        return trackX + Math.round(ratio * (BAR_W - KNOB_W));
    }

    private boolean isParamEnabled(int param) {
        return (paramEnabledMask & (1 << param)) != 0;
    }

    private void setParamEnabled(int param, boolean enabled) {
        if (enabled) paramEnabledMask |= (1 << param);
        else paramEnabledMask &= ~(1 << param);
    }

    // ==================== 交互 ====================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // 顶部参数按钮
        for (int i = 0; i < PARAM_COUNT; i++) {
            int bx = this.guiLeft + ((i < 3) ? LEFT_BTN_X : RIGHT_BTN_X);
            int by = this.guiTop + BTN_Y0 + (i % 3) * BTN_STEP;
            if (in(mouseX, mouseY, bx, by, BTN_W, BTN_H)) {
                selParam = i;
                return;
            }
        }

        // Bar1 — 切换启用/禁用
        if (in(mouseX, mouseY, this.guiLeft + BAR1_X, this.guiTop + BAR1_Y, BAR_W, BAR_H)) {
            setParamEnabled(selParam, !isParamEnabled(selParam));
            return;
        }

        // Bar2 — 开始拖拽
        if (in(mouseX, mouseY, this.guiLeft + BAR2_X, this.guiTop + BAR2_Y, BAR_W, BAR_H)) {
            dragParam = selParam;
            updateSlider(mouseX);
            return;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragParam = -1;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragParam >= 0) updateSlider(mouseX);
    }

    private void updateSlider(int mouseX) {
        int trackX = this.guiLeft + BAR2_X;
        float ratio = MathHelper.clamp((mouseX - trackX) / (float) (BAR_W - KNOB_W), 0f, 1f);
        int min = PARAM_MIN[dragParam];
        int max = PARAM_MAX[dragParam];
        values[dragParam] = min + Math.round(ratio * (max - min));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        apply();
    }

    private void apply() {
        if (toolStack.isEmpty()) return;
        ItemAdvancedMEOmniTool.setMode(toolStack, values[0]);
        ItemAdvancedMEOmniTool.setDropMode(toolStack, values[1]);
        ItemAdvancedMEOmniTool.setSilkTouchEnabled(toolStack, values[2] > 0);
        ItemAdvancedMEOmniTool.setFortuneLevel(toolStack, values[3]);
        ItemAdvancedMEOmniTool.setBlinkDistance(toolStack, values[4]);
        ItemAdvancedMEOmniTool.setBreakCooldown(toolStack, values[5]);
        for (int i = 0; i < PARAM_COUNT; i++) {
            ItemAdvancedMEOmniTool.setParamEnabled(toolStack, i, isParamEnabled(i));
        }

        AE2Enhanced.network.sendToServer(new PacketOmniToolConfig(
                values[0], values[1], values[2] > 0,
                values[3], values[4], values[5], paramEnabledMask));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

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
 * 先进ME工具配置GUI —— 参考 GuiAssemblyFormed 的纹理绘制模式.
 * 背景纹理 (0,0)->(195,221) 包含完整GUI设计，交互状态通过叠加纹理实现.
 */
public class GuiOmniToolConfig extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2Enhanced.MOD_ID, "textures/gui/me_omni_tool_gui.png");

    // GUI尺寸（与纹理设计匹配）
    private static final int GUI_W = 195;
    private static final int GUI_H = 221;

    // ---- 纹理UV：小按钮与滑块（y=221状态区）----
    private static final int SMOL_U = 0, SMOL_V = 221;
    private static final int SMOL_HL_U = 75, SMOL_HL_V = 221;
    private static final int SMOL_W = 12, SMOL_H = 18;
    private static final int KNOB_U = 150, KNOB_V = 221;
    private static final int KNOB_W = 12, KNOB_H = 17;

    // ---- 纹理UV：长条高亮（y=238状态区）----
    private static final int BAR_HL_U = 0, BAR_HL_V = 238;
    private static final int BAR_HL_W = 188, BAR_HL_H = 17;

    // ---- 屏幕坐标（相对于guiTop/guiLeft）----
    // 小按钮竖向排列在竖条两侧，与竖条间距2px
    private static final int VERT_BAR_X = 81;   // 竖条起始x
    private static final int VERT_BAR_W = 33;   // 竖条宽
    private static final int VERT_BAR_Y = 25;   // 竖条起始y
    private static final int VERT_BAR_H = 75;   // 竖条高
    private static final int SMALL_LEFT_X = 35;  // 4 + (75-12)/2, 左区域内居中
    private static final int SMALL_RIGHT_X = 147; // 116 + (75-12)/2, 右区域内居中
    private static final int SMALL_Y0 = 24;       // 适配 4×18+3×2=78 在 75px 竖条区域
    private static final int SMALL_H = 18;        // 文档明确 12×18
    private static final int SMALL_GAP = 2;       // 按钮相互间隔 2px
    private static final int SMALL_PER_SIDE = 4;

    // 长条
    private static final int BAR1_X = 4, BAR1_Y = 102, BAR1_W = 188, BAR1_H = 17;
    private static final int BAR2_X = 4, BAR2_Y = 122, BAR2_W = 187, BAR2_H = 16;

    // 底部按钮
    private static final int BOT_Y = 160;
    private static final int BOT_LEFT_X = 4;
    private static final int BOT_MID_X = 83;
    private static final int BOT_SMALL_X = 166;

    // ---- 参数数据 ----
    private final EntityPlayer player;
    private ItemStack toolStack = ItemStack.EMPTY;

    private int selParam = 0;      // 当前选中参数 0~5
    private int[] values = new int[6];
    private static final int[] MIN = {0, 0, 0, 0, 1, 0};
    private static final int[] MAX = {3, 2, 1, 20, 256, 100};

    private int drag = -1;

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
        if (toolStack.isEmpty()) { mc.displayGuiScreen(null); return; }

        values[0] = ItemAdvancedMEOmniTool.getMode(toolStack);
        values[1] = ItemAdvancedMEOmniTool.getDropMode(toolStack);
        values[2] = ItemAdvancedMEOmniTool.isSilkTouchEnabled(toolStack) ? 1 : 0;
        values[3] = Math.max(0, ItemAdvancedMEOmniTool.getFortuneLevel(toolStack));
        values[4] = (int) ItemAdvancedMEOmniTool.getBlinkDistance(toolStack);
        values[5] = ItemAdvancedMEOmniTool.getBreakCooldown(toolStack);
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

        // 1. 绘制完整背景（纹理 0,0 -> 195,221）
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_W, GUI_H);

        // 2. 顶部小按钮（竖向排列在竖条两侧）
        for (int i = 0; i < SMALL_PER_SIDE; i++) {
            int idx = i;
            if (idx >= 6) break;
            int x = this.guiLeft + SMALL_LEFT_X;
            int y = this.guiTop + SMALL_Y0 + i * (SMALL_H + SMALL_GAP);
            boolean on = (selParam == idx);
            this.drawTexturedModalRect(x, y,
                    on ? SMOL_HL_U : SMOL_U,
                    on ? SMOL_HL_V : SMOL_V,
                    SMOL_W, SMOL_H);
        }
        for (int i = 0; i < SMALL_PER_SIDE; i++) {
            int idx = 4 + i;
            if (idx >= 6) break;
            int x = this.guiLeft + SMALL_RIGHT_X;
            int y = this.guiTop + SMALL_Y0 + i * (SMALL_H + SMALL_GAP);
            boolean on = (selParam == idx);
            this.drawTexturedModalRect(x, y,
                    on ? SMOL_HL_U : SMOL_U,
                    on ? SMOL_HL_V : SMOL_V,
                    SMOL_W, SMOL_H);
        }

        // 3. 长条1：开关 — ON或悬停时叠加高亮纹理
        if (values[selParam] > 0 || in(mouseX, mouseY, this.guiLeft + BAR1_X, this.guiTop + BAR1_Y, BAR1_W, BAR1_H)) {
            this.drawTexturedModalRect(this.guiLeft + BAR1_X, this.guiTop + BAR1_Y,
                    BAR_HL_U, BAR_HL_V, BAR_HL_W, BAR_HL_H);
        }

        // 4. 长条2：滑块 — 不叠加高亮纹理
        // 滑块旋钮
        int trackX = this.guiLeft + BAR2_X + 65;
        int trackW = 100;
        float r = (values[selParam] - MIN[selParam]) / (float) (MAX[selParam] - MIN[selParam]);
        int knobX = trackX + Math.round(r * (trackW - KNOB_W));
        this.drawTexturedModalRect(knobX, this.guiTop + BAR2_Y, KNOB_U, KNOB_V, KNOB_W, KNOB_H);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.omni_tool_config.title"),
                GUI_W / 2 - fontRenderer.getStringWidth(I18n.format("gui.ae2enhanced.omni_tool_config.title")) / 2,
                6, 0x333333);

        // 当前参数名（长条1左侧）
        String name = paramName(selParam);
        fontRenderer.drawString(name, 12, BAR1_Y + 4, 0x333333);

        // 长条2标签 + 数值
        fontRenderer.drawString(name, 12, BAR2_Y + 3, 0x333333);
        String val = String.valueOf(values[selParam]);
        fontRenderer.drawString(val, BAR2_X + BAR2_W - 22, BAR2_Y + 3, 0x333333);
    }

    private String paramName(int idx) {
        switch (idx) {
            case 0: return I18n.format("gui.ae2enhanced.omni_tool_config.mode");
            case 1: return I18n.format("gui.ae2enhanced.omni_tool_config.drop_mode");
            case 2: return I18n.format("gui.ae2enhanced.omni_tool_config.silk_touch");
            case 3: return I18n.format("gui.ae2enhanced.omni_tool_config.fortune");
            case 4: return I18n.format("gui.ae2enhanced.omni_tool_config.blink_dist");
            case 5: return I18n.format("gui.ae2enhanced.omni_tool_config.break_cooldown");
            default: return "";
        }
    }

    // ==================== 交互 ====================

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // 顶部左边小按钮（竖向）
        for (int i = 0; i < SMALL_PER_SIDE; i++) {
            int idx = i;
            if (idx >= 6) break;
            int x = this.guiLeft + SMALL_LEFT_X;
            int y = this.guiTop + SMALL_Y0 + i * (SMALL_H + SMALL_GAP);
            if (in(mouseX, mouseY, x, y, SMOL_W, SMALL_H)) {
                selParam = idx;
                return;
            }
        }
        // 顶部右边小按钮（竖向）
        for (int i = 0; i < SMALL_PER_SIDE; i++) {
            int idx = 4 + i;
            if (idx >= 6) break;
            int x = this.guiLeft + SMALL_RIGHT_X;
            int y = this.guiTop + SMALL_Y0 + i * (SMALL_H + SMALL_GAP);
            if (in(mouseX, mouseY, x, y, SMOL_W, SMALL_H)) {
                selParam = idx;
                return;
            }
        }

        // 长条1 — 切换开关
        if (in(mouseX, mouseY, this.guiLeft + BAR1_X, this.guiTop + BAR1_Y, BAR1_W, BAR1_H)) {
            values[selParam] = (values[selParam] > MIN[selParam]) ? MIN[selParam] : MAX[selParam];
            return;
        }

        // 长条2 — 开始拖拽
        if (in(mouseX, mouseY, this.guiLeft + BAR2_X + 65, this.guiTop + BAR2_Y, 100, BAR2_H)) {
            drag = selParam;
            updateSlider(mouseX);
            return;
        }

        // 底部左按钮 — 复制纹理
        if (in(mouseX, mouseY, this.guiLeft + BOT_LEFT_X, this.guiTop + BOT_Y, 75, 17)) {
            // 复制纹理 — 待用户指定具体逻辑
            return;
        }
        // 底部中按钮 — 改变状态
        if (in(mouseX, mouseY, this.guiLeft + BOT_MID_X, this.guiTop + BOT_Y, 75, 17)) {
            // 改变状态 — 待用户指定具体逻辑
            return;
        }
        // 底部右小按钮
        if (in(mouseX, mouseY, this.guiLeft + BOT_SMALL_X, this.guiTop + BOT_Y, SMOL_W, SMOL_H)) {
            return;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        drag = -1;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (drag >= 0) updateSlider(mouseX);
    }

    private void updateSlider(int mouseX) {
        int trackX = this.guiLeft + BAR2_X + 65;
        int trackW = 100;
        float ratio = MathHelper.clamp((mouseX - trackX) / (float) (trackW - KNOB_W), 0f, 1f);
        values[selParam] = MIN[selParam] + Math.round(ratio * (MAX[selParam] - MIN[selParam]));
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
        if (ItemAdvancedMEOmniTool.getFortuneLevel(toolStack) >= 0)
            ItemAdvancedMEOmniTool.setFortuneLevel(toolStack, values[3]);
        if (ItemAdvancedMEOmniTool.hasTravelStaff(toolStack))
            ItemAdvancedMEOmniTool.setBlinkDistance(toolStack, values[4]);
        ItemAdvancedMEOmniTool.setBreakCooldown(toolStack, values[5]);

        AE2Enhanced.network.sendToServer(new PacketOmniToolConfig(
                values[0], values[1], values[2] > 0,
                values[3], values[4], values[5]));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

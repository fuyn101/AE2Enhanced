package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolConfig;
import net.minecraft.client.gui.GuiButton;
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
 * 先进ME工具配置GUI —— 调控各功能开关与数值滑块.
 * 使用自定义纹理（UV坐标由外部文档提供）.
 */
public class GuiOmniToolConfig extends GuiScreen {

    // 暂定使用默认样式，等UV文档后替换为自定义纹理
    private static final ResourceLocation TEXTURE = new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/omni_tool_config.png");

    private static final int GUI_WIDTH = 200;
    private static final int GUI_HEIGHT = 220;

    private final EntityPlayer player;
    private ItemStack toolStack;

    // 控件ID
    private static final int BTN_MODE_0 = 10;
    private static final int BTN_MODE_1 = 11;
    private static final int BTN_MODE_2 = 12;
    private static final int BTN_MODE_3 = 13;
    private static final int BTN_DROP_0 = 20;
    private static final int BTN_DROP_1 = 21;
    private static final int BTN_DROP_2 = 22;
    private static final int BTN_SILK = 30;
    private static final int SLIDER_FORTUNE = 40;
    private static final int SLIDER_BLINK = 41;
    private static final int SLIDER_COOLDOWN = 42;
    private static final int BTN_CONFIRM = 50;

    // 当前值（与服务端同步前的本地缓存）
    private int currentMode;
    private int currentDropMode;
    private boolean currentSilk;
    private int currentFortune;
    private double currentBlinkDist;
    private int currentCooldown;

    // 滑条状态
    private int draggingSlider = -1;
    private final int[] sliderValues = new int[3];
    private final int[] sliderMax = {20, 256, 100};
    private final int[] sliderMin = {0, 1, 0};

    public GuiOmniToolConfig(EntityPlayer player) {
        this.player = player;
        refreshStack();
    }

    private void refreshStack() {
        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                this.toolStack = stack;
                break;
            }
        }
        if (toolStack == null || toolStack.isEmpty()) {
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
    public void initGui() {
        super.initGui();
        int cx = (width - GUI_WIDTH) / 2;
        int cy = (height - GUI_HEIGHT) / 2;

        // 模式选择按钮（4个）
        addButton(new GuiButton(BTN_MODE_0, cx + 10, cy + 25, 42, 18, I18n.format("item.ae2enhanced.me_omni_tool.mode.universal")));
        addButton(new GuiButton(BTN_MODE_1, cx + 56, cy + 25, 42, 18, I18n.format("item.ae2enhanced.me_omni_tool.mode.wrench")));
        addButton(new GuiButton(BTN_MODE_2, cx + 102, cy + 25, 42, 18, I18n.format("item.ae2enhanced.me_omni_tool.mode.rotate")));
        addButton(new GuiButton(BTN_MODE_3, cx + 148, cy + 25, 42, 18, I18n.format("item.ae2enhanced.me_omni_tool.mode.travel")));

        // 掉落模式按钮（3个）
        addButton(new GuiButton(BTN_DROP_0, cx + 10, cy + 65, 58, 18, I18n.format("item.ae2enhanced.me_omni_tool.drop_mode.normal")));
        addButton(new GuiButton(BTN_DROP_1, cx + 72, cy + 65, 58, 18, I18n.format("item.ae2enhanced.me_omni_tool.drop_mode.inventory")));
        addButton(new GuiButton(BTN_DROP_2, cx + 134, cy + 65, 58, 18, I18n.format("item.ae2enhanced.me_omni_tool.drop_mode.ae")));

        // 丝绸触摸开关
        addButton(new GuiButton(BTN_SILK, cx + 10, cy + 95, 80, 18, ""));

        // 确认按钮
        addButton(new GuiButton(BTN_CONFIRM, cx + GUI_WIDTH / 2 - 40, cy + GUI_HEIGHT - 28, 80, 20, I18n.format("gui.done")));

        updateButtonStates();
    }

    private void updateButtonStates() {
        // 更新模式按钮状态
        for (int i = 0; i < 4; i++) {
            GuiButton btn = buttonList.get(i);
            btn.enabled = (currentMode != i);
        }
        // 更新掉落模式按钮状态
        for (int i = 0; i < 3; i++) {
            GuiButton btn = buttonList.get(4 + i);
            btn.enabled = (currentDropMode != i);
        }
        // 更新丝绸触摸按钮文本
        GuiButton silkBtn = buttonList.get(7);
        String silkText = currentSilk
                ? I18n.format("item.ae2enhanced.me_omni_tool.silk_touch.on")
                : I18n.format("item.ae2enhanced.me_omni_tool.silk_touch.off");
        silkBtn.displayString = silkText;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);
        switch (button.id) {
            case BTN_MODE_0: currentMode = 0; break;
            case BTN_MODE_1: currentMode = 1; break;
            case BTN_MODE_2: currentMode = 2; break;
            case BTN_MODE_3: currentMode = 3; break;
            case BTN_DROP_0: currentDropMode = 0; break;
            case BTN_DROP_1: currentDropMode = 1; break;
            case BTN_DROP_2: currentDropMode = 2; break;
            case BTN_SILK: currentSilk = !currentSilk; break;
            case BTN_CONFIRM:
                sendConfigToServer();
                mc.displayGuiScreen(null);
                return;
        }
        updateButtonStates();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        int cx = (width - GUI_WIDTH) / 2;
        int cy = (height - GUI_HEIGHT) / 2;

        // 检查是否点击了滑条
        for (int i = 0; i < 3; i++) {
            int sliderX = cx + 80;
            int sliderY = cy + 125 + i * 28;
            int sliderW = 100;
            int sliderH = 12;
            if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= sliderY && mouseY <= sliderY + sliderH) {
                draggingSlider = i;
                updateSliderValue(i, mouseX, sliderX, sliderW);
                break;
            }
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
            int cx = (width - GUI_WIDTH) / 2;
            int sliderX = cx + 80;
            int sliderW = 100;
            updateSliderValue(draggingSlider, mouseX, sliderX, sliderW);
        }
    }

    private void updateSliderValue(int idx, int mouseX, int sliderX, int sliderW) {
        float ratio = MathHelper.clamp((mouseX - sliderX) / (float) sliderW, 0f, 1f);
        sliderValues[idx] = sliderMin[idx] + Math.round(ratio * (sliderMax[idx] - sliderMin[idx]));
        switch (idx) {
            case 0: currentFortune = sliderValues[0]; break;
            case 1: currentBlinkDist = sliderValues[1]; break;
            case 2: currentCooldown = sliderValues[2]; break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int cx = (width - GUI_WIDTH) / 2;
        int cy = (height - GUI_HEIGHT) / 2;

        // 绘制GUI背景（默认矩形，等UV文档后替换为纹理）
        GlStateManager.color(1f, 1f, 1f, 1f);
        drawRect(cx, cy, cx + GUI_WIDTH, cy + GUI_HEIGHT, 0xCC000000);
        drawRect(cx + 1, cy + 1, cx + GUI_WIDTH - 1, cy + GUI_HEIGHT - 1, 0xFF2B2B2B);
        drawRect(cx + 2, cy + 2, cx + GUI_WIDTH - 2, cy + 16, 0xFF3C3C3C);

        // 标题
        String title = I18n.format("gui.ae2enhanced.omni_tool_config.title");
        fontRenderer.drawStringWithShadow(title, cx + GUI_WIDTH / 2 - fontRenderer.getStringWidth(title) / 2, cy + 5, 0xFFFFFF);

        // 绘制标签
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.omni_tool_config.mode"), cx + 10, cy + 14, 0xAAAAAA);
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.omni_tool_config.drop_mode"), cx + 10, cy + 54, 0xAAAAAA);
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.omni_tool_config.silk_touch"), cx + 10, cy + 99, 0xAAAAAA);

        // 绘制滑条标签和数值
        boolean hasFortune = toolStack != null && ItemAdvancedMEOmniTool.getFortuneLevel(toolStack) >= 0;
        boolean hasTravel = toolStack != null && ItemAdvancedMEOmniTool.hasTravelStaff(toolStack);

        drawSlider(cx, cy, 0, I18n.format("gui.ae2enhanced.omni_tool_config.fortune"), sliderValues[0], hasFortune);
        drawSlider(cx, cy, 1, I18n.format("gui.ae2enhanced.omni_tool_config.blink_dist"), sliderValues[1], hasTravel);
        drawSlider(cx, cy, 2, I18n.format("gui.ae2enhanced.omni_tool_config.break_cooldown"), sliderValues[2], true);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSlider(int cx, int cy, int idx, String label, int value, boolean enabled) {
        int sliderX = cx + 80;
        int sliderY = cy + 125 + idx * 28;
        int sliderW = 100;
        int sliderH = 12;
        int labelY = sliderY + 2;

        int color = enabled ? 0xAAAAAA : 0x555555;
        fontRenderer.drawString(label, cx + 10, labelY, color);

        if (!enabled) {
            drawRect(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF1A1A1A);
            String disabledText = I18n.format("gui.ae2enhanced.omni_tool_config.not_installed");
            fontRenderer.drawString(disabledText, sliderX + 2, labelY, 0x555555);
            return;
        }

        // 滑条轨道
        drawRect(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF1A1A1A);
        drawRect(sliderX + 1, sliderY + 1, sliderX + sliderW - 1, sliderY + sliderH - 1, 0xFF3C3C3C);

        // 滑块位置
        float ratio = (float)(value - sliderMin[idx]) / (sliderMax[idx] - sliderMin[idx]);
        int knobX = sliderX + Math.round(ratio * (sliderW - 8));
        drawRect(knobX, sliderY - 1, knobX + 8, sliderY + sliderH + 1, 0xFF888888);
        drawRect(knobX + 1, sliderY, knobX + 7, sliderY + sliderH, 0xFFAAAAAA);

        // 数值
        fontRenderer.drawString(String.valueOf(value), sliderX + sliderW + 4, labelY, 0xFFFFFF);
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

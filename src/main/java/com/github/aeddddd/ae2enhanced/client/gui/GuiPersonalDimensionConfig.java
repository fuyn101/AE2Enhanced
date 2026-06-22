package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerPersonalDimensionConfig;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRules;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import java.io.IOException;

/**
 * 个人维度规则配置 GUI。
 */
public class GuiPersonalDimensionConfig extends GuiContainer {

    private static final int BTN_MOB = 0;
    private static final int BTN_WEATHER = 1;
    private static final int BTN_TIME = 2;
    private static final int BTN_DAYLIGHT = 3;
    private static final int BTN_SAVE = 4;

    private final EntityPlayer player;
    private PersonalDimensionRules rules;

    private GuiButton btnMob;
    private GuiButton btnWeather;
    private GuiButton btnTime;
    private GuiButton btnDaylight;
    private GuiButton btnSave;
    private GuiTextField timeField;

    public GuiPersonalDimensionConfig(EntityPlayer player, ContainerPersonalDimensionConfig container) {
        super(container);
        this.player = player;
        this.xSize = 176;
        this.ySize = 166;
        PlayerDimEntry entry = PersonalDimensionManager.getEntry(player.getUniqueID());
        this.rules = entry != null ? entry.rules.copy() : new PersonalDimensionRules();
    }

    @Override
    public void initGui() {
        super.initGui();
        int cx = (this.width - this.xSize) / 2;
        int cy = (this.height - this.ySize) / 2;
        int x = cx + 10;
        int y = cy + 20;
        int w = 156;
        int h = 20;

        btnMob = new GuiButton(BTN_MOB, x, y, w, h, "");
        btnWeather = new GuiButton(BTN_WEATHER, x, y + 24, w, h, "");
        btnTime = new GuiButton(BTN_TIME, x, y + 48, w, h, "");
        btnDaylight = new GuiButton(BTN_DAYLIGHT, x, y + 72, w, h, "");
        btnSave = new GuiButton(BTN_SAVE, x, y + 116, w, h, I18n.format("gui.ae2enhanced.personal_dimension.save"));

        buttonList.add(btnMob);
        buttonList.add(btnWeather);
        buttonList.add(btnTime);
        buttonList.add(btnDaylight);
        buttonList.add(btnSave);

        timeField = new GuiTextField(0, fontRenderer, x + 100, y + 98, 56, 14);
        timeField.setMaxStringLength(7);
        timeField.setText(String.valueOf(rules.timeValue));
        updateButtonText();
    }

    private void updateButtonText() {
        btnMob.displayString = label("spawn_mobs", !rules.disableMobSpawning);
        btnWeather.displayString = label("lock_weather", rules.lockWeather);
        btnTime.displayString = label("lock_time", rules.lockTime);
        btnDaylight.displayString = label("daylight_cycle", rules.daylightCycle);
    }

    private String label(String key, boolean enabled) {
        String state = enabled ? "gui.ae2enhanced.personal_dimension.on" : "gui.ae2enhanced.personal_dimension.off";
        return I18n.format("gui.ae2enhanced.personal_dimension." + key) + ": " + I18n.format(state);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BTN_MOB:
                rules.disableMobSpawning = !rules.disableMobSpawning;
                break;
            case BTN_WEATHER:
                rules.lockWeather = !rules.lockWeather;
                break;
            case BTN_TIME:
                rules.lockTime = !rules.lockTime;
                break;
            case BTN_DAYLIGHT:
                rules.daylightCycle = !rules.daylightCycle;
                break;
            case BTN_SAVE:
                apply();
                mc.displayGuiScreen(null);
                return;
        }
        updateButtonText();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (timeField.textboxKeyTyped(typedChar, keyCode)) {
            try {
                rules.timeValue = Long.parseLong(timeField.getText());
            } catch (NumberFormatException ignored) {
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        timeField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int cx = (this.width - this.xSize) / 2;
        int cy = (this.height - this.ySize) / 2;
        drawGradientRect(cx, cy, cx + xSize, cy + ySize, 0xFF333333, 0xFF111111);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.personal_dimension.title");
        fontRenderer.drawString(title, xSize / 2 - fontRenderer.getStringWidth(title) / 2, 6, 0xFFFFFF);

        String label = I18n.format("gui.ae2enhanced.personal_dimension.time_value");
        fontRenderer.drawString(label, 10, 100, 0xFFFFFF);

        timeField.drawTextBox();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        apply();
    }

    private void apply() {
        try {
            rules.timeValue = Long.parseLong(timeField.getText());
        } catch (NumberFormatException ignored) {
        }
        AE2Enhanced.network.sendToServer(new PacketPersonalDimensionRules(rules));
    }
}

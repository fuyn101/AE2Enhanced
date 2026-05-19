package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.network.PacketUMCAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用内存卡管理 GUI（半透明现代风格）。
 */
public class GuiUniversalMemoryCard extends GuiContainer {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    // 配色
    private static final int COLOR_BG = 0xE02B2B3B;
    private static final int COLOR_BORDER = 0xFF3A8EBF;
    private static final int COLOR_TITLE_BG = 0xE0353549;
    private static final int COLOR_TITLE_LINE = 0xFF3A8EBF;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xAAAAAA;
    private static final int COLOR_TEXT_MUTED = 0x888888;
    private static final int COLOR_SEPARATOR = 0xFF3A8EBF;

    private final EntityPlayer player;
    private boolean hasConfig = false;
    private String configName = "";
    private int upgradeCount = 0;
    private List<ItemUniversalMemoryCard.SelectionEntry> selections = new ArrayList<>();

    public GuiUniversalMemoryCard(EntityPlayer player) {
        super(new com.github.aeddddd.ae2enhanced.container.ContainerUniversalMemoryCard(player));
        this.player = player;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshData();

        this.buttonList.clear();
        this.buttonList.add(new GuiModernButton(0, this.guiLeft + 10, this.guiTop + GUI_HEIGHT - 26, 70, 18,
                I18n.format("gui.ae2enhanced.umc.btn.clear_config")));
        this.buttonList.add(new GuiModernButton(1, this.guiLeft + GUI_WIDTH - 80, this.guiTop + GUI_HEIGHT - 26, 70, 18,
                I18n.format("gui.ae2enhanced.umc.btn.clear_selections")));

        for (int i = 0; i < 5; i++) {
            GuiModernButton btn = new GuiModernButton(2 + i, this.guiLeft + GUI_WIDTH - 24, this.guiTop + 72 + i * 14, 16, 12, "\u00d7");
            btn.visible = i < selections.size();
            this.buttonList.add(btn);
        }
    }

    private void refreshData() {
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof ItemUniversalMemoryCard) {
            hasConfig = ItemUniversalMemoryCard.hasConfig(stack);
            if (hasConfig) {
                NBTTagCompound config = ItemUniversalMemoryCard.getConfig(stack);
                configName = config.getString("name");
                NBTTagCompound data = config.getCompoundTag("data");
                upgradeCount = data.hasKey("ae2e:upgrades") ? data.getTagList("ae2e:upgrades", 10).tagCount() : 0;
            } else {
                configName = "";
                upgradeCount = 0;
            }
            selections = ItemUniversalMemoryCard.getSelections(stack);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_CONFIG, -1));
                break;
            case 1:
                AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_SELECTIONS, -1));
                break;
            default:
                int index = button.id - 2;
                if (index >= 0 && index < selections.size()) {
                    AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.REMOVE_SELECTION, index));
                }
                break;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof ItemUniversalMemoryCard) {
            int currentCount = ItemUniversalMemoryCard.getSelectionCount(stack);
            if (currentCount != selections.size()) {
                this.initGui();
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDefaultBackground();
        refreshData();

        int x = this.guiLeft;
        int y = this.guiTop;

        // 主背景（半透明深色）
        drawRect(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, COLOR_BG);
        // 外边框（浅蓝）
        drawRect(x, y, x + GUI_WIDTH, y + 1, COLOR_BORDER);
        drawRect(x, y + GUI_HEIGHT - 1, x + GUI_WIDTH, y + GUI_HEIGHT, COLOR_BORDER);
        drawRect(x, y, x + 1, y + GUI_HEIGHT, COLOR_BORDER);
        drawRect(x + GUI_WIDTH - 1, y, x + GUI_WIDTH, y + GUI_HEIGHT, COLOR_BORDER);

        // 标题栏背景
        drawRect(x + 1, y + 1, x + GUI_WIDTH - 1, y + 20, COLOR_TITLE_BG);
        // 标题栏底部分隔线
        drawRect(x + 1, y + 20, x + GUI_WIDTH - 1, y + 21, COLOR_TITLE_LINE);
        // 标题文字
        String title = I18n.format("gui.ae2enhanced.umc.title");
        int titleWidth = this.fontRenderer.getStringWidth(title);
        this.fontRenderer.drawString(title, x + (GUI_WIDTH - titleWidth) / 2, y + 6, COLOR_TEXT);

        // 配置区
        if (hasConfig) {
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.source", configName), x + 10, y + 28, COLOR_TEXT);
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.upgrades", upgradeCount), x + 10, y + 40, COLOR_TEXT_DIM);
        } else {
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.no_config"), x + 10, y + 28, COLOR_TEXT_MUTED);
        }

        // 分隔线
        drawRect(x + 10, y + 54, x + GUI_WIDTH - 10, y + 55, COLOR_SEPARATOR);

        // 选取区标题
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.selections", selections.size()), x + 10, y + 60, COLOR_TEXT);

        // 选取列表
        int maxDisplay = Math.min(selections.size(), 5);
        for (int i = 0; i < maxDisplay; i++) {
            ItemUniversalMemoryCard.SelectionEntry entry = selections.get(i);
            String text = entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ();
            if (entry.side >= 0) {
                text += " [P]";
            }
            this.fontRenderer.drawString(text, x + 10, y + 76 + i * 14, COLOR_TEXT_DIM);
        }
        if (selections.size() > 5) {
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.more", selections.size() - 5), x + 10, y + 76 + 5 * 14, COLOR_TEXT_MUTED);
        }

        // 更新按钮可见性
        for (int i = 0; i < 5; i++) {
            if (2 + i < this.buttonList.size()) {
                this.buttonList.get(2 + i).visible = i < selections.size();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    // ============================================================
    // 现代半透明按钮
    // ============================================================

    public static class GuiModernButton extends GuiButton {

        private static final int COLOR_BTN_BG = 0x603A8EBF;
        private static final int COLOR_BTN_BG_HOVER = 0xA04A9EDF;
        private static final int COLOR_BTN_BORDER = 0xFF3A8EBF;
        private static final int COLOR_BTN_BORDER_HOVER = 0xFF80C0FF;
        private static final int COLOR_BTN_TEXT = 0xFFFFFF;
        private static final int COLOR_BTN_TEXT_DISABLED = 0x888888;

        public GuiModernButton(int buttonId, int x, int y, int width, int height, String text) {
            super(buttonId, x, y, width, height, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

            // 背景
            int bgColor = this.hovered ? COLOR_BTN_BG_HOVER : COLOR_BTN_BG;
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

            // 边框
            int borderColor = this.hovered ? COLOR_BTN_BORDER_HOVER : COLOR_BTN_BORDER;
            drawRect(this.x, this.y, this.x + this.width, this.y + 1, borderColor);
            drawRect(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, borderColor);
            drawRect(this.x, this.y, this.x + 1, this.y + this.height, borderColor);
            drawRect(this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, borderColor);

            // 文字
            int textColor = this.enabled ? COLOR_BTN_TEXT : COLOR_BTN_TEXT_DISABLED;
            this.drawCenteredString(mc.fontRenderer, this.displayString, this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
        }
    }
}

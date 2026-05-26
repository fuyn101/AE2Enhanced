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
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用内存卡管理 GUI（半透明现代风格，支持滚动条）。
 */
public class GuiUniversalMemoryCard extends GuiContainer {

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/umc_gui.png");

    private static final int GUI_WIDTH = 220;
    private static final int GUI_HEIGHT = 220;

    private static final int VISIBLE_COUNT = 8;
    private static final int ENTRY_HEIGHT = 14;
    private static final int LIST_Y = 72;
    private static final int LIST_HEIGHT = VISIBLE_COUNT * ENTRY_HEIGHT;

    // 配色（保留文字与滚动条配色，背景和边框由纹理提供）
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xAAAAAA;
    private static final int COLOR_TEXT_MUTED = 0x888888;
    private static final int COLOR_SCROLL_TRACK = 0x40FFFFFF;
    private static final int COLOR_SCROLL_THUMB = 0xFF3A8EBF;

    private final EntityPlayer player;
    private boolean hasConfig = false;
    private String configName = "";
    private int upgradeCount = 0;
    private List<ItemUniversalMemoryCard.SelectionEntry> selections = new ArrayList<>();

    // 滚动条状态
    private int scrollIndex = 0;
    private boolean isDraggingThumb = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

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
        clampScroll();

        this.buttonList.clear();
        this.buttonList.add(new GuiModernButton(0, this.guiLeft + 10, this.guiTop + GUI_HEIGHT - 24, 80, 18,
                I18n.format("gui.ae2enhanced.umc.btn.clear_config")));
        this.buttonList.add(new GuiModernButton(1, this.guiLeft + GUI_WIDTH - 90, this.guiTop + GUI_HEIGHT - 24, 80, 18,
                I18n.format("gui.ae2enhanced.umc.btn.clear_selections")));

        for (int i = 0; i < VISIBLE_COUNT; i++) {
            GuiModernButton btn = new GuiModernButton(2 + i, this.guiLeft + GUI_WIDTH - 38, this.guiTop + LIST_Y + i * ENTRY_HEIGHT - 1, 16, 12, "\u00d7");
            btn.visible = false;
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

    private void clampScroll() {
        if (scrollIndex < 0) scrollIndex = 0;
        int max = Math.max(0, selections.size() - VISIBLE_COUNT);
        if (scrollIndex > max) scrollIndex = max;
    }

    private int getScrollBarX() {
        return this.guiLeft + GUI_WIDTH - 18;
    }

    private int getScrollBarY() {
        return this.guiTop + LIST_Y;
    }

    private int getThumbHeight() {
        if (selections.size() <= VISIBLE_COUNT) return LIST_HEIGHT;
        return Math.max(16, LIST_HEIGHT * VISIBLE_COUNT / selections.size());
    }

    private int getThumbY() {
        int scrollBarY = getScrollBarY();
        if (selections.size() <= VISIBLE_COUNT) return scrollBarY;
        int maxScroll = selections.size() - VISIBLE_COUNT;
        return scrollBarY + scrollIndex * (LIST_HEIGHT - getThumbHeight()) / maxScroll;
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
                int visibleIdx = button.id - 2;
                int actualIndex = scrollIndex + visibleIdx;
                if (actualIndex >= 0 && actualIndex < selections.size()) {
                    AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.REMOVE_SELECTION, actualIndex));
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
            boolean currentHasConfig = ItemUniversalMemoryCard.hasConfig(stack);
            if (currentCount != selections.size() || currentHasConfig != hasConfig) {
                this.initGui();
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = org.lwjgl.input.Mouse.getEventDWheel();
        if (delta != 0) {
            scrollIndex -= Integer.signum(delta);
            clampScroll();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int sbX = getScrollBarX();
        int sbY = getScrollBarY();
        if (mouseX >= sbX && mouseX < sbX + 8 &&
            mouseY >= sbY && mouseY < sbY + LIST_HEIGHT &&
            selections.size() > VISIBLE_COUNT) {
            int thumbY = getThumbY();
            int thumbH = getThumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                isDraggingThumb = true;
                dragStartY = mouseY;
                dragStartScroll = scrollIndex;
            } else if (mouseY < thumbY) {
                scrollIndex -= VISIBLE_COUNT;
                clampScroll();
            } else {
                scrollIndex += VISIBLE_COUNT;
                clampScroll();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (isDraggingThumb && selections.size() > VISIBLE_COUNT) {
            int thumbH = getThumbHeight();
            int maxScroll = selections.size() - VISIBLE_COUNT;
            int deltaPixels = mouseY - dragStartY;
            int deltaSlots = deltaPixels * maxScroll / (LIST_HEIGHT - thumbH);
            scrollIndex = dragStartScroll + deltaSlots;
            clampScroll();
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        isDraggingThumb = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDefaultBackground();
        refreshData();
        clampScroll();

        int x = this.guiLeft;
        int y = this.guiTop;

        // 绘制 GUI 纹理背景（256x256 纹理缩放至 220x220）
        Minecraft.getMinecraft().getTextureManager().bindTexture(GUI_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);

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

        // 选取区标题
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.selections", selections.size()), x + 10, y + 60, COLOR_TEXT);

        // 选取列表
        int maxDisplay = Math.min(selections.size() - scrollIndex, VISIBLE_COUNT);
        for (int i = 0; i < maxDisplay; i++) {
            ItemUniversalMemoryCard.SelectionEntry entry = selections.get(scrollIndex + i);
            String text = entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ();
            if (entry.side >= 0) {
                text += " [P]";
            }
            this.fontRenderer.drawString(text, x + 10, y + LIST_Y + i * ENTRY_HEIGHT, COLOR_TEXT_DIM);
        }

        // 滚动条
        int sbX = getScrollBarX();
        int sbY = getScrollBarY();
        drawRect(sbX, sbY, sbX + 8, sbY + LIST_HEIGHT, COLOR_SCROLL_TRACK);
        if (selections.size() > VISIBLE_COUNT) {
            int thumbY = getThumbY();
            int thumbH = getThumbHeight();
            drawRect(sbX, thumbY, sbX + 8, thumbY + thumbH, COLOR_SCROLL_THUMB);
        }

        // 更新按钮位置和可见性
        for (int i = 0; i < VISIBLE_COUNT; i++) {
            int btnIdx = 2 + i;
            if (btnIdx < this.buttonList.size()) {
                GuiButton btn = this.buttonList.get(btnIdx);
                btn.y = y + LIST_Y + i * ENTRY_HEIGHT - 1;
                btn.visible = i < maxDisplay;
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

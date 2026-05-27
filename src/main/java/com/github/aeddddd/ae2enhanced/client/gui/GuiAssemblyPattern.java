package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyPattern;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;

public class GuiAssemblyPattern extends GuiTechPanel {

    private static final int PREV_BUTTON_ID = 1;
    private static final int NEXT_BUTTON_ID = 2;

    private final TileAssemblyController tile;
    private int page;
    private GuiButtonTech prevButton;
    private GuiButtonTech nextButton;

    public GuiAssemblyPattern(InventoryPlayer playerInv, TileAssemblyController tile, int page, int patternPages) {
        super(new ContainerAssemblyPattern(playerInv, tile, page, patternPages));
        this.xSize = 350;
        this.ySize = 234;
        this.tile = tile;
        this.page = page;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawTechPanelFrame();
        drawInnerPanel(guiLeft + 8, guiTop + 22, guiLeft + 332, guiTop + 146);
        drawInnerPanel(guiLeft + 87, guiTop + 150, guiLeft + 253, guiTop + 208);
        drawInnerPanel(guiLeft + 87, guiTop + 208, guiLeft + 253, guiTop + 230);
        drawSlotBorders(mouseX, mouseY);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int cx = (this.width - this.xSize) / 2;
        int cy = (this.height - this.ySize) / 2;

        this.prevButton = new GuiButtonTech(PREV_BUTTON_ID, cx + 20, cy + 200, 40, 16,
                I18n.format("gui.ae2enhanced.pattern.prev"));
        this.nextButton = new GuiButtonTech(NEXT_BUTTON_ID, cx + 290, cy + 200, 40, 16,
                I18n.format("gui.ae2enhanced.pattern.next"));

        this.buttonList.add(this.prevButton);
        this.buttonList.add(this.nextButton);

        updateButtonStates();
    }

    private void updateButtonStates() {
        int maxPage = tile.getPatternPages() - 1;
        if (this.prevButton != null) {
            this.prevButton.enabled = page > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.enabled = page < maxPage;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateButtonStates();

        int maxPage = tile.getPatternPages() - 1;
        if (page > maxPage && maxPage >= 0) {
            mc.player.openGui(AE2Enhanced.instance, GuiHandler.encodePatternId(maxPage, tile.getPatternPages()),
                mc.world, tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ());
            return;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int maxPage = tile.getPatternPages() - 1;
        if (button.id == PREV_BUTTON_ID) {
            int targetPage = Math.max(0, page - 1);
            AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), targetPage));
        } else if (button.id == NEXT_BUTTON_ID) {
            int targetPage = Math.min(maxPage, page + 1);
            AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), targetPage));
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.pattern.title");
        this.fontRenderer.drawString(title,
                (this.xSize - this.fontRenderer.getStringWidth(title)) / 2, 8, GuiColors.ACCENT);

        String pageStr = I18n.format("gui.ae2enhanced.pattern.page",
                page + 1, tile.getPatternPages());
        this.fontRenderer.drawString(pageStr,
                (this.xSize - this.fontRenderer.getStringWidth(pageStr)) / 2, 200, GuiColors.TEXT_DIM);
    }
}

package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerEMCInterface;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/**
 * EMC 接口 GUI.
 *
 * <p>使用 AE2 存储总线背景纹理,保持与存储总线一致的视觉风格.</p>
 */
public class GuiEMCInterface extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/3.png");

    private final TileEMCInterface tile;
    private final ContainerEMCInterface container;
    private GuiButton prevButton;
    private GuiButton nextButton;

    public GuiEMCInterface(InventoryPlayer playerInventory, ContainerEMCInterface container) {
        super(container);
        this.tile = container.getTile();
        this.container = container;
        this.xSize = 177;
        this.ySize = 251;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.prevButton = new GuiButton(0, this.guiLeft + 120, this.guiTop + 4, 20, 12, "<");
        this.nextButton = new GuiButton(1, this.guiLeft + 150, this.guiTop + 4, 20, 12, ">");
        this.buttonList.add(this.prevButton);
        this.buttonList.add(this.nextButton);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawModalRectWithCustomSizedTexture(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize, 512, 512);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.emc_interface.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        String pageText = String.format("%d/%d", this.container.getCurrentPage() + 1, TileEMCInterface.WHITELIST_PAGES);
        this.fontRenderer.drawString(pageText, 90 - this.fontRenderer.getStringWidth(pageText) / 2, 6, 0x404040);

        String owner = tile.isBound() ? tile.getOwnerName() : I18n.format("gui.ae2enhanced.emc_interface.unbound");
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.emc_interface.owner", owner), 8, 166, 0x404040);

        if (tile.isBound()) {
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.emc_interface.shift_to_bind"), 8, 156, 0xFFAA00);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);
        if (button == this.prevButton) {
            this.container.setCurrentPage(this.container.getCurrentPage() - 1);
        } else if (button == this.nextButton) {
            this.container.setCurrentPage(this.container.getCurrentPage() + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Shift+左键点击标题区域绑定当前玩家
        if (mouseButton == 0 && isShiftKeyDown()
                && mouseX >= this.guiLeft + 8 && mouseX <= this.guiLeft + 100
                && mouseY >= this.guiTop + 6 && mouseY <= this.guiTop + 18) {
            AE2Enhanced.network.sendToServer(
                    new com.github.aeddddd.ae2enhanced.network.packet.PacketEMCInterfaceBind(tile.getPos()));
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}

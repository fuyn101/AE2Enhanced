package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerEMCInterface;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/**
 * EMC 接口 GUI.
 */
public class GuiEMCInterface extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/emc_interface.png");

    private final TileEMCInterface tile;

    public GuiEMCInterface(InventoryPlayer playerInventory, ContainerEMCInterface container) {
        super(container);
        this.tile = container.getTile();
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.emc_interface.title");
        this.fontRenderer.drawString(title, (this.xSize - this.fontRenderer.getStringWidth(title)) / 2, 6, 0x404040);

        String owner = tile.isBound() ? tile.getOwnerName() : I18n.format("gui.ae2enhanced.emc_interface.unbound");
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.emc_interface.owner", owner), 8, 80, 0x404040);

        if (tile.isBound()) {
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.emc_interface.shift_to_bind"), 8, 70, 0xFFAA00);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Shift+左键点击标题区域绑定当前玩家
        if (mouseButton == 0 && isShiftKeyDown()
                && mouseX >= this.guiLeft + 8 && mouseX <= this.guiLeft + 168
                && mouseY >= this.guiTop + 6 && mouseY <= this.guiTop + 18) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.network.sendToServer(
                    new com.github.aeddddd.ae2enhanced.network.packet.PacketEMCInterfaceBind(tile.getPos()));
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}

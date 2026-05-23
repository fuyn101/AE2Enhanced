package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.core.localization.GuiText;
import com.github.aeddddd.ae2enhanced.container.ContainerCentralInterface;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 中枢 ME 接口的 GUI。
 *
 * 复刻 AE2 GuiInterface 的布局和外观。
 */
public class GuiCentralInterface extends GuiUpgradeable {

    private static final ResourceLocation BG_TEXTURE_BASE =
            new ResourceLocation("ae2enhanced", "textures/gui/central_interface.png");

    public GuiCentralInterface(InventoryPlayer inventoryPlayer, TileCentralMEInterface te) {
        super(new ContainerCentralInterface(inventoryPlayer, te));
        this.ySize = 256;
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName("Central ME Interface"), 8, 6, 0x404040);
        this.fontRenderer.drawString(GuiText.Config.getLocal(), 8, 24, 0x404040);
        this.fontRenderer.drawString(GuiText.StoredItems.getLocal(), 8, 73, 0x404040);
        this.fontRenderer.drawString(GuiText.Patterns.getLocal(), 8, 86, 0x404040);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 0x404040);
    }

    @Override
    protected String getBackground() {
        return "guis/central_interface.png";
    }
}

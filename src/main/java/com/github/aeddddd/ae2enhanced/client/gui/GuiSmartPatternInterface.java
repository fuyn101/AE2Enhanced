package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 智能样板接口的客户端 GUI。
 *
 * 占位实现，等待 UV 坐标确认后完善布局和交互。
 */
public class GuiSmartPatternInterface extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("ae2enhanced", "textures/gui/smart_pattern_interface_gui.png");

    private final TileSmartPatternInterface tile;

    public GuiSmartPatternInterface(InventoryPlayer inventoryPlayer, TileSmartPatternInterface tile) {
        super(new ContainerSmartPatternInterface(inventoryPlayer, tile));
        this.tile = tile;
        this.xSize = 176;
        this.ySize = 222; // 占位高度，待 UV 确认后调整
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.smart_pattern_interface.title");
        this.fontRenderer.drawString(title, 8, 6, 0x404040);
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 3, 0x404040);

        // 占位：显示绑定状态
        if (tile.isBound()) {
            String bound = tile.getBoundBlockId();
            this.fontRenderer.drawString(bound, 8, 20, 0x404040);
        }
    }
}

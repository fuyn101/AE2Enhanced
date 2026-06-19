package com.github.aeddddd.ae2enhanced.client.gui;

import ae2.client.gui.implementations.GuiUpgradeable;
import ae2.client.gui.style.GuiStyleManager;
import ae2.core.localization.GuiText;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCollectorConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

import java.io.IOException;

/**
 * 先进 ME 收集器的 GUI.
 *
 * <p>显示当前收集范围,并提供 +/- 按钮调整范围.</p>
 */
public class GuiAdvancedMECollector extends GuiUpgradeable<ContainerAdvancedMECollector> {

    private final ContainerAdvancedMECollector container;
    private GuiButton increaseRangeButton;
    private GuiButton decreaseRangeButton;

    public GuiAdvancedMECollector(InventoryPlayer inventoryPlayer, ContainerAdvancedMECollector container) {
        super(container, inventoryPlayer, new TextComponentTranslation("gui.ae2enhanced.advanced_me_collector.name"),
                GuiStyleManager.loadStyleDoc("/screens/storage_bus.json"));
        this.container = container;
        this.ySize = 251;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.increaseRangeButton = new GuiButton(100, this.guiLeft + 150, this.guiTop + 6, 20, 12, "+");
        this.decreaseRangeButton = new GuiButton(101, this.guiLeft + 128, this.guiTop + 6, 20, 12, "-");
        this.buttonList.add(this.increaseRangeButton);
        this.buttonList.add(this.decreaseRangeButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(new TextComponentTranslation("gui.ae2enhanced.advanced_me_collector.name")).getUnformattedText(), 8, 6, 0x404040);
        this.fontRenderer.drawString(GuiText.Inventory.getLocal(), 8, this.ySize - 96 + 3, 0x404040);

        // 范围显示
        String rangeText = String.format("%d³", this.container.sideLength);
        int rangeTextWidth = this.fontRenderer.getStringWidth(rangeText);
        this.fontRenderer.drawString(rangeText, 120 - rangeTextWidth, 19, 0x404040);
    }

    protected ResourceLocation getBackground() {
        return new ResourceLocation("ae2", "textures/guis/storagebus.png");
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        super.actionPerformed(btn);
        if (btn == this.increaseRangeButton) {
            AE2Enhanced.network.sendToServer(new PacketCollectorConfig(this.container.range + 1));
        } else if (btn == this.decreaseRangeButton) {
            AE2Enhanced.network.sendToServer(new PacketCollectorConfig(this.container.range - 1));
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        this.bindTexture(this.getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 177, this.ySize);
    }
}

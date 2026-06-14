package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/**
 * ME 放置工具配置 GUI。
 */
public class GuiPlacementTool extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AE2Enhanced.MOD_ID,
            "textures/gui/me_placement_tool.png");

    private final EntityPlayer player;
    private final ContainerPlacementTool container;

    public GuiPlacementTool(EntityPlayer player, ContainerPlacementTool container) {
        super(container);
        this.player = player;
        this.container = container;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.placement_tool.title");
        fontRenderer.drawString(title, 8, 6, 0x404040);

        PlacementConfig config = container.getConfig();
        ItemStack selected = config.getStackInSlot(config.getSelectedSlot());
        String info;
        if (!selected.isEmpty()) {
            info = I18n.format("gui.ae2enhanced.placement_tool.selected",
                    selected.getDisplayName(), config.getPlacementCount());
        } else {
            info = I18n.format("gui.ae2enhanced.placement_tool.no_selection");
        }
        fontRenderer.drawString(info, 8, ySize - 94, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEXTURE);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}

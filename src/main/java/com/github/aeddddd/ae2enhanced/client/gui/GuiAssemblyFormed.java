package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyFormed;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class GuiAssemblyFormed extends GuiContainer {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/1.png");

    private final TileAssemblyController tile;
    private GuiButtonTech patternButton;

    public GuiAssemblyFormed(InventoryPlayer playerInv, TileAssemblyController tile) {
        super(new ContainerAssemblyFormed(playerInv, tile));
        this.tile = tile;
        this.xSize = 176;
        this.ySize = 166;
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
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    public void initGui() {
        super.initGui();
        patternButton = new GuiButtonTech(0, guiLeft + 65, guiTop + 46, 110, 20,
            I18n.format("gui.ae2enhanced.formed.open_patterns"));
        buttonList.add(patternButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            AE2Enhanced.network.sendToServer(new PacketPatternPage(tile.getPos(), 0));
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题画在名称栏内 (y=47~70)
        String title = I18n.format("gui.ae2enhanced.formed.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, (xSize - titleWidth) / 2, 51, 0xFFffaa00);

        // 状态信息画在名称栏内
        long parallelCap = tile.getParallelCap();
        String parallelText;
        if (parallelCap >= Long.MAX_VALUE / 2) {
            parallelText = I18n.format("gui.ae2enhanced.formed.parallel.infinite");
        } else {
            parallelText = I18n.format("gui.ae2enhanced.formed.parallel", parallelCap);
        }
        fontRenderer.drawString(parallelText, 12, 61, GuiColors.TEXT_DIM);

        String netStatus;
        int netColor;
        if (tile.isNetworkActive()) {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.active");
            netColor = GuiColors.TEXT_SUCCESS;
        } else if (tile.isNetworkPowered()) {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.booting");
            netColor = GuiColors.TEXT_WARN;
        } else {
            netStatus = I18n.format("gui.ae2enhanced.formed.network.offline");
            netColor = GuiColors.TEXT_ERROR;
        }
        int nw = fontRenderer.getStringWidth(netStatus);
        fontRenderer.drawString(netStatus, xSize - 12 - nw, 61, netColor);

        // 任务数画在格子区右侧空白处 (x=65~170, y=7~42)
        String jobs = I18n.format("gui.ae2enhanced.formed.jobs", tile.getJobCount());
        fontRenderer.drawString(jobs, 65, 15, GuiColors.TEXT_DIM);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (drawCustomTooltips(mouseX, mouseY)) {
            return;
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private boolean drawCustomTooltips(int mouseX, int mouseY) {
        int[][] upgradeSlots = {
            {8, 8}, {26, 8}, {44, 8},
            {8, 26}, {26, 26}, {44, 26}
        };
        String[] upgradeKeys = {
            "item.ae2enhanced.upgrade_card.parallel.name",
            "item.ae2enhanced.upgrade_card.speed.name",
            "item.ae2enhanced.upgrade_card.efficiency.name",
            "item.ae2enhanced.upgrade_card.capacity.name",
            "item.ae2enhanced.upgrade_card.reserved1.name",
            "item.ae2enhanced.upgrade_card.reserved2.name"
        };
        for (int i = 0; i < upgradeSlots.length; i++) {
            int sx = upgradeSlots[i][0];
            int sy = upgradeSlots[i][1];
            if (isPointInRegion(sx, sy, 16, 16, mouseX, mouseY)) {
                List<String> lines = new ArrayList<>();
                net.minecraft.inventory.Slot slot = this.inventorySlots.inventorySlots.get(i);
                boolean installed = slot != null && slot.getHasStack();
                int count = installed ? slot.getStack().getCount() : 0;
                String name = I18n.format(upgradeKeys[i]);
                if (installed) {
                    lines.add("§a● §r" + name);
                    switch (i) {
                        case 0:
                            long parallel = tile.getParallelCap();
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.parallel.status",
                                parallel >= Long.MAX_VALUE / 2 ? "∞" : String.valueOf(parallel)) + "§r");
                            break;
                        case 1:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.speed.status", tile.getCraftingTicks()) + "§r");
                            break;
                        case 2:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.efficiency.status", count) + "§r");
                            break;
                        case 3:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.capacity.status",
                                tile.getPatternPages(), tile.getPatternSlotCount()) + "§r");
                            break;
                        case 4:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.upload.status") + "§r");
                            break;
                        case 5:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.reserved2.status") + "§r");
                            break;
                    }
                } else {
                    lines.add("§7○ §r" + name);
                    switch (i) {
                        case 0:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.parallel.empty") + "§r");
                            break;
                        case 1:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.speed.empty") + "§r");
                            break;
                        case 2:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.efficiency.empty") + "§r");
                            break;
                        case 3:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.capacity.empty") + "§r");
                            break;
                        case 4:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.upload.empty") + "§r");
                            break;
                        case 5:
                            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.upgrades.reserved2.empty") + "§r");
                            break;
                    }
                }
                this.drawHoveringText(lines, mouseX, mouseY);
                return true;
            }
        }
        if (patternButton != null && patternButton.isMouseOver()) {
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("gui.ae2enhanced.tooltip.patterns"));
            lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.patterns.desc") + "§r");
            this.drawHoveringText(lines, mouseX, mouseY);
            return true;
        }
        if (isPointInRegion(65, 46, 110, 20, mouseX, mouseY)) {
            List<String> lines = new ArrayList<>();
            if (tile.isNetworkActive()) {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.active"));
            } else if (tile.isNetworkPowered()) {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.booting"));
                lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.network.booting") + "§r");
            } else {
                lines.add(I18n.format("gui.ae2enhanced.formed.network.offline"));
                lines.add("§7" + I18n.format("gui.ae2enhanced.tooltip.network.offline") + "§r");
            }
            this.drawHoveringText(lines, mouseX, mouseY);
            return true;
        }
        return false;
    }
}

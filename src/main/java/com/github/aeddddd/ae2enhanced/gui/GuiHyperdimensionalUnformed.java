package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.GuiColors;
import com.github.aeddddd.ae2enhanced.container.ContainerHyperdimensionalUnformed;
import com.github.aeddddd.ae2enhanced.network.PacketRequestAssembly;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 超维度仓储中枢未成型状态 GUI。
 * 风格与 GuiAssemblyUnformed 统一。
 */
public class GuiHyperdimensionalUnformed extends GuiContainer {


    private final TileHyperdimensionalController tile;
    private GuiButtonTech assembleButton;
    private Map<Block, Integer> missingMap = new HashMap<>();
    private int refreshTicks = 0;

    public GuiHyperdimensionalUnformed(InventoryPlayer playerInv, TileHyperdimensionalController tile) {
        super(new ContainerHyperdimensionalUnformed(playerInv, tile));
        this.tile = tile;
        this.xSize = 280;
        this.ySize = 260;
    }

    private void refreshMissingMap() {
        this.missingMap = HyperdimensionalStructure.getMissingMap(mc.world, tile.getPos());
    }

    @Override
    public void initGui() {
        super.initGui();
        int centerX = guiLeft + xSize / 2;
        assembleButton = new GuiButtonTech(0, centerX - 80, guiTop + 150, 160, 24, getAssembleButtonText());
        buttonList.add(assembleButton);
        refreshMissingMap();
        updateButtonState();
    }

    private String getAssembleButtonText() {
        if (mc.player.isCreative()) {
            return I18n.format("gui.ae2enhanced.assemble.creative");
        }
        return I18n.format("gui.ae2enhanced.assemble.survival");
    }

    private boolean hasEnoughMaterials() {
        if (missingMap.isEmpty()) return true;
        Map<Block, Integer> needed = new LinkedHashMap<>(missingMap);
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == Item.getItemFromBlock(block)) {
                    int need = entry.getValue();
                    int have = stack.getCount();
                    if (have >= need) {
                        entry.setValue(0);
                    } else {
                        entry.setValue(need - have);
                    }
                    break;
                }
            }
        }
        for (int count : needed.values()) {
            if (count > 0) return false;
        }
        return true;
    }

    private void updateButtonState() {
        if (missingMap.isEmpty()) {
            assembleButton.enabled = true;
            assembleButton.displayString = getAssembleButtonText();
        } else {
            if (mc.player.isCreative()) {
                assembleButton.enabled = true;
            } else {
                boolean hasMaterials = hasEnoughMaterials();
                assembleButton.enabled = hasMaterials;
                assembleButton.displayString = hasMaterials
                        ? getAssembleButtonText()
                        : I18n.format("gui.ae2enhanced.assemble.insufficient");
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // 主背景
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, GuiColors.PANEL_BG);

        // 内面板区域
        drawRect(guiLeft + 10, guiTop + 40, guiLeft + xSize - 10, guiTop + 140, GuiColors.PANEL_LIGHT);

        // 顶部高亮条
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 2, GuiColors.ACCENT);

        // 外边框
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + 1, GuiColors.BORDER_DIM);
        drawRect(guiLeft, guiTop + ySize - 1, guiLeft + xSize, guiTop + ySize, GuiColors.BORDER_DIM);
        drawRect(guiLeft, guiTop, guiLeft + 1, guiTop + ySize, GuiColors.BORDER_DIM);
        drawRect(guiLeft + xSize - 1, guiTop, guiLeft + xSize, guiTop + ySize, GuiColors.BORDER_DIM);

        // 角落装饰
        int corner = 10;
        drawRect(guiLeft, guiTop, guiLeft + corner, guiTop + 2, GuiColors.ACCENT);
        drawRect(guiLeft, guiTop, guiLeft + 2, guiTop + corner, GuiColors.ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop, guiLeft + xSize, guiTop + 2, GuiColors.ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop, guiLeft + xSize, guiTop + corner, GuiColors.ACCENT);
        drawRect(guiLeft, guiTop + ySize - 2, guiLeft + corner, guiTop + ySize, GuiColors.ACCENT);
        drawRect(guiLeft, guiTop + ySize - corner, guiLeft + 2, guiTop + ySize, GuiColors.ACCENT);
        drawRect(guiLeft + xSize - corner, guiTop + ySize - 2, guiLeft + xSize, guiTop + ySize, GuiColors.ACCENT);
        drawRect(guiLeft + xSize - 2, guiTop + ySize - corner, guiLeft + xSize, guiTop + ySize, GuiColors.ACCENT);

        // 内面板边框
        drawRect(guiLeft + 10, guiTop + 40, guiLeft + xSize - 10, guiTop + 41, GuiColors.BORDER_DIM);
        drawRect(guiLeft + 10, guiTop + 139, guiLeft + xSize - 10, guiTop + 140, GuiColors.BORDER_DIM);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 标题
        String title = I18n.format("gui.ae2enhanced.unformed.title");
        int titleWidth = fontRenderer.getStringWidth(title);
        fontRenderer.drawString(title, (xSize - titleWidth) / 2, 12, GuiColors.ACCENT);

        // 副标题
        String subtitle = I18n.format("tile.ae2enhanced.hyperdimensional_controller.name");
        int subWidth = fontRenderer.getStringWidth(subtitle);
        fontRenderer.drawString(subtitle, (xSize - subWidth) / 2, 28, 0xFF88ccdd);

        // 分隔线
        drawRect(16, 36, xSize - 16, 37, GuiColors.ACCENT_SOFT);

        if (missingMap.isEmpty()) {
            String ready = I18n.format("gui.ae2enhanced.unformed.ready");
            int rw = fontRenderer.getStringWidth(ready);
            fontRenderer.drawString(ready, (xSize - rw) / 2, 54, GuiColors.TEXT_SUCCESS);

            String hint = I18n.format("gui.ae2enhanced.unformed.hint");
            int hw = fontRenderer.getStringWidth(hint);
            fontRenderer.drawString(hint, (xSize - hw) / 2, 70, 0xFF88aaaa);
        } else {
            String missingTitle = I18n.format("gui.ae2enhanced.unformed.missing");
            fontRenderer.drawString(missingTitle, 26, 46, GuiColors.TEXT_WARN);

            // 表头
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.unformed.header.material"), 36, 58, 0xFF88aabb);
            fontRenderer.drawString(I18n.format("gui.ae2enhanced.unformed.header.quantity"), xSize - 90, 58, 0xFF88aabb);
            drawRect(30, 70, xSize - 30, 71, GuiColors.BORDER_DIM);

            int y = 76;
            for (Map.Entry<Block, Integer> entry : missingMap.entrySet()) {
                Block block = entry.getKey();
                int count = entry.getValue();
                ItemStack stack = new ItemStack(block, 1);
                String name = stack.getDisplayName();

                fontRenderer.drawString(name, 36, y, GuiColors.TEXT_MAIN);
                String countStr = "x" + count;
                fontRenderer.drawString(countStr, xSize - 36 - fontRenderer.getStringWidth(countStr), y, GuiColors.TEXT_ERROR);
                y += 16;
            }
        }

        // 状态提示
        if (missingMap.isEmpty()) {
            String status = I18n.format("gui.ae2enhanced.unformed.status.ready");
            int sw = fontRenderer.getStringWidth(status);
            fontRenderer.drawString(status, (xSize - sw) / 2, 134, GuiColors.TEXT_SUCCESS);
        } else {
            String status = I18n.format("gui.ae2enhanced.unformed.status.missing");
            int sw = fontRenderer.getStringWidth(status);
            fontRenderer.drawString(status, (xSize - sw) / 2, 134, GuiColors.TEXT_ERROR);
        }

        // 背包上方分隔线
        drawRect(16, 170, xSize - 16, 171, GuiColors.ACCENT_SOFT);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            AE2Enhanced.network.sendToServer(new PacketRequestAssembly(tile.getPos()));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (tile.isFormed()) {
            mc.player.closeScreen();
            return;
        }
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            refreshMissingMap();
            updateButtonState();
        }
    }
}

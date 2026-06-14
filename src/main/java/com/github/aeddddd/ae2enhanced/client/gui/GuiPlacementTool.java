package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerPlacementTool;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementGuiAction;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/**
 * ME 放置工具配置 GUI。
 */
public class GuiPlacementTool extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AE2Enhanced.MOD_ID,
            "textures/gui/me_placement_tool.png");

    private static final int BUTTON_PREV_PAGE = 1;
    private static final int BUTTON_NEXT_PAGE = 2;
    private static final int BUTTON_COUNT_PREV = 3;
    private static final int BUTTON_COUNT_NEXT = 4;

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
        int gx = (width - xSize) / 2;
        int gy = (height - ySize) / 2;

        // 翻页按钮
        addButton(new GuiButton(BUTTON_PREV_PAGE, gx + 120, gy + 17, 20, 20, "<"));
        addButton(new GuiButton(BUTTON_NEXT_PAGE, gx + 145, gy + 17, 20, 20, ">"));

        // 数量切换按钮
        addButton(new GuiButton(BUTTON_COUNT_PREV, gx + 120, gy + 45, 20, 20, "-"));
        addButton(new GuiButton(BUTTON_COUNT_NEXT, gx + 145, gy + 45, 20, 20, "+"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        PacketPlacementGuiAction.Action action = null;
        int value = 0;
        switch (button.id) {
            case BUTTON_PREV_PAGE:
                action = PacketPlacementGuiAction.Action.PAGE_PREV;
                break;
            case BUTTON_NEXT_PAGE:
                action = PacketPlacementGuiAction.Action.PAGE_NEXT;
                break;
            case BUTTON_COUNT_PREV:
                action = PacketPlacementGuiAction.Action.COUNT_PREV;
                break;
            case BUTTON_COUNT_NEXT:
                action = PacketPlacementGuiAction.Action.COUNT_NEXT;
                break;
        }
        if (action != null) {
            AE2Enhanced.network.sendToServer(new PacketPlacementGuiAction(action, value));
        }
        super.actionPerformed(button);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("gui.ae2enhanced.placement_tool.title");
        fontRenderer.drawString(title, 8, 6, 0x404040);

        PlacementConfig config = container.getConfig();
        ItemStack selected = config.getStackInSlot(config.getSelectedSlot());

        String pageText = I18n.format("gui.ae2enhanced.placement_tool.page",
                container.getCurrentPage() + 1, PlacementConfig.MAX_PAGES);
        fontRenderer.drawString(pageText, 120, 40, 0x404040);

        String countText = I18n.format("gui.ae2enhanced.placement_tool.count", config.getPlacementCount());
        fontRenderer.drawString(countText, 120, 68, 0x404040);

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

        // 点击幽灵槽时选择该槽位（容器内槽位索引 0-8 为当前页）
        int pageStart = container.getCurrentPage() * PlacementConfig.SLOTS_PER_PAGE;
        for (int i = 0; i < PlacementConfig.SLOTS_PER_PAGE; i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY)) {
                AE2Enhanced.network.sendToServer(new PacketPlacementGuiAction(
                        PacketPlacementGuiAction.Action.SELECT_SLOT, pageStart + i));
                break;
            }
        }
    }
}

package com.github.aeddddd.ae2enhanced.client.gui.platform;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.platform.ClientPlatformState;
import com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformController;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketSubnetAction;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 先进中枢平台控制器主 GUI。
 *
 * <p>布局说明（基于 advance.png UV 坐标，适配 256px 高度以容纳玩家背包）：</p>
 * <ul>
 *   <li>左侧面板: 子网滚动列表 (x=7, y=8, w=54, h=198)</li>
 *   <li>名称栏: (x=76, y=8, w=71, h=10)</li>
 *   <li>过滤格: 10×5 (x=77, y=22, 间距 18)</li>
 *   <li>选区列表: (x=77, y=118, w=160, ~4 条目)</li>
 *   <li>玩家背包: (x=42, y=174)</li>
 * </ul>
 */
public class GuiAdvancedPlatformController extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("ae2enhanced", "textures/gui/advance.png");

    private static final int GUI_WIDTH = 246;
    private static final int GUI_HEIGHT = 220;

    // 左侧面板
    private static final int LEFT_PANEL_X = 7;
    private static final int LEFT_PANEL_Y = 8;
    private static final int LEFT_PANEL_W = 54;
    private static final int LEFT_PANEL_H = 198;
    private static final int LIST_ITEM_H = 14;

    // 名称栏
    private static final int NAME_BAR_X = 76;
    private static final int NAME_BAR_Y = 8;
    private static final int NAME_BAR_W = 71;
    private static final int NAME_BAR_H = 10;

    // 编辑按钮
    private static final int EDIT_BTN_X = 79;
    private static final int EDIT_BTN_Y = 11;
    private static final int EDIT_BTN_SIZE = 4;

    // IO 切换按钮
    private static final int INPUT_BTN_X = 190;
    private static final int INPUT_BTN_Y = 8;
    private static final int IO_BTN_W = 6;
    private static final int IO_BTN_H = 9;
    private static final int OUTPUT_BTN_X = 209;
    private static final int OUTPUT_BTN_Y = 8;

    // 关闭按钮
    private static final int CLOSE_BTN_X = 225;
    private static final int CLOSE_BTN_Y = 8;
    private static final int CLOSE_BTN_SIZE = 8;

    // 过滤格
    private static final int FILTER_START_X = 77;
    private static final int FILTER_START_Y = 22;
    private static final int FILTER_COLS = 10;
    private static final int FILTER_ROWS = 5;
    private static final int FILTER_SPACING = 18;

    // 加号按钮
    private static final int PLUS_BTN_X = 80;
    private static final int PLUS_BTN_Y = 116;
    private static final int PLUS_BTN_SIZE = 8;

    // 选区列表
    private static final int ZONE_LIST_X = 77;
    private static final int ZONE_LIST_Y = 118;
    private static final int ZONE_LIST_W = 160;
    private static final int ZONE_ENTRY_H = 14;
    private static final int VISIBLE_ZONES = 4;



    private final TileAdvancedPlatformController tile;

    private final List<ClientPlatformState.SubnetData> subnets = new ArrayList<>();
    private final List<ClientPlatformState.ZoneSummary> zones = new ArrayList<>();
    private int selectedSubnetIndex = 0;
    private int selectedSubnetId = 0;
    private int selectedZoneIndex = -1;
    private boolean inputMode = true;
    private int subnetScrollOffset = 0;
    private int zoneScrollOffset = 0;

    public GuiAdvancedPlatformController(InventoryPlayer inventory, TileAdvancedPlatformController tile) {
        super(new ContainerAdvancedPlatformController(inventory, tile));
        this.tile = tile;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;

        ClientPlatformState.PlatformInitData init = ClientPlatformState.getPlatformInit(tile.getPos());
        if (init != null) {
            this.subnets.addAll(init.subnets);
            this.zones.addAll(init.zones);
        }
        if (!this.subnets.isEmpty()) {
            this.selectedSubnetIndex = 0;
            this.selectedSubnetId = this.subnets.get(0).id;
        }

        ContainerAdvancedPlatformController container = (ContainerAdvancedPlatformController) this.inventorySlots;
        container.setSelectedSubnetId(this.selectedSubnetId);
        container.setInputMode(this.inputMode);
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);

        // 绘制主背景
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 名称栏文本
        String name = getSelectedSubnetName();
        int nameW = this.fontRenderer.getStringWidth(name);
        int nameX = NAME_BAR_X + (NAME_BAR_W - nameW) / 2;
        this.fontRenderer.drawString(name, nameX, NAME_BAR_Y + 1, 0x404040);

        // 绘制左侧面板子网列表
        drawSubnetList(relX, relY);

        // 绘制选区列表
        drawZoneList(relX, relY);

        // 绘制 IO 模式指示器
        drawIoModeIndicator();
    }

    private void drawSubnetList(int relX, int relY) {
        int y = LEFT_PANEL_Y;
        int maxVisible = LEFT_PANEL_H / (LIST_ITEM_H + 2);
        for (int i = 0; i < maxVisible; i++) {
            int idx = subnetScrollOffset + i;
            if (idx >= subnets.size()) break;

            boolean selected = (idx == selectedSubnetIndex);
            boolean hovered = relX >= LEFT_PANEL_X && relX < LEFT_PANEL_X + LEFT_PANEL_W
                    && relY >= y && relY < y + LIST_ITEM_H;

            int bgColor = selected ? 0xFF9CD3FF : (hovered ? 0xFFADB0C4 : 0x00FFFFFF);
            if (bgColor != 0x00FFFFFF) {
                drawRect(LEFT_PANEL_X + 1, y, LEFT_PANEL_X + LEFT_PANEL_W - 1, y + LIST_ITEM_H, bgColor);
            }

            String text = subnets.get(idx).name;
            if (text.length() > 8) text = text.substring(0, 7) + "..";
            this.fontRenderer.drawString(text, LEFT_PANEL_X + 2, y + 3, 0x404040);

            y += LIST_ITEM_H + 2;
        }
    }

    private void drawZoneList(int relX, int relY) {
        int y = ZONE_LIST_Y;
        for (int i = 0; i < VISIBLE_ZONES; i++) {
            int idx = zoneScrollOffset + i;
            if (idx >= zones.size()) break;

            ClientPlatformState.ZoneSummary zone = zones.get(idx);
            boolean belongsToSelected = (zone.subnetId == selectedSubnetId);
            boolean selected = (idx == selectedZoneIndex) && belongsToSelected;
            boolean hovered = relX >= ZONE_LIST_X && relX < ZONE_LIST_X + ZONE_LIST_W
                    && relY >= y && relY < y + ZONE_ENTRY_H;

            int bgColor;
            if (selected) {
                bgColor = 0xFF9CD3FF;
            } else if (hovered) {
                bgColor = belongsToSelected ? 0xFFADB0C4 : 0xFF7A7D8C;
            } else {
                bgColor = 0x00FFFFFF;
            }
            if (bgColor != 0x00FFFFFF) {
                drawRect(ZONE_LIST_X, y, ZONE_LIST_X + ZONE_LIST_W, y + ZONE_ENTRY_H, bgColor);
            }

            String text = zone.name;
            if (text.length() > 20) text = text.substring(0, 19) + "..";
            int textColor = belongsToSelected ? 0x404040 : 0xFF7A7A7A;
            this.fontRenderer.drawString(text, ZONE_LIST_X + 2, y + 3, textColor);

            y += ZONE_ENTRY_H + 2;
        }
    }

    private void drawIoModeIndicator() {
        int color = inputMode ? 0xFF3C7FDE : 0xFFDEA83C;
        // 在输入/输出按钮旁绘制小指示点
        int indicatorX = inputMode ? INPUT_BTN_X - 4 : OUTPUT_BTN_X + IO_BTN_W + 2;
        drawRect(indicatorX, INPUT_BTN_Y + 3, indicatorX + 2, INPUT_BTN_Y + 5, color);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 关闭按钮
        if (isInCloseButton(relX, relY) && mouseButton == 0) {
            this.mc.player.closeScreen();
            return;
        }

        // 输入模式切换
        if (isInInputButton(relX, relY) && mouseButton == 0) {
            this.inputMode = true;
            ContainerAdvancedPlatformController container = (ContainerAdvancedPlatformController) this.inventorySlots;
            container.setInputMode(true);
            AE2Enhanced.network.sendToServer(new PacketSubnetAction(
                    PacketSubnetAction.Action.SELECT, tile.getPos(), selectedSubnetId, "", true, Collections.emptyList()));
            return;
        }

        // 输出模式切换
        if (isInOutputButton(relX, relY) && mouseButton == 0) {
            this.inputMode = false;
            ContainerAdvancedPlatformController container = (ContainerAdvancedPlatformController) this.inventorySlots;
            container.setInputMode(false);
            AE2Enhanced.network.sendToServer(new PacketSubnetAction(
                    PacketSubnetAction.Action.SELECT, tile.getPos(), selectedSubnetId, "", false, Collections.emptyList()));
            return;
        }

        // 编辑按钮
        if (isInEditButton(relX, relY) && mouseButton == 0) {
            // TODO: 打开名称编辑
            return;
        }

        // 加号按钮 -> 打开二级菜单
        if (isInPlusButton(relX, relY) && mouseButton == 0) {
            openSubmenu();
            return;
        }

        // 子网列表点击
        int subnetClick = getSubnetAt(relX, relY);
        if (subnetClick >= 0) {
            int idx = subnetClick + subnetScrollOffset;
            if (idx >= 0 && idx < subnets.size()) {
                this.selectedSubnetIndex = idx;
                this.selectedSubnetId = subnets.get(idx).id;
                this.selectedZoneIndex = -1;
                ContainerAdvancedPlatformController container = (ContainerAdvancedPlatformController) this.inventorySlots;
                container.setSelectedSubnetId(this.selectedSubnetId);
                AE2Enhanced.network.sendToServer(new PacketSubnetAction(
                        PacketSubnetAction.Action.SELECT, tile.getPos(), selectedSubnetId, "", inputMode, Collections.emptyList()));
            }
            return;
        }

        // 选区列表点击
        int zoneClick = getZoneAt(relX, relY);
        if (zoneClick >= 0) {
            int idx = zoneClick + zoneScrollOffset;
            if (idx >= 0 && idx < zones.size()) {
                ClientPlatformState.ZoneSummary zone = zones.get(idx);
                if (zone.subnetId == selectedSubnetId) {
                    this.selectedZoneIndex = idx;
                }
            }
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void openSubmenu() {
        if (selectedSubnetId <= 0) return;
        AE2Enhanced.network.sendToServer(new PacketSubnetAction(
                PacketSubnetAction.Action.OPEN_SUBMENU, tile.getPos(), selectedSubnetId, ""));
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        if (isInCloseButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.advanced_platform.close")), mouseX, mouseY);
            return;
        }
        if (isInInputButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.advanced_platform.input_mode")), mouseX, mouseY);
            return;
        }
        if (isInOutputButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.advanced_platform.output_mode")), mouseX, mouseY);
            return;
        }
        if (isInPlusButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(I18n.format("gui.ae2enhanced.advanced_platform.add_zone")), mouseX, mouseY);
            return;
        }

        int zoneClick = getZoneAt(relX, relY);
        if (zoneClick >= 0) {
            int idx = zoneClick + zoneScrollOffset;
            if (idx >= 0 && idx < zones.size()) {
                ClientPlatformState.ZoneSummary zone = zones.get(idx);
                if (zone.subnetId != selectedSubnetId) {
                    drawHoveringText(Collections.singletonList(
                            I18n.format("gui.ae2enhanced.advanced_platform.zone.other_subnet")), mouseX, mouseY);
                    return;
                }
            }
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    // ---- 碰撞检测 ----

    private boolean isInCloseButton(int x, int y) {
        return x >= CLOSE_BTN_X && x < CLOSE_BTN_X + CLOSE_BTN_SIZE
                && y >= CLOSE_BTN_Y && y < CLOSE_BTN_Y + CLOSE_BTN_SIZE;
    }

    private boolean isInInputButton(int x, int y) {
        return x >= INPUT_BTN_X && x < INPUT_BTN_X + IO_BTN_W
                && y >= INPUT_BTN_Y && y < INPUT_BTN_Y + IO_BTN_H;
    }

    private boolean isInOutputButton(int x, int y) {
        return x >= OUTPUT_BTN_X && x < OUTPUT_BTN_X + IO_BTN_W
                && y >= OUTPUT_BTN_Y && y < OUTPUT_BTN_Y + IO_BTN_H;
    }

    private boolean isInEditButton(int x, int y) {
        return x >= EDIT_BTN_X && x < EDIT_BTN_X + EDIT_BTN_SIZE
                && y >= EDIT_BTN_Y && y < EDIT_BTN_Y + EDIT_BTN_SIZE;
    }

    private boolean isInPlusButton(int x, int y) {
        return x >= PLUS_BTN_X && x < PLUS_BTN_X + PLUS_BTN_SIZE
                && y >= PLUS_BTN_Y && y < PLUS_BTN_Y + PLUS_BTN_SIZE;
    }

    private int getSubnetAt(int x, int y) {
        int itemY = LEFT_PANEL_Y;
        int maxVisible = LEFT_PANEL_H / (LIST_ITEM_H + 2);
        for (int i = 0; i < maxVisible; i++) {
            if (x >= LEFT_PANEL_X && x < LEFT_PANEL_X + LEFT_PANEL_W
                    && y >= itemY && y < itemY + LIST_ITEM_H) {
                return i;
            }
            itemY += LIST_ITEM_H + 2;
        }
        return -1;
    }

    private int getZoneAt(int x, int y) {
        int itemY = ZONE_LIST_Y;
        for (int i = 0; i < VISIBLE_ZONES; i++) {
            if (x >= ZONE_LIST_X && x < ZONE_LIST_X + ZONE_LIST_W
                    && y >= itemY && y < itemY + ZONE_ENTRY_H) {
                return i;
            }
            itemY += ZONE_ENTRY_H + 2;
        }
        return -1;
    }

    private String getSelectedSubnetName() {
        if (subnets.isEmpty()) return I18n.format("gui.ae2enhanced.advanced_platform.no_subnet");
        int idx = Math.max(0, Math.min(selectedSubnetIndex, subnets.size() - 1));
        return subnets.get(idx).name;
    }

    public TileAdvancedPlatformController getTile() {
        return tile;
    }
}

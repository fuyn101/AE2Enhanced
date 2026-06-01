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
 * 严格遵循 docs/planning/GUI_Design.md 的 UV 坐标。
 */
public class GuiAdvancedPlatformController extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("ae2enhanced", "textures/gui/advance.png");

    private static final int GUI_WIDTH = 246;
    private static final int GUI_HEIGHT = 220;

    // === 左侧滚动栏: (7,17)→(61,214), 55×198 ===
    private static final int LEFT_PANEL_X = 7;
    private static final int LEFT_PANEL_Y = 17;
    private static final int LEFT_PANEL_W = 55;
    private static final int LEFT_PANEL_H = 198;
    private static final int LIST_ITEM_H = 15;
    private static final int LIST_ITEM_SPACING = 17; // 15+2

    // === 名称栏: (76,17)→(146,26), 71×10 ===
    private static final int NAME_BAR_X = 76;
    private static final int NAME_BAR_Y = 17;
    private static final int NAME_BAR_W = 71;
    private static final int NAME_BAR_H = 10;

    // === 编辑按钮: (79,20)→(82,23), 4×4 ===
    private static final int EDIT_BTN_X = 79;
    private static final int EDIT_BTN_Y = 20;
    private static final int EDIT_BTN_SIZE = 4;

    // === IO 切换按钮 ===
    // 输入按钮(蓝): (190,17)→(193,18), 4×2
    private static final int INPUT_BTN_X = 190;
    private static final int INPUT_BTN_Y = 17;
    private static final int INPUT_BTN_W = 4;
    private static final int INPUT_BTN_H = 2;
    // 输出按钮(黄): (209,24)→(212,25), 4×2
    private static final int OUTPUT_BTN_X = 209;
    private static final int OUTPUT_BTN_Y = 24;
    private static final int OUTPUT_BTN_W = 4;
    private static final int OUTPUT_BTN_H = 2;

    // === 关闭按钮: (225,17)→(232,24), 8×8 ===
    private static final int CLOSE_BTN_X = 225;
    private static final int CLOSE_BTN_Y = 17;
    private static final int CLOSE_BTN_SIZE = 8;

    // === 过滤格区域: (76,35)→(237,124), 162×90 ===
    private static final int FILTER_AREA_X = 76;
    private static final int FILTER_AREA_Y = 35;
    private static final int FILTER_AREA_W = 162;
    private static final int FILTER_AREA_H = 90;

    // === 过滤格槽位: x=77+col×18, y=37+row×18 ===
    private static final int FILTER_START_X = 77;
    private static final int FILTER_START_Y = 37;
    private static final int FILTER_COLS = 10;
    private static final int FILTER_ROWS = 5;
    private static final int FILTER_SPACING = 18;

    // === 加号按钮: (80,130)→(87,137), 8×8 ===
    private static final int PLUS_BTN_X = 80;
    private static final int PLUS_BTN_Y = 130;
    private static final int PLUS_BTN_SIZE = 8;

    // === 选区列表区域: (76,143)→(237,214), 162×72 ===
    private static final int ZONE_AREA_X = 76;
    private static final int ZONE_AREA_Y = 143;
    private static final int ZONE_AREA_W = 162;
    private static final int ZONE_AREA_H = 72;

    // === 选区条目: (77,145)→(236,159), 160×15, 间距18 ===
    private static final int ZONE_LIST_X = 77;
    private static final int ZONE_LIST_Y = 145;
    private static final int ZONE_ENTRY_W = 160;
    private static final int ZONE_ENTRY_H = 15;
    private static final int ZONE_ENTRY_SPACING = 18;
    private static final int VISIBLE_ZONES = 5;

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

        // 主背景: (0,0)→(246,220)
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // 过滤格区域背景: (76,35)→(237,124)
        this.drawTexturedModalRect(this.guiLeft + FILTER_AREA_X, this.guiTop + FILTER_AREA_Y,
                FILTER_AREA_X, FILTER_AREA_Y, FILTER_AREA_W, FILTER_AREA_H);

        // 选区列表区域背景: (76,143)→(237,214)
        this.drawTexturedModalRect(this.guiLeft + ZONE_AREA_X, this.guiTop + ZONE_AREA_Y,
                ZONE_AREA_X, ZONE_AREA_Y, ZONE_AREA_W, ZONE_AREA_H);

        // 左侧列表项 — 纹理复制
        int maxVisible = LEFT_PANEL_H / LIST_ITEM_SPACING;
        for (int i = 0; i < maxVisible; i++) {
            int idx = subnetScrollOffset + i;
            if (idx >= subnets.size()) break;
            int itemY = LEFT_PANEL_Y + i * LIST_ITEM_SPACING;
            if (idx == selectedSubnetIndex) {
                // 高亮项: (9,19)→(59,32), 51×14
                this.drawTexturedModalRect(this.guiLeft + 9, this.guiTop + itemY, 9, 19, 51, 14);
            } else {
                // 标准项: (8,37)→(60,51), 53×15
                this.drawTexturedModalRect(this.guiLeft + 8, this.guiTop + itemY, 8, 37, 53, 15);
            }
        }

        // 名称栏: (76,17)→(146,26), 71×10
        this.drawTexturedModalRect(this.guiLeft + NAME_BAR_X, this.guiTop + NAME_BAR_Y,
                NAME_BAR_X, NAME_BAR_Y, NAME_BAR_W, NAME_BAR_H);

        // 编辑按钮: (79,20)→(82,23), 4×4
        this.drawTexturedModalRect(this.guiLeft + EDIT_BTN_X, this.guiTop + EDIT_BTN_Y,
                EDIT_BTN_X, EDIT_BTN_Y, EDIT_BTN_SIZE, EDIT_BTN_SIZE);

        // 输入按钮: (190,17)→(193,18), 4×2
        this.drawTexturedModalRect(this.guiLeft + INPUT_BTN_X, this.guiTop + INPUT_BTN_Y,
                INPUT_BTN_X, INPUT_BTN_Y, INPUT_BTN_W, INPUT_BTN_H);

        // 输出按钮: (209,24)→(212,25), 4×2
        this.drawTexturedModalRect(this.guiLeft + OUTPUT_BTN_X, this.guiTop + OUTPUT_BTN_Y,
                OUTPUT_BTN_X, OUTPUT_BTN_Y, OUTPUT_BTN_W, OUTPUT_BTN_H);

        // 关闭按钮: (225,17)→(232,24), 8×8
        this.drawTexturedModalRect(this.guiLeft + CLOSE_BTN_X, this.guiTop + CLOSE_BTN_Y,
                CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE);

        // 加号按钮: (80,130)→(87,137), 8×8
        this.drawTexturedModalRect(this.guiLeft + PLUS_BTN_X, this.guiTop + PLUS_BTN_Y,
                PLUS_BTN_X, PLUS_BTN_Y, PLUS_BTN_SIZE, PLUS_BTN_SIZE);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 名称栏文本（居中）
        String name = getSelectedSubnetName();
        int nameW = this.fontRenderer.getStringWidth(name);
        int nameX = NAME_BAR_X + (NAME_BAR_W - nameW) / 2;
        this.fontRenderer.drawString(name, nameX, NAME_BAR_Y + 2, 0x404040);

        // 左侧子网列表文本
        int maxVisible = LEFT_PANEL_H / LIST_ITEM_SPACING;
        for (int i = 0; i < maxVisible; i++) {
            int idx = subnetScrollOffset + i;
            if (idx >= subnets.size()) break;
            int itemY = LEFT_PANEL_Y + i * LIST_ITEM_SPACING;
            String text = subnets.get(idx).name;
            if (text.length() > 8) text = text.substring(0, 7) + "..";
            this.fontRenderer.drawString(text, LEFT_PANEL_X + 4, itemY + 3, 0x404040);
        }

        // 选区列表文本
        for (int i = 0; i < VISIBLE_ZONES; i++) {
            int idx = zoneScrollOffset + i;
            if (idx >= zones.size()) break;
            ClientPlatformState.ZoneSummary zone = zones.get(idx);
            boolean belongsToSelected = (zone.subnetId == selectedSubnetId);
            int itemY = ZONE_LIST_Y + i * ZONE_ENTRY_SPACING;
            String text = zone.name;
            if (text.length() > 20) text = text.substring(0, 19) + "..";
            int textColor = belongsToSelected ? 0x404040 : 0x888888;
            this.fontRenderer.drawString(text, ZONE_LIST_X + 2, itemY + 3, textColor);
        }
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
        return x >= INPUT_BTN_X && x < INPUT_BTN_X + INPUT_BTN_W
                && y >= INPUT_BTN_Y && y < INPUT_BTN_Y + INPUT_BTN_H;
    }

    private boolean isInOutputButton(int x, int y) {
        return x >= OUTPUT_BTN_X && x < OUTPUT_BTN_X + OUTPUT_BTN_W
                && y >= OUTPUT_BTN_Y && y < OUTPUT_BTN_Y + OUTPUT_BTN_H;
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
        int maxVisible = LEFT_PANEL_H / LIST_ITEM_SPACING;
        for (int i = 0; i < maxVisible; i++) {
            if (x >= LEFT_PANEL_X && x < LEFT_PANEL_X + LEFT_PANEL_W
                    && y >= itemY && y < itemY + LIST_ITEM_H) {
                return i;
            }
            itemY += LIST_ITEM_SPACING;
        }
        return -1;
    }

    private int getZoneAt(int x, int y) {
        int itemY = ZONE_LIST_Y;
        for (int i = 0; i < VISIBLE_ZONES; i++) {
            if (x >= ZONE_LIST_X && x < ZONE_LIST_X + ZONE_ENTRY_W
                    && y >= itemY && y < itemY + ZONE_ENTRY_H) {
                return i;
            }
            itemY += ZONE_ENTRY_SPACING;
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

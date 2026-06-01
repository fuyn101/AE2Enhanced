package com.github.aeddddd.ae2enhanced.client.gui.platform;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.platform.ClientPlatformState;
import com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformSubmenu;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketSubnetAction;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketZoneAction;
import com.github.aeddddd.ae2enhanced.platform.zone.FaceIoConfig;
import com.github.aeddddd.ae2enhanced.platform.zone.Zone;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 先进中枢平台控制器二级菜单 GUI。
 *
 * <p>布局说明（基于 advance-2.png UV 坐标，适配 256px 高度）：</p>
 * <ul>
 *   <li>左侧面板: 选区滚动列表 (已绑定 + 未绑定)</li>
 *   <li>名称栏: 当前选区名称</li>
 *   <li>IO 配置槽: 10×5 (x=77, y=22, 间距 18)</li>
 *   <li>6 方向 IO 配置区: (x=76, y=118, w=34, h=34)</li>
 *   <li>玩家背包: (x=42, y=174)</li>
 * </ul>
 */
public class GuiAdvancedPlatformSubmenu extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("ae2enhanced", "textures/gui/advance-2.png");

    private static final int GUI_WIDTH = 246;
    private static final int GUI_HEIGHT = 256;

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

    // 关闭/返回按钮
    private static final int CLOSE_BTN_X = 225;
    private static final int CLOSE_BTN_Y = 8;
    private static final int CLOSE_BTN_SIZE = 8;

    // IO 配置槽
    private static final int IO_START_X = 77;
    private static final int IO_START_Y = 22;
    private static final int IO_COLS = 10;
    private static final int IO_ROWS = 5;
    private static final int IO_SPACING = 18;

    // 6 方向配置区
    private static final int DIR_AREA_X = 76;
    private static final int DIR_AREA_Y = 118;
    private static final int DIR_AREA_SIZE = 34;
    private static final int DIR_SLOT_SIZE = 6;

    // 方向槽位坐标（相对于 GUI 左上角）
    private static final int DIR_UP_X = 90;
    private static final int DIR_UP_Y = 118;
    private static final int DIR_DOWN_X = 90;
    private static final int DIR_DOWN_Y = 144;
    private static final int DIR_LEFT_X = 80;
    private static final int DIR_LEFT_Y = 132;
    private static final int DIR_RIGHT_X = 100;
    private static final int DIR_RIGHT_Y = 132;
    private static final int DIR_FRONT_X = 91;
    private static final int DIR_FRONT_Y = 126;

    // IO 模式循环按钮
    private static final int IO_CYCLE_X = 140;
    private static final int IO_CYCLE_Y = 118;
    private static final int IO_CYCLE_W = 50;
    private static final int IO_CYCLE_H = 12;

    // 玩家背包标签
    private static final int INV_LABEL_X = 42;
    private static final int INV_LABEL_Y = 163;

    private final TileAdvancedPlatformController tile;
    private final int selectedSubnetId;

    private final List<ClientPlatformState.ZoneSummary> boundZones = new ArrayList<>();
    private final List<ClientPlatformState.ZoneSummary> unboundZones = new ArrayList<>();
    private int selectedZoneIndex = -1;
    private int selectedZoneId = 0;
    private int leftScrollOffset = 0;
    private EnumFacing selectedFace = null;
    private FaceIoConfig.IoMode currentFaceMode = FaceIoConfig.IoMode.NONE;

    // 6 个方向的显示名称
    private static final String[] DIR_NAMES = {
        "up", "down", "north", "south", "west", "east"
    };
    private static final EnumFacing[] DIR_FACINGS = {
        EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH,
        EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
    };

    public GuiAdvancedPlatformSubmenu(InventoryPlayer inventory, TileAdvancedPlatformController tile, int selectedSubnetId) {
        super(new ContainerAdvancedPlatformSubmenu(inventory, tile, selectedSubnetId));
        this.tile = tile;
        this.selectedSubnetId = selectedSubnetId;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;

        ClientPlatformState.PlatformInitData init = ClientPlatformState.getPlatformInit(tile.getPos());
        if (init != null) {
            for (ClientPlatformState.ZoneSummary zone : init.zones) {
                if (zone.subnetId == selectedSubnetId) {
                    boundZones.add(zone);
                } else if (zone.subnetId == 0) {
                    unboundZones.add(zone);
                }
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);

        // 绘制主背景（纹理顶部 220px）
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, Math.min(this.ySize, 220));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 名称栏文本
        String name = getSelectedZoneName();
        int nameW = this.fontRenderer.getStringWidth(name);
        int nameX = NAME_BAR_X + (NAME_BAR_W - nameW) / 2;
        this.fontRenderer.drawString(name, nameX, NAME_BAR_Y + 1, 0x404040);

        // 玩家背包标签
        this.fontRenderer.drawString(I18n.format("container.inventory"), INV_LABEL_X, INV_LABEL_Y, 0x404040);

        // 绘制左侧面板选区列表
        drawZoneList(relX, relY);

        // 绘制 6 方向配置区
        drawDirectionConfig(relX, relY);

        // 绘制 IO 模式循环按钮
        drawIoModeCycle(relX, relY);
    }

    private void drawZoneList(int relX, int relY) {
        int y = LEFT_PANEL_Y;
        int visibleCount = 0;
        int maxVisible = LEFT_PANEL_H / (LIST_ITEM_H + 2);

        // 已绑定选区
        for (int i = 0; i < maxVisible && leftScrollOffset + i < boundZones.size(); i++) {
            int idx = leftScrollOffset + i;
            boolean selected = (idx == selectedZoneIndex);
            drawListItem(LEFT_PANEL_X, y, LEFT_PANEL_W, LIST_ITEM_H,
                    boundZones.get(idx).name, selected,
                    relX, relY, 0xFF9CD3FF);
            y += LIST_ITEM_H + 2;
            visibleCount++;
        }

        // 分隔（如有未绑定选区）
        if (!unboundZones.isEmpty() && !boundZones.isEmpty() && visibleCount < maxVisible) {
            drawRect(LEFT_PANEL_X + 2, y, LEFT_PANEL_X + LEFT_PANEL_W - 2, y + 1, 0xFF9A9FB4);
            y += 3;
            visibleCount++;
        }

        // 未绑定选区
        for (int i = 0; i < maxVisible - visibleCount && i < unboundZones.size(); i++) {
            int idx = i;
            drawListItem(LEFT_PANEL_X, y, LEFT_PANEL_W, LIST_ITEM_H,
                    unboundZones.get(idx).name, false,
                    relX, relY, 0xFFADB0C4);
            y += LIST_ITEM_H + 2;
        }
    }

    private void drawListItem(int x, int y, int w, int h, String text,
                              boolean selected, int relX, int relY, int hoverColor) {
        boolean hovered = relX >= x && relX < x + w && relY >= y && relY < y + h;
        int bgColor = selected ? 0xFF9CD3FF : (hovered ? hoverColor : 0x00FFFFFF);
        if (bgColor != 0x00FFFFFF) {
            drawRect(x + 1, y, x + w - 1, y + h, bgColor);
        }
        if (text.length() > 8) text = text.substring(0, 7) + "..";
        this.fontRenderer.drawString(text, x + 2, y + 3, 0x404040);
    }

    private void drawDirectionConfig(int relX, int relY) {
        // 6 方向配置区背景框
        drawRect(DIR_AREA_X, DIR_AREA_Y,
                DIR_AREA_X + DIR_AREA_SIZE, DIR_AREA_Y + DIR_AREA_SIZE,
                0xFF0f3460);
        drawRect(DIR_AREA_X, DIR_AREA_Y,
                DIR_AREA_X + DIR_AREA_SIZE, DIR_AREA_Y + 1, 0xFF0f3460);
        drawRect(DIR_AREA_X, DIR_AREA_Y + DIR_AREA_SIZE - 1,
                DIR_AREA_X + DIR_AREA_SIZE, DIR_AREA_Y + DIR_AREA_SIZE, 0xFF0f3460);

        // 上
        drawDirSlot(DIR_UP_X, DIR_UP_Y, EnumFacing.UP, relX, relY);
        // 下
        drawDirSlot(DIR_DOWN_X, DIR_DOWN_Y, EnumFacing.DOWN, relX, relY);
        // 左
        drawDirSlot(DIR_LEFT_X, DIR_LEFT_Y, EnumFacing.WEST, relX, relY);
        // 右
        drawDirSlot(DIR_RIGHT_X, DIR_RIGHT_Y, EnumFacing.EAST, relX, relY);
        // 前 (中心)
        drawDirSlot(DIR_FRONT_X, DIR_FRONT_Y, EnumFacing.NORTH, relX, relY);

        // TODO: 后 (BACK) 方向槽位 — 当前纹理仅提供 5 个可见位置，需设计确认第 6 个位置
    }

    private void drawDirSlot(int x, int y, EnumFacing face, int relX, int relY) {
        boolean hovered = relX >= x && relX < x + DIR_SLOT_SIZE
                && relY >= y && relY < y + DIR_SLOT_SIZE;
        boolean selected = (face == selectedFace);

        int borderColor = selected ? 0xFF00d4ff : 0xFFFFFFFF;
        int bgColor = hovered ? 0xFFADB0C4 : 0xFF1a1a2e;

        drawRect(x, y, x + DIR_SLOT_SIZE, y + DIR_SLOT_SIZE, borderColor);
        drawRect(x + 1, y + 1, x + DIR_SLOT_SIZE - 1, y + DIR_SLOT_SIZE - 1, bgColor);

        // 绘制方向缩写
        String label = face.name().substring(0, 1);
        int lw = this.fontRenderer.getStringWidth(label);
        this.fontRenderer.drawString(label, x + (DIR_SLOT_SIZE - lw) / 2, y + 1, 0xFFFFFF);

        // 绘制该面的 IO 模式指示器（小点）
        FaceIoConfig.IoMode mode = getFaceMode(face);
        if (mode != FaceIoConfig.IoMode.NONE) {
            int modeColor = getIoModeColor(mode);
            drawRect(x + DIR_SLOT_SIZE + 1, y + 1, x + DIR_SLOT_SIZE + 4, y + 4, modeColor);
        }
    }

    private void drawIoModeCycle(int relX, int relY) {
        boolean hovered = relX >= IO_CYCLE_X && relX < IO_CYCLE_X + IO_CYCLE_W
                && relY >= IO_CYCLE_Y && relY < IO_CYCLE_Y + IO_CYCLE_H;
        int bg = hovered ? 0xFFADB0C4 : 0xFF1a1a2e;
        drawRect(IO_CYCLE_X, IO_CYCLE_Y, IO_CYCLE_X + IO_CYCLE_W, IO_CYCLE_Y + IO_CYCLE_H, 0xFFFFFFFF);
        drawRect(IO_CYCLE_X + 1, IO_CYCLE_Y + 1, IO_CYCLE_X + IO_CYCLE_W - 1, IO_CYCLE_Y + IO_CYCLE_H - 1, bg);

        String label = currentFaceMode.name();
        int color = getIoModeColor(currentFaceMode);
        int lw = this.fontRenderer.getStringWidth(label);
        this.fontRenderer.drawString(label, IO_CYCLE_X + (IO_CYCLE_W - lw) / 2, IO_CYCLE_Y + 2, color);
    }

    private FaceIoConfig.IoMode getFaceMode(EnumFacing face) {
        if (selectedZoneId <= 0 || face == null) return FaceIoConfig.IoMode.NONE;
        Zone zone = tile.getZoneRegistry().getZone(selectedZoneId);
        if (zone == null) return FaceIoConfig.IoMode.NONE;
        FaceIoConfig config = zone.getFaceIo().get(face);
        return config != null ? config.getMode() : FaceIoConfig.IoMode.NONE;
    }

    private int getIoModeColor(FaceIoConfig.IoMode mode) {
        switch (mode) {
            case INPUT: return 0xFF3C7FDE;
            case OUTPUT: return 0xFFDEA83C;
            case BOTH: return 0xFF3CDE5A;
            default: return 0xFF999999;
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

        // 关闭/返回按钮
        if (isInCloseButton(relX, relY) && mouseButton == 0) {
            returnToMainGui();
            return;
        }

        // 编辑按钮
        if (isInEditButton(relX, relY) && mouseButton == 0) {
            // TODO: 打开选区名称编辑
            return;
        }

        // IO 模式循环按钮
        if (isInIoCycleButton(relX, relY) && mouseButton == 0) {
            cycleIoMode();
            return;
        }

        // 选区列表点击
        int zoneClick = getZoneAt(relX, relY);
        if (zoneClick >= 0) {
            handleZoneClick(zoneClick);
            return;
        }

        // 方向槽位点击
        EnumFacing clickedFace = getFaceAt(relX, relY);
        if (clickedFace != null) {
            this.selectedFace = clickedFace;
            updateCurrentFaceMode();
            if (selectedZoneId > 0) {
                AE2Enhanced.network.sendToServer(new PacketZoneAction(
                        PacketZoneAction.Action.SELECT, tile.getPos(), selectedZoneId, selectedSubnetId, selectedFace, null));
            }
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleZoneClick(int zoneClick) {
        int maxVisible = LEFT_PANEL_H / (LIST_ITEM_H + 2);
        int boundVisible = Math.min(boundZones.size() - leftScrollOffset, maxVisible);

        if (zoneClick < boundVisible) {
            // 已绑定选区点击 -> 选中
            int idx = leftScrollOffset + zoneClick;
            if (idx >= 0 && idx < boundZones.size()) {
                this.selectedZoneIndex = idx;
                this.selectedZoneId = boundZones.get(idx).id;
                updateCurrentFaceMode();
                AE2Enhanced.network.sendToServer(new PacketZoneAction(
                        PacketZoneAction.Action.SELECT, tile.getPos(), selectedZoneId, selectedSubnetId, selectedFace, null));
            }
        } else {
            // 未绑定选区点击 -> 绑定到当前子网
            int unboundIdx = zoneClick - boundVisible;
            if (unboundIdx >= 0 && unboundIdx < unboundZones.size()) {
                int zoneId = unboundZones.get(unboundIdx).id;
                AE2Enhanced.network.sendToServer(new PacketZoneAction(
                        PacketZoneAction.Action.ASSIGN, tile.getPos(), zoneId, selectedSubnetId, null, null));
            }
        }
    }

    private void cycleIoMode() {
        if (selectedZoneId <= 0 || selectedFace == null) return;
        FaceIoConfig.IoMode[] modes = FaceIoConfig.IoMode.values();
        int next = (currentFaceMode.ordinal() + 1) % modes.length;
        currentFaceMode = modes[next];
        FaceIoConfig config = new FaceIoConfig();
        config.setMode(currentFaceMode);
        AE2Enhanced.network.sendToServer(new PacketZoneAction(
                PacketZoneAction.Action.IO_CONFIG, tile.getPos(), selectedZoneId, selectedSubnetId, selectedFace, config));
    }

    private void updateCurrentFaceMode() {
        if (selectedZoneId <= 0 || selectedFace == null) {
            currentFaceMode = FaceIoConfig.IoMode.NONE;
            return;
        }
        Zone zone = tile.getZoneRegistry().getZone(selectedZoneId);
        if (zone != null) {
            FaceIoConfig config = zone.getFaceIo().get(selectedFace);
            currentFaceMode = config != null ? config.getMode() : FaceIoConfig.IoMode.NONE;
        } else {
            currentFaceMode = FaceIoConfig.IoMode.NONE;
        }
    }

    private void returnToMainGui() {
        AE2Enhanced.network.sendToServer(new PacketSubnetAction(
                PacketSubnetAction.Action.OPEN_MAIN, tile.getPos(), 0, ""));
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        if (isInCloseButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(
                    I18n.format("gui.ae2enhanced.advanced_platform.back")), mouseX, mouseY);
            return;
        }

        if (isInIoCycleButton(relX, relY)) {
            drawHoveringText(Collections.singletonList(
                    I18n.format("gui.ae2enhanced.advanced_platform.io_mode.cycle")), mouseX, mouseY);
            return;
        }

        EnumFacing face = getFaceAt(relX, relY);
        if (face != null) {
            drawHoveringText(Collections.singletonList(
                    I18n.format("gui.ae2enhanced.advanced_platform.face." + face.getName().toLowerCase())),
                    mouseX, mouseY);
            return;
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    // ---- 碰撞检测 ----

    private boolean isInCloseButton(int x, int y) {
        return x >= CLOSE_BTN_X && x < CLOSE_BTN_X + CLOSE_BTN_SIZE
                && y >= CLOSE_BTN_Y && y < CLOSE_BTN_Y + CLOSE_BTN_SIZE;
    }

    private boolean isInEditButton(int x, int y) {
        return x >= EDIT_BTN_X && x < EDIT_BTN_X + EDIT_BTN_SIZE
                && y >= EDIT_BTN_Y && y < EDIT_BTN_Y + EDIT_BTN_SIZE;
    }

    private boolean isInIoCycleButton(int x, int y) {
        return x >= IO_CYCLE_X && x < IO_CYCLE_X + IO_CYCLE_W
                && y >= IO_CYCLE_Y && y < IO_CYCLE_Y + IO_CYCLE_H;
    }

    private int getZoneAt(int x, int y) {
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

    private EnumFacing getFaceAt(int x, int y) {
        if (x >= DIR_UP_X && x < DIR_UP_X + DIR_SLOT_SIZE
                && y >= DIR_UP_Y && y < DIR_UP_Y + DIR_SLOT_SIZE) {
            return EnumFacing.UP;
        }
        if (x >= DIR_DOWN_X && x < DIR_DOWN_X + DIR_SLOT_SIZE
                && y >= DIR_DOWN_Y && y < DIR_DOWN_Y + DIR_SLOT_SIZE) {
            return EnumFacing.DOWN;
        }
        if (x >= DIR_LEFT_X && x < DIR_LEFT_X + DIR_SLOT_SIZE
                && y >= DIR_LEFT_Y && y < DIR_LEFT_Y + DIR_SLOT_SIZE) {
            return EnumFacing.WEST;
        }
        if (x >= DIR_RIGHT_X && x < DIR_RIGHT_X + DIR_SLOT_SIZE
                && y >= DIR_RIGHT_Y && y < DIR_RIGHT_Y + DIR_SLOT_SIZE) {
            return EnumFacing.EAST;
        }
        if (x >= DIR_FRONT_X && x < DIR_FRONT_X + DIR_SLOT_SIZE
                && y >= DIR_FRONT_Y && y < DIR_FRONT_Y + DIR_SLOT_SIZE) {
            return EnumFacing.NORTH;
        }
        return null;
    }

    private String getSelectedZoneName() {
        if (selectedZoneIndex >= 0 && selectedZoneIndex < boundZones.size()) {
            return boundZones.get(selectedZoneIndex).name;
        }
        return I18n.format("gui.ae2enhanced.advanced_platform.no_zone");
    }

    public TileAdvancedPlatformController getTile() {
        return tile;
    }

    public int getSelectedSubnetId() {
        return selectedSubnetId;
    }
}

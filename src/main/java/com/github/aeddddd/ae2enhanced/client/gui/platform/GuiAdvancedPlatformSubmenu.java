package com.github.aeddddd.ae2enhanced.client.gui.platform;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.platform.ClientPlatformState;
import com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformSubmenu;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketSubnetAction;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketZoneAction;
import com.github.aeddddd.ae2enhanced.platform.zone.FaceIoConfig;
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
 * 严格遵循 docs/planning/GUI_Design.md 的 UV 坐标。
 */
public class GuiAdvancedPlatformSubmenu extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("ae2enhanced", "textures/gui/advance-2.png");

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 224;

    // === 左侧滚动栏: (7,17)→(61,214), 55×198 ===
    private static final int LEFT_PANEL_X = 7;
    private static final int LEFT_PANEL_Y = 17;
    private static final int LEFT_PANEL_W = 55;
    private static final int LEFT_PANEL_H = 198;

    // 列表项: 53×17, 标准(8,36), 高亮(8,18), 间隔18
    private static final int LIST_ITEM_W = 53;
    private static final int LIST_ITEM_H = 17;
    private static final int LIST_ITEM_SPACING = 18;
    private static final int LIST_ITEM_UV_X = 8;
    private static final int LIST_ITEM_UV_Y = 36;
    private static final int LIST_HIGHLIGHT_UV_X = 8;
    private static final int LIST_HIGHLIGHT_UV_Y = 18;

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
    private static final int INPUT_BTN_X = 190;
    private static final int INPUT_BTN_Y = 17;
    private static final int INPUT_BTN_W = 4;
    private static final int INPUT_BTN_H = 2;
    private static final int OUTPUT_BTN_X = 209;
    private static final int OUTPUT_BTN_Y = 24;
    private static final int OUTPUT_BTN_W = 4;
    private static final int OUTPUT_BTN_H = 2;

    // === 关闭/返回按钮: (225,17)→(232,24), 8×8 ===
    private static final int CLOSE_BTN_X = 225;
    private static final int CLOSE_BTN_Y = 17;
    private static final int CLOSE_BTN_SIZE = 8;

    // === 删除按钮(X) — 复用关闭按钮纹理 ===
    private static final int DELETE_BTN_UV_X = 225;
    private static final int DELETE_BTN_UV_Y = 17;
    private static final int DELETE_BTN_SIZE = 8;

    // === IO 配置槽区域: (76,35)→(237,124), 162×90 ===
    private static final int IO_AREA_X = 76;
    private static final int IO_AREA_Y = 35;
    private static final int IO_AREA_W = 162;
    private static final int IO_AREA_H = 90;

    // === 6 方向槽位 (8×8) ===
    // 上(89,132), 左(79,142), 前(89,142), 右(99,142), 下(89,152), 后(99,152)
    private static final int DIR_UP_X = 89;
    private static final int DIR_UP_Y = 132;
    private static final int DIR_DOWN_X = 89;
    private static final int DIR_DOWN_Y = 152;
    private static final int DIR_LEFT_X = 79;
    private static final int DIR_LEFT_Y = 142;
    private static final int DIR_RIGHT_X = 99;
    private static final int DIR_RIGHT_Y = 142;
    private static final int DIR_FRONT_X = 89;
    private static final int DIR_FRONT_Y = 142;
    private static final int DIR_BACK_X = 99;
    private static final int DIR_BACK_Y = 152;
    private static final int DIR_SLOT_SIZE = 8;

    // 方向槽状态纹理源 UV (8×8)
    // BOTH→上槽(89,132), INPUT→左槽(79,142), OUTPUT→前槽(89,142), NONE→右槽(99,142)
    private static final int[] UV_BOTH = {89, 132};
    private static final int[] UV_INPUT = {79, 142};
    private static final int[] UV_OUTPUT = {89, 142};
    private static final int[] UV_NONE = {99, 142};

    private final TileAdvancedPlatformController tile;
    private final int selectedSubnetId;

    private final List<ClientPlatformState.ZoneSummary> boundZones = new ArrayList<>();
    private final List<ClientPlatformState.ZoneSummary> unboundZones = new ArrayList<>();
    private int selectedZoneIndex = -1;
    private int selectedZoneId = 0;
    private int leftScrollOffset = 0;
    private EnumFacing selectedFace = null;

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
                } else if (zone.subnetId == 0 && selectedSubnetId != 0) {
                    unboundZones.add(zone);
                } else if (zone.subnetId != 0 && selectedSubnetId == 0) {
                    // 主网界面：其他子网的 zone 视为未绑定
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
    public void updateScreen() {
        super.updateScreen();
        refreshClientData();
    }

    private void refreshClientData() {
        ClientPlatformState.PlatformInitData init = ClientPlatformState.getPlatformInit(tile.getPos());
        if (init == null) return;

        List<ClientPlatformState.ZoneSummary> newBound = new ArrayList<>();
        List<ClientPlatformState.ZoneSummary> newUnbound = new ArrayList<>();
        for (ClientPlatformState.ZoneSummary zone : init.zones) {
            if (zone.subnetId == selectedSubnetId) {
                newBound.add(zone);
            } else if (zone.subnetId == 0 && selectedSubnetId != 0) {
                newUnbound.add(zone);
            } else if (zone.subnetId != 0 && selectedSubnetId == 0) {
                newUnbound.add(zone);
            }
        }

        boolean changed = newBound.size() != boundZones.size() || newUnbound.size() != unboundZones.size();
        if (!changed) {
            for (int i = 0; i < newBound.size(); i++) {
                ClientPlatformState.ZoneSummary a = newBound.get(i);
                ClientPlatformState.ZoneSummary b = boundZones.get(i);
                if (a.id != b.id || !a.name.equals(b.name) || a.blockCount != b.blockCount) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) {
            for (int i = 0; i < newUnbound.size(); i++) {
                ClientPlatformState.ZoneSummary a = newUnbound.get(i);
                ClientPlatformState.ZoneSummary b = unboundZones.get(i);
                if (a.id != b.id || !a.name.equals(b.name) || a.blockCount != b.blockCount) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) return;

        boundZones.clear();
        boundZones.addAll(newBound);
        unboundZones.clear();
        unboundZones.addAll(newUnbound);

        int newIndex = -1;
        for (int i = 0; i < boundZones.size(); i++) {
            if (boundZones.get(i).id == selectedZoneId) {
                newIndex = i;
                break;
            }
        }
        if (newIndex >= 0) {
            selectedZoneIndex = newIndex;
        } else {
            selectedZoneIndex = -1;
            selectedZoneId = 0;
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);

        // 主背景: (0,0)→(256,224)
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // IO 配置槽区域背景
        this.drawTexturedModalRect(this.guiLeft + IO_AREA_X, this.guiTop + IO_AREA_Y,
                IO_AREA_X, IO_AREA_Y, IO_AREA_W, IO_AREA_H);

        // 左侧列表项
        int maxVisible = LEFT_PANEL_H / LIST_ITEM_SPACING;
        for (int i = 0; i < maxVisible; i++) {
            int idx = leftScrollOffset + i;
            int itemY = LEFT_PANEL_Y + i * LIST_ITEM_SPACING;
            if (idx < boundZones.size()) {
                boolean selected = (idx == selectedZoneIndex);
                int drawY = selected ? itemY + 3 : itemY;
                int srcY = selected ? LIST_HIGHLIGHT_UV_Y : LIST_ITEM_UV_Y;
                this.drawTexturedModalRect(this.guiLeft + LIST_ITEM_UV_X, this.guiTop + drawY,
                        LIST_ITEM_UV_X, srcY, LIST_ITEM_W, LIST_ITEM_H);
            } else if (idx - boundZones.size() < unboundZones.size()) {
                this.drawTexturedModalRect(this.guiLeft + LIST_ITEM_UV_X, this.guiTop + itemY,
                        LIST_ITEM_UV_X, LIST_ITEM_UV_Y, LIST_ITEM_W, LIST_ITEM_H);
            }
        }

        // 名称栏
        this.drawTexturedModalRect(this.guiLeft + NAME_BAR_X, this.guiTop + NAME_BAR_Y,
                NAME_BAR_X, NAME_BAR_Y, NAME_BAR_W, NAME_BAR_H);

        // 编辑按钮
        this.drawTexturedModalRect(this.guiLeft + EDIT_BTN_X, this.guiTop + EDIT_BTN_Y,
                EDIT_BTN_X, EDIT_BTN_Y, EDIT_BTN_SIZE, EDIT_BTN_SIZE);

        // 输入按钮
        this.drawTexturedModalRect(this.guiLeft + INPUT_BTN_X, this.guiTop + INPUT_BTN_Y,
                INPUT_BTN_X, INPUT_BTN_Y, INPUT_BTN_W, INPUT_BTN_H);

        // 输出按钮
        this.drawTexturedModalRect(this.guiLeft + OUTPUT_BTN_X, this.guiTop + OUTPUT_BTN_Y,
                OUTPUT_BTN_X, OUTPUT_BTN_Y, OUTPUT_BTN_W, OUTPUT_BTN_H);

        // 关闭/返回按钮
        this.drawTexturedModalRect(this.guiLeft + CLOSE_BTN_X, this.guiTop + CLOSE_BTN_Y,
                CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE);

        // 6 方向槽位 — 纹理复制
        drawDirSlot(DIR_UP_X, DIR_UP_Y, EnumFacing.UP);
        drawDirSlot(DIR_DOWN_X, DIR_DOWN_Y, EnumFacing.DOWN);
        drawDirSlot(DIR_LEFT_X, DIR_LEFT_Y, EnumFacing.WEST);
        drawDirSlot(DIR_RIGHT_X, DIR_RIGHT_Y, EnumFacing.EAST);
        drawDirSlot(DIR_FRONT_X, DIR_FRONT_Y, EnumFacing.NORTH);
        drawDirSlot(DIR_BACK_X, DIR_BACK_Y, EnumFacing.SOUTH);
    }

    private void drawDirSlot(int destX, int destY, EnumFacing face) {
        FaceIoConfig.IoMode mode = getFaceMode(face);
        int[] uv = getDirSlotUv(mode);
        this.drawTexturedModalRect(this.guiLeft + destX, this.guiTop + destY, uv[0], uv[1], DIR_SLOT_SIZE, DIR_SLOT_SIZE);
    }

    private int[] getDirSlotUv(FaceIoConfig.IoMode mode) {
        switch (mode) {
            case BOTH: return UV_BOTH;
            case INPUT: return UV_INPUT;
            case OUTPUT: return UV_OUTPUT;
            case NONE: default: return UV_NONE;
        }
    }

    private FaceIoConfig.IoMode getFaceMode(EnumFacing face) {
        if (selectedZoneId <= 0 || face == null) return FaceIoConfig.IoMode.NONE;
        com.github.aeddddd.ae2enhanced.platform.zone.Zone zone = tile.getZoneRegistry().getZone(selectedZoneId);
        if (zone == null) return FaceIoConfig.IoMode.NONE;
        com.github.aeddddd.ae2enhanced.platform.zone.FaceIoConfig config = zone.getFaceIo().get(face);
        return config != null ? config.getMode() : FaceIoConfig.IoMode.NONE;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // 名称栏文本
        String name = getSelectedZoneName();
        int nameW = this.fontRenderer.getStringWidth(name);
        int nameX = NAME_BAR_X + (NAME_BAR_W - nameW) / 2;
        this.fontRenderer.drawString(name, nameX, NAME_BAR_Y + 2, 0x404040);

        // 左侧列表文本 + 删除按钮
        int maxVisible = LEFT_PANEL_H / LIST_ITEM_SPACING;
        for (int i = 0; i < maxVisible; i++) {
            int idx = leftScrollOffset + i;
            int itemY = LEFT_PANEL_Y + i * LIST_ITEM_SPACING;
            if (idx < boundZones.size()) {
                boolean selected = (idx == selectedZoneIndex);
                int drawY = selected ? itemY + 3 : itemY;
                String text = boundZones.get(idx).name;
                if (text.length() > 8) text = text.substring(0, 7) + "..";
                this.fontRenderer.drawString(text, LEFT_PANEL_X + 4, drawY + 4, 0x404040);
                // 删除按钮
                int delX = LEFT_PANEL_X + LIST_ITEM_W - DELETE_BTN_SIZE + 1;
                int delY = drawY + 4;
                this.drawTexturedModalRect(delX, delY, DELETE_BTN_UV_X, DELETE_BTN_UV_Y, DELETE_BTN_SIZE, DELETE_BTN_SIZE);
            } else if (idx - boundZones.size() < unboundZones.size()) {
                int unboundIdx = idx - boundZones.size();
                String text = unboundZones.get(unboundIdx).name;
                if (text.length() > 8) text = text.substring(0, 7) + "..";
                this.fontRenderer.drawString(text, LEFT_PANEL_X + 4, itemY + 4, 0x888888);
                // 删除按钮
                int delX = LEFT_PANEL_X + LIST_ITEM_W - DELETE_BTN_SIZE + 1;
                int delY = itemY + 4;
                this.drawTexturedModalRect(delX, delY, DELETE_BTN_UV_X, DELETE_BTN_UV_Y, DELETE_BTN_SIZE, DELETE_BTN_SIZE);
            }
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

        // 选区列表点击
        int zoneClick = getZoneAt(relX, relY);
        if (zoneClick >= 0) {
            int idx = leftScrollOffset + zoneClick;
            boolean isBound = idx < boundZones.size();
            int actualIdx = isBound ? idx : idx - boundZones.size();

            // 删除按钮
            if (isInZoneDeleteButton(relX, relY, zoneClick, isBound)) {
                int zoneId = isBound ? boundZones.get(actualIdx).id : unboundZones.get(actualIdx).id;
                AE2Enhanced.network.sendToServer(new PacketZoneAction(
                        PacketZoneAction.Action.DELETE, tile.getPos(), zoneId, selectedSubnetId, null, null));
                return;
            }

            // 右键 bound zone = 解绑
            if (mouseButton == 1 && isBound) {
                int zoneId = boundZones.get(actualIdx).id;
                AE2Enhanced.network.sendToServer(new PacketZoneAction(
                        PacketZoneAction.Action.ASSIGN, tile.getPos(), zoneId, 0, null, null));
                return;
            }

            if (isBound) {
                this.selectedZoneIndex = actualIdx;
                this.selectedZoneId = boundZones.get(actualIdx).id;
                updateCurrentFaceMode();
                AE2Enhanced.network.sendToServer(new PacketZoneAction(
                        PacketZoneAction.Action.SELECT, tile.getPos(), selectedZoneId, selectedSubnetId, selectedFace, null));
            } else {
                int zoneId = unboundZones.get(actualIdx).id;
                AE2Enhanced.network.sendToServer(new PacketZoneAction(
                        PacketZoneAction.Action.ASSIGN, tile.getPos(), zoneId, selectedSubnetId, null, null));
            }
            return;
        }

        // 方向槽位点击 — 已选中则循环模式，否则选中
        EnumFacing clickedFace = getFaceAt(relX, relY);
        if (clickedFace != null) {
            if (clickedFace == selectedFace && selectedZoneId > 0) {
                cycleIoMode();
            } else {
                this.selectedFace = clickedFace;
                updateCurrentFaceMode();
                if (selectedZoneId > 0) {
                    AE2Enhanced.network.sendToServer(new PacketZoneAction(
                            PacketZoneAction.Action.SELECT, tile.getPos(), selectedZoneId, selectedSubnetId, selectedFace, null));
                }
            }
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void cycleIoMode() {
        if (selectedZoneId <= 0 || selectedFace == null) return;
        FaceIoConfig.IoMode[] modes = FaceIoConfig.IoMode.values();
        FaceIoConfig.IoMode current = getFaceMode(selectedFace);
        int next = (current.ordinal() + 1) % modes.length;
        FaceIoConfig.IoMode newMode = modes[next];
        FaceIoConfig config = new FaceIoConfig();
        config.setMode(newMode);
        AE2Enhanced.network.sendToServer(new PacketZoneAction(
                PacketZoneAction.Action.IO_CONFIG, tile.getPos(), selectedZoneId, selectedSubnetId, selectedFace, config));
    }

    private void updateCurrentFaceMode() {
        // 服务端同步后自动更新，此处无需本地状态缓存
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

        // 方向槽位 tooltip
        EnumFacing face = getFaceAt(relX, relY);
        if (face != null) {
            FaceIoConfig.IoMode mode = getFaceMode(face);
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("gui.ae2enhanced.advanced_platform.face." + getFaceName(face)));
            lines.add(I18n.format("gui.ae2enhanced.advanced_platform.current_mode") + ": " +
                    I18n.format("gui.ae2enhanced.advanced_platform.io_mode." + mode.name().toLowerCase()));
            lines.add(I18n.format("gui.ae2enhanced.advanced_platform.click_to_cycle"));
            drawHoveringText(lines, mouseX, mouseY);
            return;
        }

        // 选区列表 tooltip
        int zoneClick = getZoneAt(relX, relY);
        if (zoneClick >= 0) {
            int idx = leftScrollOffset + zoneClick;
            boolean isBound = idx < boundZones.size();
            int actualIdx = isBound ? idx : idx - boundZones.size();
            ClientPlatformState.ZoneSummary zone = isBound ? boundZones.get(actualIdx) : unboundZones.get(actualIdx);
            if (zone != null) {
                if (isInZoneDeleteButton(relX, relY, zoneClick, isBound)) {
                    drawHoveringText(Collections.singletonList(
                            I18n.format("gui.ae2enhanced.advanced_platform.delete_zone")), mouseX, mouseY);
                    return;
                }
                List<String> lines = new ArrayList<>();
                lines.add(zone.name);
                lines.add(I18n.format("gui.ae2enhanced.advanced_platform.zone.blocks", zone.blockCount));
                if (isBound) {
                    lines.add(I18n.format("gui.ae2enhanced.advanced_platform.zone.right_click_unbind"));
                } else {
                    lines.add(I18n.format("gui.ae2enhanced.advanced_platform.zone.click_to_assign"));
                }
                drawHoveringText(lines, mouseX, mouseY);
                return;
            }
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private String getFaceName(EnumFacing face) {
        switch (face) {
            case UP: return "up";
            case DOWN: return "down";
            case NORTH: return "front";
            case SOUTH: return "back";
            case WEST: return "left";
            case EAST: return "right";
            default: return "unknown";
        }
    }

    // ---- 碰撞检测 ----

    private boolean isInCloseButton(int x, int y) {
        return x >= CLOSE_BTN_X && x < CLOSE_BTN_X + CLOSE_BTN_SIZE
                && y >= CLOSE_BTN_Y && y < CLOSE_BTN_Y + CLOSE_BTN_SIZE;
    }

    private int getZoneAt(int x, int y) {
        int itemY = LEFT_PANEL_Y;
        int maxVisible = LEFT_PANEL_H / LIST_ITEM_SPACING;
        for (int i = 0; i < maxVisible; i++) {
            if (x >= LEFT_PANEL_X && x < LEFT_PANEL_X + LEFT_PANEL_W
                    && y >= itemY && y < itemY + LIST_ITEM_H) {
                int idx = leftScrollOffset + i;
                if (idx < boundZones.size() + unboundZones.size()) {
                    return i;
                }
            }
            itemY += LIST_ITEM_SPACING;
        }
        return -1;
    }

    private boolean isInZoneDeleteButton(int x, int y, int visibleIndex, boolean isBound) {
        int itemY = LEFT_PANEL_Y + visibleIndex * LIST_ITEM_SPACING;
        int drawY = itemY;
        if (isBound) {
            int idx = leftScrollOffset + visibleIndex;
            if (idx == selectedZoneIndex) drawY += 3;
        }
        int delX = LEFT_PANEL_X + LIST_ITEM_W - DELETE_BTN_SIZE + 1;
        int delY = drawY + 4;
        return x >= delX && x < delX + DELETE_BTN_SIZE
                && y >= delY && y < delY + DELETE_BTN_SIZE;
    }

    private EnumFacing getFaceAt(int x, int y) {
        if (x >= DIR_UP_X && x < DIR_UP_X + DIR_SLOT_SIZE
                && y >= DIR_UP_Y && y < DIR_UP_Y + DIR_SLOT_SIZE) return EnumFacing.UP;
        if (x >= DIR_DOWN_X && x < DIR_DOWN_X + DIR_SLOT_SIZE
                && y >= DIR_DOWN_Y && y < DIR_DOWN_Y + DIR_SLOT_SIZE) return EnumFacing.DOWN;
        if (x >= DIR_LEFT_X && x < DIR_LEFT_X + DIR_SLOT_SIZE
                && y >= DIR_LEFT_Y && y < DIR_LEFT_Y + DIR_SLOT_SIZE) return EnumFacing.WEST;
        if (x >= DIR_RIGHT_X && x < DIR_RIGHT_X + DIR_SLOT_SIZE
                && y >= DIR_RIGHT_Y && y < DIR_RIGHT_Y + DIR_SLOT_SIZE) return EnumFacing.EAST;
        if (x >= DIR_FRONT_X && x < DIR_FRONT_X + DIR_SLOT_SIZE
                && y >= DIR_FRONT_Y && y < DIR_FRONT_Y + DIR_SLOT_SIZE) return EnumFacing.NORTH;
        if (x >= DIR_BACK_X && x < DIR_BACK_X + DIR_SLOT_SIZE
                && y >= DIR_BACK_Y && y < DIR_BACK_Y + DIR_SLOT_SIZE) return EnumFacing.SOUTH;
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

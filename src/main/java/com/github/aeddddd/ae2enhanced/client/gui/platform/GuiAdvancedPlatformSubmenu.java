package com.github.aeddddd.ae2enhanced.client.gui.platform;

import com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformSubmenu;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;

import java.io.IOException;
import java.util.ArrayList;
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

    // 玩家背包标签
    private static final int INV_LABEL_X = 42;
    private static final int INV_LABEL_Y = 163;

    private final TileAdvancedPlatformController tile;
    private final int selectedSubnetId;

    // TODO: 客户端状态（待网络同步协议实现后替换）
    private final List<String> boundZoneNames = new ArrayList<>();
    private final List<String> unboundZoneNames = new ArrayList<>();
    private int selectedZoneIndex = -1;
    private int leftScrollOffset = 0;
    private EnumFacing selectedFace = null;

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

        // TODO: 从 Tile 或网络包加载实际数据
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
    }

    private void drawZoneList(int relX, int relY) {
        int y = LEFT_PANEL_Y;
        int visibleCount = 0;
        int maxVisible = LEFT_PANEL_H / (LIST_ITEM_H + 2);

        // 已绑定选区
        for (int i = 0; i < maxVisible && leftScrollOffset + i < boundZoneNames.size(); i++) {
            int idx = leftScrollOffset + i;
            drawListItem(LEFT_PANEL_X, y, LEFT_PANEL_W, LIST_ITEM_H,
                    boundZoneNames.get(idx), idx == selectedZoneIndex,
                    relX, relY, 0xFF9CD3FF);
            y += LIST_ITEM_H + 2;
            visibleCount++;
        }

        // 分隔（如有未绑定选区）
        if (!unboundZoneNames.isEmpty() && !boundZoneNames.isEmpty() && visibleCount < maxVisible) {
            drawRect(LEFT_PANEL_X + 2, y, LEFT_PANEL_X + LEFT_PANEL_W - 2, y + 1, 0xFF9A9FB4);
            y += 3;
            visibleCount++;
        }

        // 未绑定选区
        for (int i = 0; i < maxVisible - visibleCount && i < unboundZoneNames.size(); i++) {
            int idx = i;
            drawListItem(LEFT_PANEL_X, y, LEFT_PANEL_W, LIST_ITEM_H,
                    unboundZoneNames.get(idx), false,
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

        // 选区列表点击
        int zoneClick = getZoneAt(relX, relY);
        if (zoneClick >= 0) {
            this.selectedZoneIndex = zoneClick + leftScrollOffset;
            // TODO: 同步选中选区到服务端
            return;
        }

        // 方向槽位点击
        EnumFacing clickedFace = getFaceAt(relX, relY);
        if (clickedFace != null) {
            this.selectedFace = clickedFace;
            // TODO: 打开该面的详细 IO 配置（模式 / 过滤 / 通道）
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void returnToMainGui() {
        // TODO: 通过发送网络包请求服务端切回主 GUI
        this.mc.player.closeScreen();
        // 完整实现应发送 PacketOpenGui 到服务端，由服务端调用 player.openGui
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        if (isInCloseButton(relX, relY)) {
            drawHoveringText(java.util.Collections.singletonList(
                    I18n.format("gui.ae2enhanced.advanced_platform.back")), mouseX, mouseY);
            return;
        }

        EnumFacing face = getFaceAt(relX, relY);
        if (face != null) {
            drawHoveringText(java.util.Collections.singletonList(
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
        if (selectedZoneIndex >= 0 && selectedZoneIndex < boundZoneNames.size()) {
            return boundZoneNames.get(selectedZoneIndex);
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

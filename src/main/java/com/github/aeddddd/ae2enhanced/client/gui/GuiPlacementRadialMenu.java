package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPreset;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ME 放置工具径向菜单 —— 重做版。
 *
 * 特性：
 * - 显示 N 个已保存预设 + 1 个"选取当前"槽位，最多 9 个扇区。
 * - 不需要把鼠标移到图标上；根据鼠标相对于屏幕中心的角度选中对应扇区。
 * - 松开按键即确认选择。
 */
public class GuiPlacementRadialMenu extends GuiScreen {

    private static final int RADIUS = 70;
    private static final int ITEM_SIZE = 18;
    private static final int DEADZONE = 20; // 中心死区，防止误触

    private final EntityPlayer player;
    private final PlacementConfig config;
    private final int keyCode;

    private List<ItemStack> visibleStacks = new ArrayList<>();
    private boolean isPickSlot = false; // 最后一个扇区是否为"选取当前"
    private int hoveredSector = -1;

    public GuiPlacementRadialMenu(EntityPlayer player, int keyCode) {
        this.player = player;
        this.config = new PlacementConfig(player.getHeldItemMainhand());
        this.keyCode = keyCode;
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshVisibleStacks();
    }

    private void refreshVisibleStacks() {
        visibleStacks.clear();
        for (int i = 0; i < PlacementConfig.MAX_PRESETS; i++) {
            ItemStack s = config.getStackInSlot(i);
            if (!s.isEmpty()) {
                visibleStacks.add(s);
            }
        }
        // 如果未满 9 个，追加一个"选取当前"槽
        isPickSlot = visibleStacks.size() < PlacementConfig.MAX_PRESETS;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int cx = width / 2;
        int cy = height / 2;
        int sectors = getSectorCount();

        drawCenteredString(fontRenderer, I18n.format("gui.ae2enhanced.placement_radial.title"), cx, cy - RADIUS - 24, 0xFFFFFF);

        hoveredSector = getHoveredSector(mouseX, mouseY, cx, cy, sectors);

        // 绘制扇区背景
        for (int i = 0; i < sectors; i++) {
            drawSector(cx, cy, i, sectors, i == hoveredSector);
        }

        // 绘制物品
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < sectors; i++) {
            double angle = getSectorAngle(i, sectors);
            int x = cx + (int) (Math.cos(angle) * RADIUS) - ITEM_SIZE / 2;
            int y = cy - (int) (Math.sin(angle) * RADIUS) - ITEM_SIZE / 2;

            if (i < visibleStacks.size()) {
                ItemStack stack = visibleStacks.get(i);
                itemRender.renderItemAndEffectIntoGUI(stack, x + 1, y + 1);
                itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x + 1, y + 1, null);
            } else {
                // 选取当前槽：显示准星目标物品或 "+"
                ItemStack lookStack = getLookAtStack();
                if (!lookStack.isEmpty()) {
                    itemRender.renderItemAndEffectIntoGUI(lookStack, x + 1, y + 1);
                } else {
                    drawCenteredString(fontRenderer, "+", x + ITEM_SIZE / 2 + 1, y + ITEM_SIZE / 2 - 3, 0xFFFFFF);
                }
            }
        }
        RenderHelper.disableStandardItemLighting();

        // 底部提示
        String hint;
        if (hoveredSector >= 0 && hoveredSector < visibleStacks.size()) {
            hint = visibleStacks.get(hoveredSector).getDisplayName();
        } else if (hoveredSector == visibleStacks.size()) {
            hint = I18n.format("gui.ae2enhanced.placement_radial.pick_current");
        } else {
            hint = I18n.format("gui.ae2enhanced.placement_radial.aim_to_select");
        }
        drawCenteredString(fontRenderer, hint, cx, cy + RADIUS + 16, 0xCCCCCC);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSector(int cx, int cy, int index, int sectors, boolean highlighted) {
        double start = getSectorStartAngle(index, sectors);
        double end = getSectorStartAngle(index + 1, sectors);
        int color = highlighted ? 0x60FFFFFF : 0x30FFFFFF;

        // 简单绘制扇形边界框（用线段近似）
        int steps = 10;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        // 这里仅绘制外弧一小段作为高亮提示；完整扇形可用三角填充，但为简洁用高亮框
        if (highlighted) {
            double angle = getSectorAngle(index, sectors);
            int x = cx + (int) (Math.cos(angle) * RADIUS) - ITEM_SIZE / 2 - 2;
            int y = cy - (int) (Math.sin(angle) * RADIUS) - ITEM_SIZE / 2 - 2;
            drawRect(x, y, x + ITEM_SIZE + 4, y + ITEM_SIZE + 4, 0x80FFFFFF);
        }
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!Keyboard.isKeyDown(keyCode)) {
            confirmSelection();
            mc.player.closeScreen();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        confirmSelection();
        mc.player.closeScreen();
    }

    private void confirmSelection() {
        int sectors = getSectorCount();
        if (hoveredSector < 0 || hoveredSector >= sectors) return;

        int cx = width / 2;
        int cy = height / 2;
        int actualSlot = -1;

        if (hoveredSector < visibleStacks.size()) {
            // 找到这个可见 stack 对应的实际槽位
            actualSlot = findActualSlot(visibleStacks.get(hoveredSector), hoveredSector);
        } else {
            // 选取当前槽
            actualSlot = PlacementConfig.MAX_PRESETS; // 用 MAX_PRESETS 作为特殊标记
        }

        if (actualSlot >= 0) {
            AE2Enhanced.network.sendToServer(new PacketPlacementSelectPreset(actualSlot));
        }
    }

    private int findActualSlot(ItemStack stack, int visibleIndex) {
        // 按顺序匹配实际槽位
        int seen = 0;
        for (int i = 0; i < PlacementConfig.MAX_PRESETS; i++) {
            ItemStack s = config.getStackInSlot(i);
            if (!s.isEmpty()) {
                if (seen == visibleIndex) return i;
                seen++;
            }
        }
        return -1;
    }

    private int getSectorCount() {
        int count = visibleStacks.size();
        if (isPickSlot) count++;
        return Math.max(1, count);
    }

    private int getHoveredSector(int mouseX, int mouseY, int cx, int cy, int sectors) {
        double dx = mouseX - cx;
        double dy = cy - mouseY; // Y 轴向上
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < DEADZONE) return -1;

        double angle = Math.atan2(dy, dx); // 0 右侧，逆时针增加
        if (angle < 0) angle += 2 * Math.PI;

        double sectorSize = 2 * Math.PI / sectors;
        // 第一个扇区在上方（PI/2）
        double startOffset = Math.PI / 2 - sectorSize / 2;
        double adjusted = (angle - startOffset + 2 * Math.PI) % (2 * Math.PI);
        int index = MathHelper.floor(adjusted / sectorSize);
        return MathHelper.clamp(index, 0, sectors - 1);
    }

    private double getSectorStartAngle(int index, int sectors) {
        double sectorSize = 2 * Math.PI / sectors;
        double startOffset = Math.PI / 2 - sectorSize / 2;
        return startOffset + index * sectorSize;
    }

    private double getSectorAngle(int index, int sectors) {
        double sectorSize = 2 * Math.PI / sectors;
        double startOffset = Math.PI / 2 - sectorSize / 2;
        return startOffset + index * sectorSize + sectorSize / 2;
    }

    private ItemStack getLookAtStack() {
        RayTraceResult ray = player.rayTrace(5.0, 1.0f);
        if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) return ItemStack.EMPTY;
        net.minecraft.block.state.IBlockState state = player.world.getBlockState(ray.getBlockPos());
        net.minecraft.item.ItemStack pick = state.getBlock().getItem(player.world, ray.getBlockPos(), state);
        return pick == null ? ItemStack.EMPTY : pick;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

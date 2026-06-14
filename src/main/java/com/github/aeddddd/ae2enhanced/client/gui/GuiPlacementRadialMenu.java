package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementGuiAction;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * ME 放置工具径向菜单 —— 按住键时显示 18 个槽位的圆形选择菜单。
 */
public class GuiPlacementRadialMenu extends GuiScreen {

    private final EntityPlayer player;
    private final PlacementConfig config;
    private final int keyCode;
    private int hoveredSlot = -1;

    private static final int RADIUS = 80;
    private static final int ITEM_SIZE = 20;

    public GuiPlacementRadialMenu(EntityPlayer player, int keyCode) {
        this.player = player;
        this.config = new PlacementConfig(player.getHeldItemMainhand());
        this.keyCode = keyCode;
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int cx = width / 2;
        int cy = height / 2;

        // 绘制标题
        drawCenteredString(fontRenderer, I18n.format("gui.ae2enhanced.placement_radial.title"), cx, cy - RADIUS - 20, 0xFFFFFF);

        // 绘制数量信息
        String countText = I18n.format("gui.ae2enhanced.placement_radial.count", config.getPlacementCount());
        drawCenteredString(fontRenderer, countText, cx, cy + RADIUS + 10, 0xFFFFFF);

        // 计算悬停槽位
        hoveredSlot = getHoveredSlot(mouseX, mouseY, cx, cy);

        // 绘制每个槽位
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < PlacementConfig.TOTAL_SLOTS; i++) {
            double angle = Math.PI / 2 - i * (2 * Math.PI / PlacementConfig.TOTAL_SLOTS);
            int x = cx + (int) (Math.cos(angle) * RADIUS) - ITEM_SIZE / 2;
            int y = cy - (int) (Math.sin(angle) * RADIUS) - ITEM_SIZE / 2;

            // 高亮悬停项或当前选中项
            if (i == hoveredSlot || i == config.getSelectedSlot()) {
                drawRect(x - 2, y - 2, x + ITEM_SIZE + 2, y + ITEM_SIZE + 2,
                        i == hoveredSlot ? 0x80FFFFFF : 0x80FFFF00);
            }

            ItemStack stack = config.getStackInSlot(i);
            if (!stack.isEmpty()) {
                itemRender.renderItemAndEffectIntoGUI(stack, x + 2, y + 2);
                itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x + 2, y + 2, null);
            } else {
                drawRect(x + 2, y + 2, x + ITEM_SIZE - 2, y + ITEM_SIZE - 2, 0x40FFFFFF);
            }
        }
        RenderHelper.disableStandardItemLighting();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!Keyboard.isKeyDown(keyCode)) {
            if (hoveredSlot >= 0 && hoveredSlot < PlacementConfig.TOTAL_SLOTS) {
                AE2Enhanced.network.sendToServer(new PacketPlacementGuiAction(
                        PacketPlacementGuiAction.Action.SELECT_SLOT, hoveredSlot));
            }
            mc.player.closeScreen();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int cx = width / 2;
        int cy = height / 2;
        int hovered = getHoveredSlot(mouseX, mouseY, cx, cy);
        if (hovered >= 0 && hovered < PlacementConfig.TOTAL_SLOTS) {
            AE2Enhanced.network.sendToServer(new PacketPlacementGuiAction(
                    PacketPlacementGuiAction.Action.SELECT_SLOT, hovered));
        }
        mc.player.closeScreen();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int getHoveredSlot(int mouseX, int mouseY, int cx, int cy) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < RADIUS - ITEM_SIZE || dist > RADIUS + ITEM_SIZE) {
            return -1;
        }
        double angle = Math.atan2(-dy, dx); // 0 为右侧，逆时针增加
        if (angle < 0) angle += 2 * Math.PI;
        // 起始项在上方（PI/2），转换为索引
        double startAngle = Math.PI / 2;
        double adjusted = (startAngle - angle + 2 * Math.PI) % (2 * Math.PI);
        int index = MathHelper.floor(adjusted / (2 * Math.PI / PlacementConfig.TOTAL_SLOTS));
        if (index < 0) index = 0;
        if (index >= PlacementConfig.TOTAL_SLOTS) index = PlacementConfig.TOTAL_SLOTS - 1;
        return index;
    }
}

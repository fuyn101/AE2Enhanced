package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.network.PacketUMCAction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用内存卡管理 GUI（自绘制浅灰+浅蓝 AE2 风格）。
 */
public class GuiUniversalMemoryCard extends GuiContainer {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private final EntityPlayer player;
    private boolean hasConfig = false;
    private String configName = "";
    private int upgradeCount = 0;
    private List<ItemUniversalMemoryCard.SelectionEntry> selections = new ArrayList<>();

    public GuiUniversalMemoryCard(EntityPlayer player) {
        super(new com.github.aeddddd.ae2enhanced.container.ContainerUniversalMemoryCard(player));
        this.player = player;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshData();

        this.buttonList.clear();
        // 清除配置按钮
        this.buttonList.add(new GuiButton(0, this.guiLeft + 10, this.guiTop + GUI_HEIGHT - 24, 70, 18, "\u6e05\u9664\u914d\u7f6e"));
        // 清除选取按钮
        this.buttonList.add(new GuiButton(1, this.guiLeft + GUI_WIDTH - 80, this.guiTop + GUI_HEIGHT - 24, 70, 18, "\u6e05\u9664\u9009\u53d6"));

        // 选取列表的删除按钮（最多 5 个）
        for (int i = 0; i < 5; i++) {
            GuiButton btn = new GuiButton(2 + i, this.guiLeft + GUI_WIDTH - 24, this.guiTop + 72 + i * 14, 16, 12, "\u00d7");
            btn.visible = i < selections.size();
            this.buttonList.add(btn);
        }
    }

    private void refreshData() {
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof ItemUniversalMemoryCard) {
            hasConfig = ItemUniversalMemoryCard.hasConfig(stack);
            if (hasConfig) {
                NBTTagCompound config = ItemUniversalMemoryCard.getConfig(stack);
                configName = config.getString("name");
                NBTTagCompound data = config.getCompoundTag("data");
                upgradeCount = data.hasKey("ae2e:upgrades") ? data.getTagList("ae2e:upgrades", 10).tagCount() : 0;
            } else {
                configName = "";
                upgradeCount = 0;
            }
            selections = ItemUniversalMemoryCard.getSelections(stack);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_CONFIG, -1));
                break;
            case 1:
                AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_SELECTIONS, -1));
                break;
            default:
                int index = button.id - 2;
                if (index >= 0 && index < selections.size()) {
                    AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.REMOVE_SELECTION, index));
                }
                break;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 如果选取数量变化，重新初始化按钮
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof ItemUniversalMemoryCard) {
            int currentCount = ItemUniversalMemoryCard.getSelectionCount(stack);
            if (currentCount != selections.size()) {
                this.initGui();
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDefaultBackground();
        refreshData();

        int x = this.guiLeft;
        int y = this.guiTop;

        // 主背景 - 浅灰
        drawRect(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFFC0C0C0);
        // 边框 - 浅蓝
        drawRect(x, y, x + GUI_WIDTH, y + 1, 0xFF3A8EBF);
        drawRect(x, y + GUI_HEIGHT - 1, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFF3A8EBF);
        drawRect(x, y, x + 1, y + GUI_HEIGHT, 0xFF3A8EBF);
        drawRect(x + GUI_WIDTH - 1, y, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFF3A8EBF);

        // 标题栏背景 - 稍深
        drawRect(x + 1, y + 1, x + GUI_WIDTH - 1, y + 18, 0xFFA0A0A0);
        // 标题
        String title = "\u901a\u7528\u5185\u5b58\u5361";
        int titleWidth = this.fontRenderer.getStringWidth(title);
        this.fontRenderer.drawString(title, x + (GUI_WIDTH - titleWidth) / 2, y + 5, 0xFFFFFF);

        // 配置区
        if (hasConfig) {
            this.fontRenderer.drawString("\u6765\u6e90: " + configName, x + 8, y + 24, 0x333333);
            this.fontRenderer.drawString("\u5347\u7ea7: " + upgradeCount + " \u79cd", x + 8, y + 36, 0x333333);
        } else {
            this.fontRenderer.drawString("\u65e0\u914d\u7f6e", x + 8, y + 24, 0x888888);
            this.fontRenderer.drawString("", x + 8, y + 36, 0x888888);
        }

        // 分隔线
        drawRect(x + 8, y + 50, x + GUI_WIDTH - 8, y + 51, 0xFF808080);

        // 选取区标题
        this.fontRenderer.drawString("\u5df2\u9009\u53d6 " + selections.size() + " \u4e2a\u76ee\u6807", x + 8, y + 56, 0x333333);

        // 选取列表
        int maxDisplay = Math.min(selections.size(), 5);
        for (int i = 0; i < maxDisplay; i++) {
            ItemUniversalMemoryCard.SelectionEntry entry = selections.get(i);
            String text = entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ();
            if (entry.side >= 0) {
                text += " [P]";
            }
            this.fontRenderer.drawString(text, x + 8, y + 72 + i * 14, 0x555555);
        }
        if (selections.size() > 5) {
            this.fontRenderer.drawString("... \u7b49 " + (selections.size() - 5) + " \u4e2a", x + 8, y + 72 + 5 * 14, 0x888888);
        }

        // 更新按钮可见性
        for (int i = 0; i < 5; i++) {
            if (2 + i < this.buttonList.size()) {
                this.buttonList.get(2 + i).visible = i < selections.size();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }
}

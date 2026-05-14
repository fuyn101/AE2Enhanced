package com.github.aeddddd.ae2enhanced.gui;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.ITooltip;
import appeng.container.slot.SlotFake;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.container.ContainerStockingBus;
import com.github.aeddddd.ae2enhanced.network.PacketStockingBusConfig;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GuiStockingBus extends GuiUpgradeable {

    private final ContainerStockingBus container;
    private GuiButton modeButton;

    private static final String[] MODE_ABBREVS = { "BID", "SUP", "REC" };
    private static final String[] MODE_NAMES = {
        "Bidirectional", "Supply Only", "Recover Only"
    };
    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2", "textures/guis/states.png");

    // 槽位坐标：中心(80,40) + 8方向偏移
    private static final int[] SLOT_X = { 80, 62, 98, 80, 80, 62, 98, 62, 98 };
    private static final int[] SLOT_Y = { 40, 40, 40, 22, 58, 22, 22, 58, 58 };

    public GuiStockingBus(InventoryPlayer inventoryPlayer, PartStockingBus te) {
        super(new ContainerStockingBus(inventoryPlayer, te));
        this.container = (ContainerStockingBus) this.cvb;
    }

    @Override
    protected void addButtons() {
        super.addButtons();
        this.modeButton = new ModeButton();
        this.buttonList.add(this.modeButton);
    }

    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        super.func_73863_a(mouseX, mouseY, partialTicks);
        // 在 slot 绘制完成后绘制目标数量，避免与 Minecraft 默认数量重叠
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.guiLeft, this.guiTop, 0);
        this.drawTargetAmounts();
        GlStateManager.popMatrix();
    }

    private void drawTargetAmounts() {
        for (int i = 0; i < 9; i++) {
            int amount = this.container.getTargetAmount(i);
            if (amount > 1) {
                String text;
                if (amount >= 1000000) {
                    text = (amount / 1000000) + "M";
                } else if (amount >= 1000) {
                    text = (amount / 1000) + "k";
                } else {
                    text = String.valueOf(amount);
                }
                int sx = SLOT_X[i];
                int sy = SLOT_Y[i];
                int tw = this.fontRenderer.getStringWidth(text);
                int x = sx + 19 - tw;
                int y = sy + 10;
                // 绘制灰色背景覆盖默认的物品数量显示区域
                drawRect(x - 1, y - 1, x + tw + 1, y + 8, 0xFF555555);
                this.fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
            }
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName("Stocking Bus"), 8, 6, 0x404040);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 0x404040);

        if (this.redstoneMode != null) {
            this.redstoneMode.set(this.cvb.getRedStoneMode());
        }
        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }
        if (this.craftMode != null) {
            this.craftMode.set(this.cvb.getCraftingMode());
        }
        if (this.schedulingMode != null) {
            this.schedulingMode.set(this.cvb.getSchedulingMode());
        }
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();
        if (this.modeButton != null) {
            this.modeButton.visible = true;
        }
        if (this.schedulingMode != null) {
            this.schedulingMode.setVisibility(this.bc.getInstalledUpgrades(appeng.api.config.Upgrades.CAPACITY) > 0);
        }
    }

    @Override
    protected String getBackground() {
        return "guis/bus.png";
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        super.actionPerformed(btn);
        if (btn == this.modeButton) {
            boolean backwards = Mouse.isButtonDown(1);
            int newMode = this.container.modeOrdinal + (backwards ? -1 : 1);
            if (newMode < 0) newMode = PartStockingBus.StockingMode.values().length - 1;
            if (newMode >= PartStockingBus.StockingMode.values().length) newMode = 0;
            AE2Enhanced.network.sendToServer(new PacketStockingBusConfig(-1, newMode));
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

            for (int i = 0; i < 9; i++) {
                int slotX = this.guiLeft + SLOT_X[i];
                int slotY = this.guiTop + SLOT_Y[i];
                if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                    int delta = wheel > 0 ? 1 : -1;
                    if (isShiftKeyDown()) delta *= 10;
                    if (isCtrlKeyDown()) delta *= 100;

                    long current = this.container.getTargetAmount(i);
                    long newAmount = Math.max(0, current + delta);
                    this.container.setTargetAmount(i, (int) newAmount);
                    AE2Enhanced.network.sendToServer(new PacketStockingBusConfig(i, newAmount));
                    return;
                }
            }
        }
        super.handleMouseInput();
    }

    private class ModeButton extends GuiButton implements ITooltip {
        ModeButton() {
            super(100, GuiStockingBus.this.guiLeft - 18, GuiStockingBus.this.guiTop + 88, 16, 16, "");
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int state = this.getHoverState(this.hovered);

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(STATES_TEXTURE);
            GuiStockingBus.this.drawTexturedModalRect(this.x, this.y, 240, 240, 16, 16);

            int modeIdx = container.modeOrdinal;
            if (modeIdx >= 0 && modeIdx < MODE_ABBREVS.length) {
                String text = MODE_ABBREVS[modeIdx];
                int textColor = (state == 2) ? 0xFFFFA0 : 0xFFFFFF;
                mc.fontRenderer.drawStringWithShadow(text,
                        this.x + (this.width - mc.fontRenderer.getStringWidth(text)) / 2,
                        this.y + 4, textColor);
            }
        }

        @Override
        public String getMessage() {
            int modeIdx = container.modeOrdinal;
            if (modeIdx >= 0 && modeIdx < MODE_NAMES.length) {
                return "Mode: " + MODE_NAMES[modeIdx];
            }
            return "Mode";
        }

        @Override public int xPos() { return this.x; }
        @Override public int yPos() { return this.y; }
        @Override public int getWidth() { return this.width; }
        @Override public int getHeight() { return this.height; }
        @Override public boolean isVisible() { return this.visible; }
    }

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        this.mapTargetSlot.clear();

        boolean isItem = ingredient instanceof ItemStack;
        boolean isFluid = ingredient instanceof FluidStack;
        boolean isGas = false;
        if (!isItem && !isFluid) {
            try {
                isGas = ingredient.getClass().getName().equals("mekanism.api.gas.GasStack");
            } catch (Exception ignored) {
            }
        }

        if (!isItem && !isFluid && !isGas) {
            return Collections.emptyList();
        }

        ArrayList<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof appeng.container.slot.SlotFake) || !((appeng.container.slot.SlotFake) slot).isSlotEnabled()) continue;
            GhostIngredientTarget target = new GhostIngredientTarget(this.getGuiLeft(), this.getGuiTop(), slot);
            targets.add(target);
            this.mapTargetSlot.putIfAbsent(target, slot);
        }
        return targets;
    }
}

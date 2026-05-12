package com.github.aeddddd.ae2enhanced.gui;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.slot.SlotFake;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.GuiBusModeButton;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.container.ContainerUniversalImportBus;
import com.github.aeddddd.ae2enhanced.network.PacketUniversalBusConfig;
import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * E1a：通用输入总线的 GUI。
 * 继承 GuiUpgradeable，左侧添加模式切换按钮。
 */
public class GuiUniversalImportBus extends GuiUpgradeable {

    private final ContainerUniversalImportBus container;
    private GuiBusModeButton busModeButton;

    public GuiUniversalImportBus(InventoryPlayer inventoryPlayer, PartUniversalImportBus te) {
        super(new ContainerUniversalImportBus(inventoryPlayer, te));
        this.container = (ContainerUniversalImportBus) this.cvb;
        this.ySize = 251;
    }

    @Override
    protected void addButtons() {
        super.addButtons();
        // 模式按钮放在左侧最下方
        this.busModeButton = new GuiBusModeButton(this.guiLeft - 18, this.guiTop + 88);
        this.buttonList.add(this.busModeButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName("Universal Import Bus"), 8, 6, 0x404040);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 0x404040);

        // 同步 AE2 原生按钮状态
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

        // 同步模式按钮状态
        if (this.busModeButton != null) {
            this.busModeButton.setMode(this.container.busModeOrdinal);
        }
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();
        if (this.busModeButton != null) {
            this.busModeButton.visible = true; // 模式按钮始终可见
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.handleButtonVisibility();
        this.bindTexture(this.getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 177, this.ySize);
        if (this.drawUpgrades()) {
            this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, 14 + 4 * 18);
        }
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + 178, offsetY + this.ySize - 90, 178, this.ySize - 90, 68, 68);
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png"; // 复用存储总线背景（更大，适合 63 槽）
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        super.actionPerformed(btn);
        if (btn == this.busModeButton) {
            boolean backwards = Mouse.isButtonDown(1);
            int newMode = this.busModeButton.getModeIndex() + (backwards ? -1 : 1);
            if (newMode < 0) newMode = PartUniversalImportBus.BusMode.values().length - 1;
            if (newMode >= PartUniversalImportBus.BusMode.values().length) newMode = 0;
            AE2Enhanced.network.sendToServer(new PacketUniversalBusConfig(newMode));
        }
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
            if (!(slot instanceof SlotFake) || !((SlotFake) slot).isSlotEnabled()) continue;
            GhostIngredientTarget target = new GhostIngredientTarget(this.getGuiLeft(), this.getGuiTop(), slot);
            targets.add(target);
            this.mapTargetSlot.putIfAbsent(target, slot);
        }
        return targets;
    }
}

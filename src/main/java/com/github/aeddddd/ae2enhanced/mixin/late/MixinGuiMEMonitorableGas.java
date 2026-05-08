package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * E2a：在标准 AE2 物品终端中渲染气体假物品的 tooltip。
 * 本 mixin 位于 mixins.ae2enhanced.late.gas.json 中，条件加载。
 */
@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 1098)
public class MixinGuiMEMonitorableGas {

    @Inject(method = "renderHoveredToolTip", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onRenderTooltipGas(int mouseX, int mouseY, CallbackInfo ci) {
        if (com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED) return;
        GuiContainer screen = (GuiContainer) (Object) this;
        Slot slot = screen.getSlotUnderMouse();
        if (!(slot instanceof SlotME) || !slot.getHasStack()) {
            return;
        }

        ItemStack stack = slot.getStack();
        if (!ItemGasDrop.isGasDrop(stack)) {
            return;
        }

        String gasName = ItemGasDrop.getGasName(stack);
        String name = gasName != null ? gasName : "Unknown Gas";
        List<String> tooltip = new ArrayList<>();
        tooltip.add("\u00A7b" + "Gas: " + name);

        long amount = getAEStackSize(slot, stack);
        tooltip.add("\u00A77" + "Amount: " + amount);

        screen.drawHoveringText(tooltip, mouseX, mouseY);
        ci.cancel();
    }

    private static long getAEStackSize(Slot slot, ItemStack stack) {
        if (slot instanceof SlotME) {
            IAEItemStack aeStack = ((SlotME) slot).getAEStack();
            if (aeStack != null) {
                return aeStack.getStackSize();
            }
        }
        return stack.getCount();
    }
}

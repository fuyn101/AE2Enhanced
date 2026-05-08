package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.client.gui.implementations.GuiCraftAmount;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.util.FakeEssentias;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

/**
 * E2a：在合成数量 GUI 中渲染流体/气体/源质假物品的真实名称 tooltip。
 */
@Mixin(value = GuiCraftAmount.class, remap = false)
public class MixinGuiCraftAmount {

    @Inject(method = "renderHoveredToolTip", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onRenderTooltip(int mouseX, int mouseY, CallbackInfo ci) {
        GuiContainer screen = (GuiContainer) (Object) this;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.getHasStack()) {
            return;
        }

        ItemStack stack = slot.getStack();
        String tooltip = null;

        if (!com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED && ItemFluidDrop.isFluidDrop(stack)) {
            net.minecraftforge.fluids.FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            if (fluid != null) {
                tooltip = fluid.getLocalizedName();
            }
        } else if (!com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED && ItemGasDrop.isGasDrop(stack)) {
            String gasName = ItemGasDrop.getGasName(stack);
            if (gasName != null) {
                tooltip = gasName;
            }
        } else if (FakeEssentias.isEssentiaFakeItem(stack)) {
            String aspectTag = com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop.getAspectTag(stack);
            if (aspectTag != null) {
                tooltip = "Essentia: " + aspectTag;
            }
        }

        if (tooltip != null) {
            screen.drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);
            ci.cancel();
        }
    }
}

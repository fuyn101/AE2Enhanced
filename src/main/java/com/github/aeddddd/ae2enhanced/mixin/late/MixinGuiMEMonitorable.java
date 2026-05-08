package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.util.FakeEssentias;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * E2a：在标准 AE2 物品终端中渲染假物品（流体/气体/源质）的 tooltip。
 * 与 ae2fc 的兼容策略：priority=1100，在 ae2fc 的 tooltip 渲染之前优先检查我们的假物品。
 */
@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 1100)
public class MixinGuiMEMonitorable {

    /**
     * 拦截鼠标悬停 tooltip 渲染。
     */
    @Inject(method = "renderHoveredToolTip", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onRenderTooltip(int mouseX, int mouseY, CallbackInfo ci) {
        GuiContainer screen = (GuiContainer) (Object) this;
        Slot slot = screen.getSlotUnderMouse();
        if (!(slot instanceof SlotME) || !slot.getHasStack()) {
            return;
        }

        ItemStack stack = slot.getStack();
        List<String> tooltip = buildFakeItemTooltip(stack, slot);
        if (tooltip == null) {
            return; // 不是我们的假物品，放行
        }

        screen.drawHoveringText(tooltip, mouseX, mouseY);
        ci.cancel();
    }

    /**
     * 根据假物品类型构建 tooltip。返回 null 表示不是本 mod 的假物品。
     */
    private static List<String> buildFakeItemTooltip(ItemStack stack, Slot slot) {
        List<String> tooltip = new ArrayList<>();

        // 流体假物品
        if (ItemFluidDrop.isFluidDrop(stack)) {
            FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            String name = fluid != null ? fluid.getLocalizedName() : "Unknown Fluid";
            tooltip.add("\u00A7b" + "Fluid: " + name);
            tooltip.add("\u00A77" + "Amount: " + getAEStackSize(slot, stack));
            return tooltip;
        }

        // 气体假物品
        if (ItemGasDrop.isGasDrop(stack)) {
            String gasName = ItemGasDrop.getGasName(stack);
            String name = gasName != null ? gasName : "Unknown Gas";
            tooltip.add("\u00A7b" + "Gas: " + name);
            tooltip.add("\u00A77" + "Amount: " + getAEStackSize(slot, stack));
            return tooltip;
        }

        // 源质假物品
        if (FakeEssentias.isEssentiaFakeItem(stack)) {
            String aspectTag = ItemEssentiaDrop.getAspectTag(stack);
            String aspectName = aspectTag != null ? aspectTag : "Unknown";
            tooltip.add("\u00A7b" + "Essentia: " + aspectName);
            tooltip.add("\u00A77" + "Amount: " + getAEStackSize(slot, stack));
            return tooltip;
        }

        return null; // 不是我们的假物品
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

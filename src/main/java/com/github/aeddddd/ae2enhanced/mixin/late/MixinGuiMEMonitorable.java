package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.util.FakeEssentias;
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
 * E2a：在标准 AE2 物品终端中渲染源质假物品的 tooltip。
 * 与 ae2fc 的兼容策略：priority=1100，在 ae2fc 的 tooltip 渲染之前优先检查我们的假物品。
 * 如果是源质假物品，渲染自定义 tooltip 并 cancel，否则放行给 ae2fc 处理。
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
        if (!FakeEssentias.isEssentiaFakeItem(stack)) {
            return;
        }

        // 构建源质 tooltip
        String aspectTag = ItemEssentiaDrop.getAspectTag(stack);
        String aspectName = aspectTag != null ? aspectTag : "Unknown";
        List<String> tooltip = new ArrayList<>();
        tooltip.add("\u00A7b" + "Essentia: " + aspectName); // 青色前缀

        // 获取实际数量：SlotME 中的 IAEItemStack 数量可能大于 ItemStack.count
        if (slot instanceof SlotME) {
            IAEItemStack aeStack = ((SlotME) slot).getAEStack();
            if (aeStack != null) {
                tooltip.add("\u00A77" + "Amount: " + aeStack.getStackSize());
            } else {
                tooltip.add("\u00A77" + "Amount: " + stack.getCount());
            }
        }

        screen.drawHoveringText(tooltip, mouseX, mouseY);
        ci.cancel();
    }
}

package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.me.SlotFluidME;
import appeng.fluids.client.gui.GuiFluidTerminal;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：拦截 AE2-UEL 流体终端的点击。
 * GuiFluidTerminal 直接 override 了 func_184098_a，处理 SlotFluidME 后直接 return，
 * 不会走到 AEBaseGui.func_184098_a，因此需要单独拦截。
 */
@Mixin(value = GuiFluidTerminal.class, remap = false, priority = 1099)
public class MixinGuiFluidTerminalHandleClick {

    @Inject(method = "func_184098_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onHandleMouseClickFluid(Slot slot, int slotIdx, int mouseButton, ClickType clickType, CallbackInfo ci) {
        // 日志已移除，保留注入点以备未来扩展
    }
}

package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：阻止玩家将假物品（流体/气体）从鼠标拖入 AE2 终端槽位。
 * 本 mixin 位于 mixins.ae2enhanced.late.json 中，无条件加载。
 * 源质假物品的拦截由 MixinGuiMEMonitorable（thaumic.json）处理。
 *
 * 注意：仅拦截"放入"操作（玩家鼠标上有假物品时点击 SlotME）。
 * "提取"操作（点击假物品槽位）仍由服务器端的 MixinNetworkMonitor 处理。
 */
@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 1099)
public class MixinGuiMEMonitorableHandleClick {

    @Inject(method = "func_184098_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onHandleMouseClick(Slot slot, int slotId, int mouseButton, ClickType clickType, CallbackInfo ci) {
        if (!(slot instanceof SlotME)) {
            return;
        }

        // 获取玩家鼠标上的物品
        ItemStack mouseItem = Minecraft.getMinecraft().player.inventory.getItemStack();
        if (mouseItem.isEmpty()) {
            return; // 鼠标上没有物品，是提取操作，放行给服务器处理
        }

        // 检查鼠标物品是否是流体/气体假物品
        if (ItemFluidDrop.isFluidDrop(mouseItem) || ItemGasDrop.isGasDrop(mouseItem)) {
            ci.cancel();
        }
    }
}

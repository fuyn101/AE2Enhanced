package com.github.aeddddd.ae2enhanced.mixin.late;

import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E2a：阻止假物品（流体/气体/源质）进入玩家背包。
 * AE2 终端在提取时可能绕过 NetworkMonitor.extractItems 拦截，
 * 直接在容器层将假物品放入背包。此处兜底拦截。
 */
@Mixin(value = InventoryPlayer.class)
public class MixinInventoryPlayer {

    @Inject(method = "addItemStackToInventory", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onAddItemStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        if (stack.getItem() instanceof ItemEssentiaDrop ||
            stack.getItem() instanceof ItemFluidDrop ||
            stack.getItem() instanceof ItemGasDrop) {
            cir.setReturnValue(false);
        }
    }
}

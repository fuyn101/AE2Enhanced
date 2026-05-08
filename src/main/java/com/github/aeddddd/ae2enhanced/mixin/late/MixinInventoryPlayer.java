package com.github.aeddddd.ae2enhanced.mixin.late;

import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E2a：阻止 ItemEssentiaDrop 假物品进入玩家背包。
 * AE2 终端在提取时可能绕过 NetworkMonitor.extractItems 拦截，
 * 直接在容器层将假物品放入背包。此处兜底拦截。
 */
@Mixin(value = InventoryPlayer.class, remap = false)
public class MixinInventoryPlayer {

    @Inject(method = "addItemStackToInventory", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onAddItemStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemEssentiaDrop) {
            cir.setReturnValue(false);
        }
    }
}

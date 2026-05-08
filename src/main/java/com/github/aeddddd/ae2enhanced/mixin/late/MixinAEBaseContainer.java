package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.helpers.InventoryAction;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.util.FakeEssentias;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：阻止从 AE2 终端提取假物品（流体/气体/源质）。
 * 无论左键、右键还是 Shift+点击，只要目标物品是假物品，直接取消 doAction。
 * 这是比 MixinNetworkMonitor.extractItems 更可靠的兜底拦截，因为某些终端实现
 * 可能使用自定义的 IMEMonitor，其 extractItems 不会走到 NetworkMonitor。
 */
@Mixin(value = AEBaseContainer.class, remap = false)
public class MixinAEBaseContainer {

    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$preventFakeItemExtraction(EntityPlayerMP player, InventoryAction action, int slot, long id,
                                                        CallbackInfo ci) {
        IAEItemStack slotItem = ((AEBaseContainer) (Object) this).getTargetStack();
        if (slotItem == null) return;

        net.minecraft.item.ItemStack mcStack = slotItem.createItemStack();
        if (mcStack.isEmpty()) return;

        // 拦截源质假物品
        if (FakeEssentias.isEssentiaFakeItem(mcStack)) {
            ci.cancel();
            return;
        }

        // 拦截流体假物品
        if (ItemFluidDrop.isFluidDrop(mcStack)) {
            ci.cancel();
            return;
        }

        // 拦截气体假物品
        if (ItemGasDrop.isGasDrop(mcStack)) {
            ci.cancel();
        }
    }
}

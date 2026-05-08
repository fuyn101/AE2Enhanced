package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.helpers.InventoryAction;
import com.github.aeddddd.ae2enhanced.util.FakeEssentias;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：阻止从 AE2 终端提取 ItemEssentiaDrop 假物品。
 * 无论左键、右键还是 Shift+点击，只要目标物品是源质假物品，直接取消 doAction。
 * 这是比 MixinNetworkMonitor.extractItems 更可靠的兜底拦截，因为某些终端实现
 * 可能使用自定义的 IMEMonitor，其 extractItems 不会走到 NetworkMonitor。
 */
@Mixin(value = AEBaseContainer.class, remap = false)
public class MixinAEBaseContainer {

    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$preventFakeEssentiaExtraction(EntityPlayerMP player, InventoryAction action, int slot, long id,
                                                            CallbackInfo ci) {
        IAEItemStack slotItem = ((AEBaseContainer) (Object) this).getTargetStack();
        if (slotItem != null && FakeEssentias.isEssentiaFakeItem(slotItem.createItemStack())) {
            ci.cancel();
        }
    }
}

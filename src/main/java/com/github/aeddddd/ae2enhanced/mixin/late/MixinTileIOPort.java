package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.tile.storage.TileIOPort;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.util.FakeEssentias;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E2a：防止流体/气体/源质假物品在 IO Port 传输时被计数为真实物品。
 */
@Mixin(value = TileIOPort.class, remap = false)
public class MixinTileIOPort {

    private static final ThreadLocal<Boolean> checkingDrop = new ThreadLocal<>();

    @Inject(method = "transferContents", at = @At("HEAD"))
    private void onTransferHead(CallbackInfoReturnable<Boolean> cir) {
        checkingDrop.set(true);
    }

    @Inject(method = "transferContents", at = @At("RETURN"))
    private void onTransferReturn(CallbackInfoReturnable<Boolean> cir) {
        checkingDrop.remove();
    }

    @Redirect(method = "transferContents", at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J", ordinal = 0))
    private long removeDropSize(IAEStack instance) {
        if (com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED) return instance.getStackSize();
        if (Boolean.TRUE.equals(checkingDrop.get()) && instance instanceof IAEItemStack) {
            ItemStack mcStack = ((IAEItemStack) instance).createItemStack();
            if (ItemFluidDrop.isFluidDrop(mcStack) || ItemGasDrop.isGasDrop(mcStack) || FakeEssentias.isEssentiaFakeItem(mcStack)) {
                return 0L;
            }
        }
        return instance.getStackSize();
    }
}

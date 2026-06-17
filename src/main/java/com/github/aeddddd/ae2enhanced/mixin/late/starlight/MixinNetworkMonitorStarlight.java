package com.github.aeddddd.ae2enhanced.mixin.late.starlight;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IAEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeStarlight;
import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;

/**
 * 在 AE2 物品网络的 NetworkMonitor 中注入 Starlight 假物品.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkMonitor.class, remap = false)
public class MixinNetworkMonitorStarlight {

    @Shadow
    private GridStorageCache myGridCache;

    @Shadow
    private IStorageChannel myChannel;

    private boolean ae2enhanced$isItemChannel() {
        return this.myChannel == AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @SuppressWarnings("unchecked")
    private IMEMonitor<IAEStarlightStack> ae2enhanced$getStarlightMonitor() {
        try {
            IStarlightStorageChannel starlightChannel = AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class);
            return this.myGridCache.getInventory(starlightChannel);
        } catch (Exception e) {
            return null;
        }
    }

    @Inject(method = "getAvailableItems", at = @At("TAIL"))
    private void ae2enhanced$onGetAvailableItemsStarlight(IItemList out, CallbackInfoReturnable<IItemList> cir) {
        if (!ae2enhanced$isItemChannel()) return;

        IMEMonitor<IAEStarlightStack> starlightMonitor = ae2enhanced$getStarlightMonitor();
        if (starlightMonitor == null) return;

        IStarlightStorageChannel starlightChannel = AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class);
        IItemList<IAEStarlightStack> starlightList = starlightChannel.createList();
        starlightMonitor.getAvailableItems(starlightList);

        for (IAEStarlightStack starlight : starlightList) {
            if (starlight == null || starlight.getStackSize() <= 0) continue;
            IAEItemStack fakeItem = FakeStarlight.packStarlight(starlight);
            if (fakeItem != null) {
                out.addStorage(fakeItem);
            }
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onExtractItemsStarlight(IAEStack request, Actionable mode, IActionSource source,
                                                      CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemStarlightDrop.isStarlightDrop(mcStack)) return;

        IMEMonitor<IAEStarlightStack> starlightMonitor = ae2enhanced$getStarlightMonitor();
        if (starlightMonitor == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEStarlightStack starlightRequest = AEStarlightStack.create(itemStack.getStackSize());
        IAEStarlightStack notExtracted = starlightMonitor.extractItems(starlightRequest, mode, source);
        long notExtractedSize = notExtracted != null ? notExtracted.getStackSize() : 0;
        long extractedSize = itemStack.getStackSize() - notExtractedSize;

        if (extractedSize > 0 && mode == Actionable.MODULATE) {
            IAEItemStack diff = itemStack.copy();
            diff.setStackSize(-extractedSize);
            ae2enhanced$notifyListeners(Collections.singletonList(diff), source);
        }

        if (notExtractedSize == 0) {
            cir.setReturnValue(null);
        } else {
            IAEItemStack result = itemStack.copy();
            result.setStackSize(notExtractedSize);
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onInjectItemsStarlight(IAEStack input, Actionable mode, IActionSource source,
                                                     CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        ItemStack mcStack = itemStack.createItemStack();
        if (!ItemStarlightDrop.isStarlightDrop(mcStack)) return;

        IMEMonitor<IAEStarlightStack> starlightMonitor = ae2enhanced$getStarlightMonitor();
        if (starlightMonitor == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEStarlightStack starlightInput = AEStarlightStack.create(itemStack.getStackSize());
        IAEStarlightStack notInjected = starlightMonitor.injectItems(starlightInput, mode, source);
        long notInjectedSize = notInjected != null ? notInjected.getStackSize() : 0;
        long injectedSize = itemStack.getStackSize() - notInjectedSize;

        if (injectedSize > 0 && mode == Actionable.MODULATE) {
            IAEItemStack diff = itemStack.copy();
            diff.setStackSize(injectedSize);
            ae2enhanced$notifyListeners(Collections.singletonList(diff), source);
        }

        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack result = itemStack.copy();
            result.setStackSize(notInjectedSize);
            cir.setReturnValue(result);
        } else {
            cir.setReturnValue(null);
        }
    }

    private void ae2enhanced$notifyListeners(Iterable<IAEItemStack> diff, IActionSource source) {
        MixinReflectionHelper.notifyListenersOfChange((NetworkMonitor)(Object)this, diff, source);
    }
}

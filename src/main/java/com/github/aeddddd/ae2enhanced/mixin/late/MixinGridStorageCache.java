package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.util.EssentiaBusHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;

import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * E2a：GridStorageCache 实时联动——源质通道变化通知物品通道。
 * 本 mixin 位于 mixins.ae2enhanced.late.thaumic.json 中，条件加载。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = GridStorageCache.class, remap = false)
public class MixinGridStorageCache {

    private static final java.util.Set<IStorageChannel<?>> ESSENTIA_CHANNELS = new java.util.HashSet<>();

    static {
        try {
            ESSENTIA_CHANNELS.add(AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class));
        } catch (Exception e) {
            // 源质通道不可用
        }
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    private void onPostAlterationEssentia(IStorageChannel<?> chan, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (!ESSENTIA_CHANNELS.contains(chan)) return;
        postEssentiaChanges(input, src);
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void onPostChangesEssentia(C chan, int upOrDown, IItemList<T> availableItems, IActionSource src, CallbackInfo ci) {
        if (!ESSENTIA_CHANNELS.contains(chan)) return;
        postEssentiaChanges(availableItems, src);
    }

    @SuppressWarnings("unchecked")
    private void postEssentiaChanges(Iterable<? extends IAEStack<?>> changes, IActionSource src) {
        if (changes == null) return;

        try {
            GridStorageCache cache = (GridStorageCache) (Object) this;
            NetworkMonitor itemMonitor = (NetworkMonitor) cache.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return;

            List<IAEItemStack> fakeChanges = new ArrayList<>();
            for (IAEStack<?> stack : changes) {
                if (!(stack instanceof IAEEssentiaStack)) continue;
                IAEEssentiaStack essentia = (IAEEssentiaStack) stack;
                long size = essentia.getStackSize();
                if (size == 0) continue;
                IAEItemStack fake = EssentiaBusHelper.packEssentia(essentia);
                if (fake != null) {
                    fakeChanges.add(fake);
                }
            }

            if (fakeChanges.isEmpty()) return;

            MixinReflectionHelper.postChange(itemMonitor, true, fakeChanges, src);
        } catch (Exception e) {
            // 反射调用失败，静默处理
        }
    }
}

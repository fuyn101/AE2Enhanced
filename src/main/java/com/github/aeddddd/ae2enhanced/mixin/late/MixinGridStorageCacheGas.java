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
import com.github.aeddddd.ae2enhanced.util.FakeGases;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * E2a：GridStorageCache 实时联动——气体通道变化通知物品通道。
 * 本 mixin 位于 mixins.ae2enhanced.late.gas.json 中，条件加载。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = GridStorageCache.class, remap = false)
public class MixinGridStorageCacheGas {

    private static final java.util.Set<IStorageChannel<?>> GAS_CHANNELS = new java.util.HashSet<>();

    static {
        try {
            GAS_CHANNELS.add(AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class));
        } catch (Exception e) {
            // 气体通道不可用
        }
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    private void onPostAlterationGas(IStorageChannel<?> chan, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (!GAS_CHANNELS.contains(chan)) return;
        postGasChanges(input, src);
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void onPostChangesGas(C chan, int upOrDown, IItemList<T> availableItems, IActionSource src, CallbackInfo ci) {
        if (!GAS_CHANNELS.contains(chan)) return;
        postGasChanges(availableItems, src);
    }

    @SuppressWarnings("unchecked")
    private void postGasChanges(Iterable<? extends IAEStack<?>> changes, IActionSource src) {
        if (com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED) return;
        if (changes == null) return;

        try {
            GridStorageCache cache = (GridStorageCache) (Object) this;
            NetworkMonitor itemMonitor = (NetworkMonitor) cache.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return;

            List<IAEItemStack> fakeChanges = new ArrayList<>();
            for (IAEStack<?> stack : changes) {
                if (!(stack instanceof IAEGasStack)) continue;
                IAEGasStack gas = (IAEGasStack) stack;
                long size = gas.getStackSize();
                if (size == 0) continue;
                IAEItemStack fake = FakeGases.packGas(gas);
                if (fake != null) {
                    fakeChanges.add(fake);
                }
            }

            if (fakeChanges.isEmpty()) return;

            Method postChangeMethod = NetworkMonitor.class.getDeclaredMethod("postChange", boolean.class, Iterable.class, IActionSource.class);
            postChangeMethod.setAccessible(true);
            postChangeMethod.invoke(itemMonitor, true, fakeChanges, src);
        } catch (Exception e) {
            // 反射调用失败，静默处理
        }
    }
}

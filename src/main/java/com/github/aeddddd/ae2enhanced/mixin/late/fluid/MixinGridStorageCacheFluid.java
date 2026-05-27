package com.github.aeddddd.ae2enhanced.mixin.late.fluid;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * E2a：GridStorageCache 实时联动——流体通道变化通知物品通道。
 * 当流体网络中的存储发生实时变化时，将变化转换为假物品并 postChange 到物品 monitor，
 * 使终端自动刷新流体显示。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = GridStorageCache.class, remap = false)
public class MixinGridStorageCacheFluid {

    private static final java.util.Set<IStorageChannel<?>> FLUID_CHANNELS = new java.util.HashSet<>();

    static {
        try {
            FLUID_CHANNELS.add(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        } catch (Exception e) {
            // 流体通道不可用
        }
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    private void onPostAlteration(IStorageChannel<?> chan, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (!FLUID_CHANNELS.contains(chan)) return;
        postFluidChanges(input, src);
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void onPostChanges(C chan, int upOrDown, IItemList<T> availableItems, IActionSource src, CallbackInfo ci) {
        if (!FLUID_CHANNELS.contains(chan)) return;
        postFluidChanges(availableItems, src);
    }

    @SuppressWarnings("unchecked")
    private void postFluidChanges(Iterable<? extends IAEStack<?>> changes, IActionSource src) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return;
        if (changes == null) return;

        try {
            GridStorageCache cache = (GridStorageCache) (Object) this;
            NetworkMonitor itemMonitor = (NetworkMonitor) cache.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return;

            List<IAEItemStack> fakeChanges = new ArrayList<>();
            for (IAEStack<?> stack : changes) {
                if (!(stack instanceof IAEFluidStack)) continue;
                IAEFluidStack fluid = (IAEFluidStack) stack;
                long size = fluid.getStackSize();
                if (size == 0) continue;
                IAEItemStack fake = FakeFluids.packFluid(fluid);
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

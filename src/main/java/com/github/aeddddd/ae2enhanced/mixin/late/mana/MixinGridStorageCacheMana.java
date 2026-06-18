package com.github.aeddddd.ae2enhanced.mixin.late.mana;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeMana;
import com.github.aeddddd.ae2enhanced.mixin.MixinReflectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GridStorageCache 实时联动——Mana 通道变化通知物品通道.
 */
@SuppressWarnings("rawtypes")
@Mixin(value = GridStorageCache.class, remap = false)
public class MixinGridStorageCacheMana {

    private static final Set<IStorageChannel<?>> MANA_CHANNELS = new HashSet<>();

    static {
        try {
            MANA_CHANNELS.add(AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class));
        } catch (Exception e) {
            // Mana 通道不可用
        }
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("TAIL"))
    private void onPostAlteration(IStorageChannel<?> chan, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (!MANA_CHANNELS.contains(chan)) return;
        postManaChanges(input, src, true);
    }

    @Inject(method = "postChangesToNetwork", at = @At("TAIL"))
    private <T extends IAEStack<T>, C extends IStorageChannel<T>> void onPostChanges(C chan, int upOrDown, IItemList<T> availableItems, IActionSource src, CallbackInfo ci) {
        if (!MANA_CHANNELS.contains(chan)) return;
        postManaChanges(availableItems, src, upOrDown > 0);
    }

    @SuppressWarnings("unchecked")
    private void postManaChanges(Iterable<? extends IAEStack<?>> changes, IActionSource src, boolean add) {
        if (changes == null) return;

        try {
            GridStorageCache cache = (GridStorageCache) (Object) this;
            NetworkMonitor itemMonitor = (NetworkMonitor) cache.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return;

            List<IAEItemStack> fakeChanges = new ArrayList<>();
            for (IAEStack<?> stack : changes) {
                if (!(stack instanceof IAEManaStack)) continue;
                IAEManaStack mana = (IAEManaStack) stack;
                long size = mana.getStackSize();
                if (size == 0) continue;
                IAEItemStack fake = FakeMana.packMana(mana);
                if (fake != null) {
                    fakeChanges.add(fake);
                }
            }

            if (fakeChanges.isEmpty()) return;

            MixinReflectionHelper.postChange(itemMonitor, add, fakeChanges, src);
        } catch (Exception e) {
            // 反射调用失败,静默处理
        }
    }
}

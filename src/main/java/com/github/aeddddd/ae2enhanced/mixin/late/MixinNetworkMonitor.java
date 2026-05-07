package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.github.aeddddd.ae2enhanced.util.FakeEssentias;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics.api.storage.IAEEssentiaStack;

/**
 * E2a：在 AE2 物品网络的 NetworkMonitor 中注入源质假物品。
 * 与 ae2fc 的兼容策略：priority=1100（高于 ae2fc 默认 1000），
 * 在 ae2fc 完成流体/气体注入后，再注入源质假物品。
 * extractItems / injectItems 使用 HEAD + cancellable 优先拦截我们的假物品，
 * 非我们的假物品则放行给 ae2fc 或原始逻辑处理。
 */
@SuppressWarnings("rawtypes")
@Mixin(value = NetworkMonitor.class, remap = false, priority = 1100)
public class MixinNetworkMonitor {

    @Shadow
    private GridStorageCache myGridCache;

    @Shadow
    private IStorageChannel myChannel;

    /**
     * 判断当前 monitor 是否是物品存储通道。
     */
    private boolean ae2enhanced$isItemChannel() {
        return this.myChannel == AEApi.instance().storage().getStorageChannel(
                appeng.api.storage.channels.IItemStorageChannel.class);
    }

    /**
     * 获取源质存储通道的 monitor。
     */
    @SuppressWarnings("unchecked")
    private IMEMonitor<IAEEssentiaStack> ae2enhanced$getEssentiaMonitor() {
        try {
            IStorageChannel<IAEEssentiaStack> essentiaChannel = AEApi.instance().storage()
                    .getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
            IMEInventoryHandler<IAEEssentiaStack> handler = this.myGridCache.getInventoryHandler(essentiaChannel);
            if (handler instanceof IMEMonitor) {
                return (IMEMonitor<IAEEssentiaStack>) handler;
            }
        } catch (Exception e) {
            // Thaumic Energistics 通道不可用
        }
        return null;
    }

    /**
     * 在物品列表查询的末尾注入源质假物品。
     */
    @Inject(method = "getAvailableItems", at = @At("TAIL"))
    private void ae2enhanced$onGetAvailableItems(IItemList out, CallbackInfoReturnable<IItemList> cir) {
        if (!ae2enhanced$isItemChannel()) return;

        IMEMonitor<IAEEssentiaStack> essentiaMonitor = ae2enhanced$getEssentiaMonitor();
        if (essentiaMonitor == null) return;

        IStorageChannel<IAEEssentiaStack> essentiaChannel = AEApi.instance().storage()
                .getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
        IItemList<IAEEssentiaStack> essentiaList = essentiaChannel.createList();
        essentiaMonitor.getAvailableItems(essentiaList);

        for (IAEEssentiaStack essentia : essentiaList) {
            if (essentia == null || essentia.getStackSize() <= 0) continue;
            IAEItemStack fakeItem = FakeEssentias.packEssentia(essentia);
            if (fakeItem != null) {
                out.addStorage(fakeItem);
            }
        }
    }

    /**
     * 拦截提取请求：如果是源质假物品，从源质网络中提取。
     */
    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onExtractItems(IAEStack request, Actionable mode, IActionSource source,
                                            CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(request instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) request;
        if (!FakeEssentias.isEssentiaFakeItem(itemStack.createItemStack())) return;

        IMEMonitor<IAEEssentiaStack> essentiaMonitor = ae2enhanced$getEssentiaMonitor();
        if (essentiaMonitor == null) {
            cir.setReturnValue(request); // 无法提取，返回原请求
            return;
        }

        IAEEssentiaStack essentiaRequest = FakeEssentias.unpackEssentia(itemStack);
        if (essentiaRequest == null) {
            cir.setReturnValue(request);
            return;
        }

        IAEEssentiaStack notExtracted = essentiaMonitor.extractItems(essentiaRequest, mode, source);
        if (notExtracted != null && notExtracted.getStackSize() > 0) {
            // 部分未提取，转换回假物品返回
            IAEItemStack fakeNotExtracted = FakeEssentias.packEssentia(notExtracted);
            cir.setReturnValue(fakeNotExtracted);
        } else {
            // 全部提取成功
            cir.setReturnValue(null);
        }
    }

    /**
     * 拦截注入请求：如果是源质假物品，注入到源质网络中。
     */
    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onInjectItems(IAEStack input, Actionable mode, IActionSource source,
                                           CallbackInfoReturnable<IAEStack> cir) {
        if (!ae2enhanced$isItemChannel()) return;
        if (!(input instanceof IAEItemStack)) return;

        IAEItemStack itemStack = (IAEItemStack) input;
        if (!FakeEssentias.isEssentiaFakeItem(itemStack.createItemStack())) return;

        IMEMonitor<IAEEssentiaStack> essentiaMonitor = ae2enhanced$getEssentiaMonitor();
        if (essentiaMonitor == null) {
            cir.setReturnValue(input); // 无法注入，返回原输入
            return;
        }

        IAEEssentiaStack essentiaInput = FakeEssentias.unpackEssentia(itemStack);
        if (essentiaInput == null) {
            cir.setReturnValue(input);
            return;
        }

        IAEEssentiaStack notInjected = essentiaMonitor.injectItems(essentiaInput, mode, source);
        if (notInjected != null && notInjected.getStackSize() > 0) {
            IAEItemStack fakeNotInjected = FakeEssentias.packEssentia(notInjected);
            cir.setReturnValue(fakeNotInjected);
        } else {
            cir.setReturnValue(null);
        }
    }
}

package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;

import java.math.BigInteger;

/**
 * 源质存储适配器，继承 {@link AbstractStorageAdapter}。
 * 内部使用 BigInteger 维护数量，突破 long 上限。
 */
public class EssentiaStorageAdapter extends AbstractStorageAdapter<IAEEssentiaStack, EssentiaDescriptor> {

    public EssentiaStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class);
        file.loadEssentias(storage);
        recalcTotal(); // 从文件加载后必须重新计算总数
    }

    @Override
    protected EssentiaDescriptor createDescriptor(IAEEssentiaStack input) {
        return new EssentiaDescriptor(input);
    }

    @Override
    protected IAEEssentiaStack createResult(IAEEssentiaStack request, BigInteger amount) {
        IAEEssentiaStack result = request.copy();
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            result.setStackSize(Long.MAX_VALUE);
        } else {
            result.setStackSize(amount.longValueExact());
        }
        return result;
    }

    @Override
    protected IAEEssentiaStack getAETemplate(EssentiaDescriptor descriptor) {
        return descriptor.getAETemplate();
    }

    @Override
    public IStorageChannel<IAEEssentiaStack> getChannel() {
        return (IStorageChannel<IAEEssentiaStack>) channel;
    }
}

package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import java.math.BigInteger;

/**
 * 物品存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 BigInteger 维护数量,突破 long 上限.
 */
public class ItemStorageAdapter extends AbstractStorageAdapter<IAEItemStack, ItemDescriptor> {

    public ItemStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        file.load(storage);
        recalcTotal(); // 从文件加载后必须重新计算总数
    }

    @Override
    protected ItemDescriptor createDescriptor(IAEItemStack input) {
        return new ItemDescriptor(input.createItemStack());
    }

    @Override
    protected IAEItemStack createResult(IAEItemStack request, BigInteger amount) {
        IAEItemStack result = ((IItemStorageChannel) channel).createStack(request.createItemStack());
        if (result == null) return null;
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            result.setStackSize(Long.MAX_VALUE);
        } else {
            result.setStackSize(amount.longValueExact());
        }
        return result;
    }

    @Override
    protected IAEItemStack getAETemplate(ItemDescriptor descriptor) {
        return descriptor.getAETemplate((IItemStorageChannel) channel);
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return (IStorageChannel<IAEItemStack>) channel;
    }
}

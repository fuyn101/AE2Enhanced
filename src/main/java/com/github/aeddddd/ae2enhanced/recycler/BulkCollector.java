package com.github.aeddddd.ae2enhanced.recycler;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * 批量收集待注入的物品.
 */
public class BulkCollector {

    private final KeyCounter buffer = new KeyCounter();

    public void add(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return;
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return;
        buffer.add(key, stack.getCount());
    }

    public void add(@Nonnull AEItemKey key, long amount) {
        if (amount <= 0) return;
        buffer.add(key, amount);
    }

    @Nonnull
    public List<Object2LongMap.Entry<AEItemKey>> drain() {
        if (buffer.isEmpty()) return Collections.emptyList();
        List<Object2LongMap.Entry<AEItemKey>> result = new java.util.ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : buffer) {
            if (entry.getKey() instanceof AEItemKey) {
                @SuppressWarnings("unchecked")
                Object2LongMap.Entry<AEItemKey> cast = (Object2LongMap.Entry<AEItemKey>) (Object) entry;
                result.add(cast);
            }
        }
        buffer.reset();
        return result;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public int getTypeCount() {
        return buffer.size();
    }

    public long getTotalCount() {
        long total = 0;
        for (Object2LongMap.Entry<AEKey> entry : buffer) {
            total += entry.getLongValue();
        }
        return total;
    }
}

package com.github.aeddddd.ae2enhanced.recycler;

import ae2.api.storage.channels.IItemStorageChannel;
import ae2.api.storage.data.AEItemKey;
import ae2.util.item.AEItemKey;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量收集待注入的物品.
 */
public class BulkCollector {

    private final List<AEItemKey> buffer = new ArrayList<>();
    private long totalCount = 0;

    public void add(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return;
        AEItemKey ae = AEItemKey.fromItemStack(stack);
        if (ae == null) return;
        add(ae);
    }

    public void add(@Nonnull AEItemKey stack) {
        if (stack.getStackSize() <= 0) return;
        for (AEItemKey existing : buffer) {
            if (existing.isSameType(stack)) {
                existing.add(stack);
                totalCount += stack.getStackSize();
                return;
            }
        }
        buffer.add(stack.copy());
        totalCount += stack.getStackSize();
    }

    @Nonnull
    public List<AEItemKey> drain() {
        if (buffer.isEmpty()) return Collections.emptyList();
        List<AEItemKey> result = new ArrayList<>(buffer);
        buffer.clear();
        totalCount = 0;
        return result;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public int getTypeCount() {
        return buffer.size();
    }

    public long getTotalCount() {
        return totalCount;
    }
}

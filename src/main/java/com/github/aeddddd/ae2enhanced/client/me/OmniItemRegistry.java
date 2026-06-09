package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.storage.data.IAEItemStack;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Collection;

/**
 * Omni Terminal 客户端物品注册表。
 *
 * <p>维护服务端分配的 int ID 与本地 {@link IAEItemStack} 副本 + 数量的映射。
 * 生命周期与 Omni Terminal GUI Session 绑定，关闭 GUI 后由 GC 回收。
 */
public class OmniItemRegistry {

    private final Int2ObjectMap<IAEItemStack> idToStack = new Int2ObjectOpenHashMap<>();
    private final Int2LongMap idToCount = new Int2LongOpenHashMap();

    public void register(int id, IAEItemStack stack, long count) {
        IAEItemStack copy = stack.copy();
        copy.setStackSize(count);
        this.idToStack.put(id, copy);
        this.idToCount.put(id, count);
    }

    public void updateCount(int id, long count) {
        this.idToCount.put(id, count);
        IAEItemStack stack = this.idToStack.get(id);
        if (stack != null) {
            stack.setStackSize(count);
        }
    }

    public IAEItemStack getStack(int id) {
        return this.idToStack.get(id);
    }

    public long getCount(int id) {
        return this.idToCount.get(id);
    }

    public void clear() {
        this.idToStack.clear();
        this.idToCount.clear();
    }

    public Collection<IAEItemStack> getAllStacks() {
        return this.idToStack.values();
    }
}

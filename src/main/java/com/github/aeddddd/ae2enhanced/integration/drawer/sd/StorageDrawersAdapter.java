package com.github.aeddddd.ae2enhanced.integration.drawer.sd;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import com.github.aeddddd.ae2enhanced.integration.drawer.IDrawerIndexAdapter;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * StorageDrawers Hash 索引适配器.
 *
 * <p>包装 {@link IItemRepository},对 TileEntityController 利用其内置的
 * drawerPrimaryLookup Hash 索引加速 {@link #getAvailableStacks},跳过空槽位遍历.</p>
 *
 * <p>所有反射和类加载风险全部封装在本类内部,外部调用者无需考虑 NPE.</p>
 *
 * <p>已迁移至 AE2S API:使用 {@link AEItemKey} + {@code long amount} 与 {@link KeyCounter}.</p>
 */
public class StorageDrawersAdapter implements IDrawerIndexAdapter {

    private final IItemRepository repository;
    private final TileEntityController controller;
    private final Object lookup;
    private final Method entrySetMethod;

    // SlotRecord 反射缓存
    private static final Class<?> SLOT_RECORD_CLASS;
    private static final Field SLOT_RECORD_GROUP_FIELD;
    private static final Field SLOT_RECORD_SLOT_FIELD;

    static {
        Class<?> clazz = null;
        Field groupField = null;
        Field slotField = null;
        try {
            clazz = Class.forName("com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController$SlotRecord");
            groupField = clazz.getDeclaredField("group");
            groupField.setAccessible(true);
            slotField = clazz.getDeclaredField("slot");
            slotField.setAccessible(true);
        } catch (Exception ignored) {}
        SLOT_RECORD_CLASS = clazz;
        SLOT_RECORD_GROUP_FIELD = groupField;
        SLOT_RECORD_SLOT_FIELD = slotField;
    }

    public StorageDrawersAdapter(IItemRepository repository, TileEntity tile) {
        this.repository = repository;
        if (tile instanceof TileEntityController) {
            this.controller = (TileEntityController) tile;
            Object lk = null;
            Method es = null;
            try {
                Field lookupField = TileEntityController.class.getDeclaredField("drawerPrimaryLookup");
                lookupField.setAccessible(true);
                lk = lookupField.get(this.controller);
                es = lk.getClass().getDeclaredMethod("entrySet");
                es.setAccessible(true);
            } catch (Exception ignored) {}
            this.lookup = lk;
            this.entrySetMethod = es;
        } else {
            this.controller = null;
            this.lookup = null;
            this.entrySetMethod = null;
        }
    }

    @Override
    public long injectItems(AEItemKey input, long amount, Actionable type, IActionSource src) {
        if (input == null || amount <= 0) {
            return amount;
        }
        ItemStack stack = input.toStack();
        stack.setCount((int) Math.min(amount, Integer.MAX_VALUE));
        ItemStack remaining = this.repository.insertItem(stack, type == Actionable.SIMULATE);
        if (remaining == stack) {
            return amount;
        }
        if (remaining.isEmpty()) {
            return 0;
        }
        return remaining.getCount();
    }

    @Override
    public long extractItems(AEItemKey request, long amount, Actionable mode, IActionSource src) {
        if (request == null || amount <= 0) {
            return 0;
        }
        int amt = (int) Math.min(amount, Integer.MAX_VALUE);
        ItemStack extracted = this.repository.extractItem(request.toStack(), amt, mode == Actionable.SIMULATE);
        return extracted.isEmpty() ? 0 : extracted.getCount();
    }

    /**
     * 比较两个 ItemStack 是否属于同一物品类型,忽略 count.
     *
     * <p>与 Minecraft 原版的 {@link ItemStack#areItemStacksEqual} 不同,
     * 本方法在比较时会忽略 stackSize,专门用于把 count 聚合到同一 AEItemKey.</p>
     */
    private static boolean itemStackEqualsIgnoreCount(ItemStack a, ItemStack b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return a == b;
        }
        if (a.isEmpty() != b.isEmpty()) {
            return false;
        }
        if (a.isEmpty()) {
            return true;
        }
        return a.getItem() == b.getItem()
                && a.getMetadata() == b.getMetadata()
                && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static final class PrototypeCount {
        ItemStack prototype;
        long count;

        PrototypeCount(ItemStack prototype, long count) {
            this.prototype = prototype;
            this.count = count;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getAvailableStacks(KeyCounter out) {
        if (this.lookup != null && this.entrySetMethod != null && SLOT_RECORD_CLASS != null
                && SLOT_RECORD_GROUP_FIELD != null && SLOT_RECORD_SLOT_FIELD != null) {
            try {
                Set<Map.Entry<Item, Map<Integer, Collection<?>>>> entries =
                        (Set<Map.Entry<Item, Map<Integer, Collection<?>>>>) this.entrySetMethod.invoke(this.lookup);
                for (Map.Entry<Item, Map<Integer, Collection<?>>> itemEntry : entries) {
                    for (Map.Entry<Integer, Collection<?>> metaEntry : itemEntry.getValue().entrySet()) {
                        // 同一 Item+meta 的抽屉可能因 NBT 不同而属于不同 AEItemKey,
                        // 必须按完整 ItemStack(Item+meta+NBT) 细分,避免把不同 NBT 的物品合并成一份.
                        List<PrototypeCount> groups = new ArrayList<>();
                        for (Object recordObj : metaEntry.getValue()) {
                            IDrawerGroup group = (IDrawerGroup) SLOT_RECORD_GROUP_FIELD.get(recordObj);
                            int slot = (int) SLOT_RECORD_SLOT_FIELD.get(recordObj);
                            if (group == null) {
                                continue;
                            }
                            IDrawer drawer = group.getDrawer(slot);
                            if (drawer == null || !drawer.isEnabled() || drawer.isEmpty()) {
                                continue;
                            }
                            long count = drawer.getStoredItemCount();
                            ItemStack prototype = drawer.getStoredItemPrototype();
                            if (prototype != null) {
                                prototype = prototype.copy();
                                prototype.setCount(1);
                            }
                            PrototypeCount match = null;
                            for (PrototypeCount candidate : groups) {
                                if (itemStackEqualsIgnoreCount(candidate.prototype, prototype)) {
                                    match = candidate;
                                    break;
                                }
                            }
                            if (match != null) {
                                match.count += count;
                            } else {
                                groups.add(new PrototypeCount(prototype, count));
                            }
                        }
                        for (PrototypeCount group : groups) {
                            ItemStack prototype = group.prototype;
                            if (prototype == null || prototype.isEmpty()) {
                                prototype = new ItemStack(itemEntry.getKey(), 1, metaEntry.getKey());
                            }
                            if (group.count > 0 && !prototype.isEmpty()) {
                                AEItemKey aeKey = AEItemKey.of(prototype);
                                if (aeKey != null) {
                                    out.add(aeKey, group.count);
                                }
                            }
                        }
                    }
                }
                return;
            } catch (Exception ignored) {
                // 反射失败,回退到默认路径
            }
        }

        // 默认路径：委托给 IItemRepository.getAllItems()
        NonNullList<IItemRepository.ItemRecord> records = this.repository.getAllItems();
        for (IItemRepository.ItemRecord record : records) {
            if (record == null || record.itemPrototype == null) continue;
            if (record.count > 0 && !record.itemPrototype.isEmpty()) {
                AEItemKey aeKey = AEItemKey.of(record.itemPrototype);
                if (aeKey != null) {
                    out.add(aeKey, record.count);
                }
            }
        }
    }
}

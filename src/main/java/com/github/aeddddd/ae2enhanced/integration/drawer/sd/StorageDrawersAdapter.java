package com.github.aeddddd.ae2enhanced.integration.drawer.sd;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
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
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * StorageDrawers Hash 索引适配器.
 *
 * <p>包装 {@link IItemRepository},对 TileEntityController 利用其内置的
 * drawerPrimaryLookup Hash 索引加速 {@link #getAvailableItems},跳过空槽位遍历.</p>
 *
 * <p>所有反射和类加载风险全部封装在本类内部,外部调用者无需考虑 NPE.</p>
 */
public class StorageDrawersAdapter implements IDrawerIndexAdapter {

    private final IItemRepository repository;
    private final IItemStorageChannel channel;
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

    public StorageDrawersAdapter(IItemRepository repository, TileEntity tile, IItemStorageChannel channel) {
        this.repository = repository;
        this.channel = channel;
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
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) {
            return null;
        }
        ItemStack stack = input.getDefinition().copy();
        stack.setCount((int) Math.min(input.getStackSize(), Integer.MAX_VALUE));
        ItemStack remaining = this.repository.insertItem(stack, type == Actionable.SIMULATE);
        if (remaining == stack) {
            return input;
        }
        if (remaining.isEmpty()) {
            return null;
        }
        IAEItemStack result = input.copy();
        result.setStackSize(remaining.getCount());
        return result;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) {
            return null;
        }
        int amount = (int) Math.min(request.getStackSize(), Integer.MAX_VALUE);
        ItemStack extracted = this.repository.extractItem(request.getDefinition(), amount, mode == Actionable.SIMULATE);
        if (extracted.isEmpty()) {
            return null;
        }
        IAEItemStack result = AEItemStack.fromItemStack(extracted);
        if (result != null) {
            result.setStackSize(extracted.getCount());
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        if (this.lookup != null && this.entrySetMethod != null && SLOT_RECORD_CLASS != null
                && SLOT_RECORD_GROUP_FIELD != null && SLOT_RECORD_SLOT_FIELD != null) {
            try {
                Set<Map.Entry<Item, Map<Integer, Collection<?>>>> entries =
                        (Set<Map.Entry<Item, Map<Integer, Collection<?>>>>) this.entrySetMethod.invoke(this.lookup);
                for (Map.Entry<Item, Map<Integer, Collection<?>>> itemEntry : entries) {
                    for (Map.Entry<Integer, Collection<?>> metaEntry : itemEntry.getValue().entrySet()) {
                        long totalCount = 0L;
                        for (Object recordObj : metaEntry.getValue()) {
                            IDrawerGroup group = (IDrawerGroup) SLOT_RECORD_GROUP_FIELD.get(recordObj);
                            int slot = (int) SLOT_RECORD_SLOT_FIELD.get(recordObj);
                            if (group != null) {
                                IDrawer drawer = group.getDrawer(slot);
                                if (drawer != null && drawer.isEnabled() && !drawer.isEmpty()) {
                                    totalCount += drawer.getStoredItemCount();
                                }
                            }
                        }
                        if (totalCount > 0) {
                            ItemStack prototype = new ItemStack(itemEntry.getKey(), 1, metaEntry.getKey());
                            IAEItemStack aeStack = this.channel.createStack(prototype);
                            if (aeStack != null) {
                                aeStack.setStackSize(totalCount);
                                out.add(aeStack);
                            }
                        }
                    }
                }
                return out;
            } catch (Exception ignored) {
                // 反射失败,回退到默认路径
            }
        }

        // 默认路径：委托给 IItemRepository.getAllItems()
        NonNullList<IItemRepository.ItemRecord> records = this.repository.getAllItems();
        for (IItemRepository.ItemRecord record : records) {
            if (record == null || record.itemPrototype == null) continue;
            IAEItemStack aeStack = this.channel.createStack(record.itemPrototype);
            if (aeStack != null) {
                aeStack.setStackSize(record.count);
                out.add(aeStack);
            }
        }
        return out;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        if (input == null) {
            return false;
        }
        return this.repository.getRemainingItemCapacity(input.getDefinition()) > 0;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return true;
    }
}

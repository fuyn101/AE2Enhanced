package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.config.SortOrder;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniInventoryUpdate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 支持合成置顶的 ItemRepo，同时为 Omni Terminal 提供增量渲染与 ID 缓存支持。
 *
 * <p>activeCrafting 物品独占第一行(不受滚动影响),其余物品从第二行开始。
 */
public class OmniItemRepo extends ItemRepo {

    private static final Field VIEW_FIELD;
    private static final Field LIST_FIELD;
    private static final Field CHANGED_FIELD;
    private static final Field RESORT_FIELD;
    private static final Field SORT_SRC_FIELD;

    private List<CraftingStatus> activeCrafting = Collections.emptyList();
    private List<IAEItemStack> normalView = Collections.emptyList();

    static {
        try {
            VIEW_FIELD = ItemRepo.class.getDeclaredField("view");
            VIEW_FIELD.setAccessible(true);
            LIST_FIELD = ItemRepo.class.getDeclaredField("list");
            LIST_FIELD.setAccessible(true);
            CHANGED_FIELD = ItemRepo.class.getDeclaredField("changed");
            CHANGED_FIELD.setAccessible(true);
            RESORT_FIELD = ItemRepo.class.getDeclaredField("resort");
            RESORT_FIELD.setAccessible(true);
            SORT_SRC_FIELD = ItemRepo.class.getDeclaredField("sortSrc");
            SORT_SRC_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access ItemRepo fields", e);
        }
    }

    private final IScrollSource scrollSrc;
    private final OmniItemRegistry registry = new OmniItemRegistry();

    public OmniItemRepo(IScrollSource src, ISortSource sortSrc) {
        super(src, sortSrc);
        this.scrollSrc = src;
    }

    public void setActiveCrafting(List<CraftingStatus> list) {
        this.activeCrafting = list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    public List<CraftingStatus> getActiveCrafting() {
        return this.activeCrafting;
    }

    // ==================== Omni Terminal 自定义同步处理 ====================

    /**
     * FULL_INIT：清空所有数据，用注册表重建 list
     */
    public void handleFullInit(List<PacketOmniInventoryUpdate.Entry> entries) {
        this.registry.clear();
        clearList();

        for (PacketOmniInventoryUpdate.Entry e : entries) {
            this.registry.register(e.id, e.stack, e.count);
            if (e.count > 0) {
                this.postUpdate(e.stack.copy());
            }
        }

        setChanged(true);
        this.updateView();
    }

    /**
     * FULL_CONTINUE：追加到注册表和 list
     */
    public void handleFullContinue(List<PacketOmniInventoryUpdate.Entry> entries) {
        for (PacketOmniInventoryUpdate.Entry e : entries) {
            this.registry.register(e.id, e.stack, e.count);
            if (e.count > 0) {
                this.postUpdate(e.stack.copy());
            }
        }
        setChanged(true);
        this.updateView();
    }

    /**
     * ITEM_REGISTER：注册新类型（不触发渲染更新）
     */
    public void handleItemRegister(int id, IAEItemStack stack) {
        this.registry.register(id, stack, 0);
    }

    /**
     * DELTA_COUNT：只更新数量，尝试增量渲染
     */
    public void handleDeltaCount(List<PacketOmniInventoryUpdate.Entry> entries) {
        boolean anyChange = false;

        for (PacketOmniInventoryUpdate.Entry e : entries) {
            long oldCount = this.registry.getCount(e.id);
            this.registry.updateCount(e.id, e.count);

            IAEItemStack stack = this.registry.getStack(e.id);
            if (stack == null) continue;

            this.postUpdate(stack.copy());

            if (oldCount != e.count) {
                anyChange = true;
            }
        }

        if (anyChange) {
            setChanged(true);
            updateViewIfNeeded();
        }
    }

    /**
     * 条件渲染：需要重建时才重建，否则只更新数量
     */
    public void updateViewIfNeeded() {
        if (isResortNeeded() || isSortedByAmount()) {
            // resort 为 true（搜索/排序/显示模式变化）或按数量排序时，必须重建
            this.updateView();
        } else {
            updateViewCountsOnly();
        }
    }

    /**
     * 仅更新 view / activeCrafting / normalView 中各元素的数量，不重建列表结构。
     * 适用于：只有物品数量变化，且排序方式不是按数量时。
     */
    private void updateViewCountsOnly() {
        try {
            @SuppressWarnings("unchecked")
            List<IAEItemStack> view = (List<IAEItemStack>) VIEW_FIELD.get(this);

            // 1. 更新 view 中每个元素的数量
            for (IAEItemStack viewStack : view) {
                @SuppressWarnings("unchecked")
                appeng.api.storage.data.IItemList<IAEItemStack> list =
                    (appeng.api.storage.data.IItemList<IAEItemStack>) LIST_FIELD.get(this);
                IAEItemStack real = list.findPrecise(viewStack);
                if (real != null) {
                    viewStack.setStackSize(real.getStackSize());
                }
            }

            // 2. 更新 activeCrafting 数量
            for (CraftingStatus status : this.activeCrafting) {
                @SuppressWarnings("unchecked")
                appeng.api.storage.data.IItemList<IAEItemStack> list =
                    (appeng.api.storage.data.IItemList<IAEItemStack>) LIST_FIELD.get(this);
                IAEItemStack real = list.findPrecise(status.output);
                if (real != null) {
                    status.output.setStackSize(real.getStackSize());
                }
            }

            // 3. normalView 持有 view 的引用，数量已同步更新

            setChanged(false);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void clearList() {
        try {
            @SuppressWarnings("unchecked")
            appeng.api.storage.data.IItemList<IAEItemStack> list =
                (appeng.api.storage.data.IItemList<IAEItemStack>) LIST_FIELD.get(this);
            list.resetStatus();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void setChanged(boolean value) {
        try {
            CHANGED_FIELD.set(this, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isResortNeeded() {
        try {
            return (boolean) RESORT_FIELD.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isSortedByAmount() {
        try {
            ISortSource src = (ISortSource) SORT_SRC_FIELD.get(this);
            return src.getSortBy() == SortOrder.AMOUNT;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 原有逻辑 ====================

    @Override
    public void updateView() {
        super.updateView();
        try {
            @SuppressWarnings("unchecked")
            List<IAEItemStack> view = (List<IAEItemStack>) VIEW_FIELD.get(this);
            // 将 activeCrafting 的数量同步为 view 中的实际存储数量
            for (CraftingStatus status : this.activeCrafting) {
                IAEItemStack real = findInView(status.output, view);
                if (real != null) {
                    status.output.setStackSize(real.getStackSize());
                }
            }
            this.normalView = new ArrayList<>(view.size());
            for (IAEItemStack stack : view) {
                if (!isInActiveCrafting(stack)) {
                    this.normalView.add(stack);
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private IAEItemStack findInView(IAEItemStack target, List<IAEItemStack> view) {
        for (IAEItemStack stack : view) {
            if (stack.equals(target)) {
                return stack;
            }
        }
        return null;
    }

    @Override
    public IAEItemStack getReferenceItem(int idx) {
        if (this.activeCrafting.isEmpty()) {
            return super.getReferenceItem(idx);
        }

        int rowSize = this.getRowSize();
        int row = idx / rowSize;
        int col = idx % rowSize;

        // 第一行：只显示 activeCrafting(不受 scroll 影响)
        if (row == 0) {
            if (col < this.activeCrafting.size()) {
                return this.activeCrafting.get(col).output;
            }
            return null;
        }

        // 第二行及以后：从 normalView 按 scroll 偏移获取
        int scrollOffset = this.scrollSrc.getCurrentScroll() * rowSize;
        int normalIdx = scrollOffset + (idx - rowSize);

        if (normalIdx < 0 || normalIdx >= this.normalView.size()) {
            return null;
        }
        return this.normalView.get(normalIdx);
    }

    private boolean isInActiveCrafting(IAEItemStack stack) {
        for (CraftingStatus status : this.activeCrafting) {
            if (status.output.equals(stack)) {
                return true;
            }
        }
        return false;
    }
}

package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;
import appeng.core.AEConfig;
import appeng.integration.Integrations;
import appeng.integration.modules.bogosorter.InventoryBogoSortModule;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniInventoryUpdate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 支持合成置顶的 ItemRepo，同时为 Omni Terminal 提供增量渲染、ID 缓存、
 * 搜索预处理缓存、视图快照增量更新支持。
 *
 * <p>核心优化：
 * <ul>
 *   <li>覆盖 updateView()，消除 addIAE() 中每次遍历都编译正则的致命性能问题</li>
 *   <li>视图快照：相同搜索/排序/显示模式下只更新数量，不重建列表</li>
 *   <li>批量加载延迟更新：FULL_CONTINUE 期间不频繁重建 view</li>
 * </ul>
 */
public class OmniItemRepo extends ItemRepo {

    // ==================== 反射字段 ====================
    private static final Field VIEW_FIELD;
    private static final Field LIST_FIELD;
    private static final Field CHANGED_FIELD;
    private static final Field RESORT_FIELD;
    private static final Field SORT_SRC_FIELD;
    private static final Field SEARCH_STRING_FIELD;
    private static final Field MY_PARTITION_LIST_FIELD;
    private static final Field LAST_VIEW_FIELD;
    private static final Field LAST_SEARCH_MODE_FIELD;
    private static final Field LAST_SORT_BY_FIELD;
    private static final Field LAST_SORT_DIR_FIELD;
    private static final Field LAST_SEARCH_FIELD;

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
            SEARCH_STRING_FIELD = ItemRepo.class.getDeclaredField("searchString");
            SEARCH_STRING_FIELD.setAccessible(true);
            MY_PARTITION_LIST_FIELD = ItemRepo.class.getDeclaredField("myPartitionList");
            MY_PARTITION_LIST_FIELD.setAccessible(true);
            LAST_VIEW_FIELD = ItemRepo.class.getDeclaredField("lastView");
            LAST_VIEW_FIELD.setAccessible(true);
            LAST_SEARCH_MODE_FIELD = ItemRepo.class.getDeclaredField("lastSearchMode");
            LAST_SEARCH_MODE_FIELD.setAccessible(true);
            LAST_SORT_BY_FIELD = ItemRepo.class.getDeclaredField("lastSortBy");
            LAST_SORT_BY_FIELD.setAccessible(true);
            LAST_SORT_DIR_FIELD = ItemRepo.class.getDeclaredField("lastSortDir");
            LAST_SORT_DIR_FIELD.setAccessible(true);
            LAST_SEARCH_FIELD = ItemRepo.class.getDeclaredField("lastSearch");
            LAST_SEARCH_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access ItemRepo fields", e);
        }
    }

    // ==================== 原有字段 ====================
    private final IScrollSource scrollSrc;
    private final OmniItemRegistry registry = new OmniItemRegistry();
    private List<CraftingStatus> activeCrafting = Collections.emptyList();
    private List<IAEItemStack> normalView = Collections.emptyList();

    // ==================== V3 新增：搜索缓存 ====================
    private static class SearchCache {
        String raw = "";
        String lower = "";
        String[] terms = new String[0];
        Pattern pattern = null;
        boolean isModSearch = false;
        boolean valid = false;

        void rebuild(String searchString) {
            if (searchString.equals(raw)) {
                return;
            }
            raw = searchString;
            lower = searchString.toLowerCase();
            isModSearch = lower.startsWith("@");
            if (isModSearch) {
                lower = lower.substring(1);
            }
            terms = lower.isEmpty() ? new String[0] : lower.split(" ");
            try {
                pattern = Pattern.compile(lower, Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                pattern = Pattern.compile(Pattern.quote(lower), Pattern.CASE_INSENSITIVE);
            }
            valid = !raw.isEmpty();
        }
    }

    private final SearchCache searchCache = new SearchCache();

    // ==================== V3 新增：视图快照 ====================
    private static class ViewSnapshot {
        List<IAEItemStack> view = new ArrayList<>();
        String searchString = "";
        Enum<?> sortBy = null;
        Enum<?> sortDir = null;
        Enum<?> viewMode = null;
        Enum<?> searchMode = null;
    }

    private final ViewSnapshot viewSnapshot = new ViewSnapshot();

    // ==================== V3 新增：批量加载状态 ====================
    private boolean isBulkLoading = false;

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

    public void setBulkLoading(boolean loading) {
        this.isBulkLoading = loading;
    }

    public boolean isBulkLoading() {
        return this.isBulkLoading;
    }

    // ==================== Omni Terminal 自定义同步处理 ====================

    public void handleFullInit(List<PacketOmniInventoryUpdate.Entry> entries) {
        this.registry.clear();
        clearList();
        this.viewSnapshot.view.clear();
        this.normalView.clear();

        for (PacketOmniInventoryUpdate.Entry e : entries) {
            this.registry.register(e.id, e.stack, e.count);
            if (e.count > 0) {
                this.postUpdate(e.stack.copy());
            }
        }

        setChanged(true);
        // FULL_INIT 需要立即显示，不走延迟
        this.updateView();
    }

    public void handleFullContinue(List<PacketOmniInventoryUpdate.Entry> entries) {
        for (PacketOmniInventoryUpdate.Entry e : entries) {
            this.registry.register(e.id, e.stack, e.count);
            if (e.count > 0) {
                this.postUpdate(e.stack.copy());
            }
        }
        setChanged(true);
        // 批量加载期间延迟更新
        if (!this.isBulkLoading) {
            this.updateView();
        }
    }

    public void handleItemRegister(int id, IAEItemStack stack) {
        this.registry.register(id, stack, 0);
    }

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
            if (!this.isBulkLoading) {
                updateViewIfNeeded();
            }
        }
    }

    /**
     * 条件渲染：需要重建时才重建，否则只更新数量
     */
    public void updateViewIfNeeded() {
        try {
            boolean resort = (boolean) RESORT_FIELD.get(this);
            if (resort || isSortedByAmount()) {
                this.updateView();
            } else {
                updateViewCountsOnly();
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 仅更新 viewSnapshot.view 中各元素的数量，不重建列表结构。
     */
    private void updateViewCountsOnly() {
        // 1. 更新 viewSnapshot.view 中每个元素的数量
        for (IAEItemStack viewStack : this.viewSnapshot.view) {
            try {
                @SuppressWarnings("unchecked")
                appeng.api.storage.data.IItemList<IAEItemStack> list =
                        (appeng.api.storage.data.IItemList<IAEItemStack>) LIST_FIELD.get(this);
                IAEItemStack real = list.findPrecise(viewStack);
                if (real != null) {
                    viewStack.setStackSize(real.getStackSize());
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        // 2. 更新 activeCrafting 数量
        for (CraftingStatus status : this.activeCrafting) {
            try {
                @SuppressWarnings("unchecked")
                appeng.api.storage.data.IItemList<IAEItemStack> list =
                        (appeng.api.storage.data.IItemList<IAEItemStack>) LIST_FIELD.get(this);
                IAEItemStack real = list.findPrecise(status.output);
                if (real != null) {
                    status.output.setStackSize(real.getStackSize());
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        // 3. normalView 持有 viewSnapshot.view 的引用，数量已同步更新
        // 4. 同步到父类 view 字段
        try {
            VIEW_FIELD.set(this, this.viewSnapshot.view);
            CHANGED_FIELD.set(this, false);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== V3 核心：完全覆盖 updateView() ====================

    @Override
    public void updateView() {
        try {
            ISortSource sortSrc = (ISortSource) SORT_SRC_FIELD.get(this);
            Enum<?> viewMode = sortSrc.getSortDisplay();
            Enum<?> searchMode = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
            Enum<?> sortBy = sortSrc.getSortBy();
            Enum<?> sortDir = sortSrc.getSortDir();
            String currentSearch = (String) SEARCH_STRING_FIELD.get(this);

            // 1. JEI 搜索同步
            if (searchMode == SearchBoxMode.JEI_AUTOSEARCH || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH
                    || searchMode == SearchBoxMode.JEI_AUTOSEARCH_KEEP || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP) {
                if (Integrations.jei() != null) {
                    Integrations.jei().setSearchText(currentSearch);
                }
            }

            // 2. 更新搜索缓存（只做一次）
            searchCache.rebuild(currentSearch);

            // 3. 判断是否需要全量重建
            boolean needRebuild = viewSnapshot.viewMode != viewMode
                    || viewSnapshot.sortBy != sortBy
                    || viewSnapshot.sortDir != sortDir
                    || viewSnapshot.searchMode != searchMode
                    || !viewSnapshot.searchString.equals(currentSearch);

            boolean changed = (boolean) CHANGED_FIELD.get(this);
            boolean resort = (boolean) RESORT_FIELD.get(this);

            if (!needRebuild && !changed && !resort) {
                return; // 无任何变化
            }

            if (!needRebuild && changed && !resort) {
                // 只有数量变化，且不是按数量排序：走增量更新
                if (sortBy != SortOrder.AMOUNT) {
                    updateViewCountsOnly();
                    return;
                }
            }

            // 4. 全量重建
            @SuppressWarnings("unchecked")
            appeng.api.storage.data.IItemList<IAEItemStack> list =
                    (appeng.api.storage.data.IItemList<IAEItemStack>) LIST_FIELD.get(this);

            @SuppressWarnings("unchecked")
            IPartitionList<IAEItemStack> myPartitionList =
                    (IPartitionList<IAEItemStack>) MY_PARTITION_LIST_FIELD.get(this);

            List<IAEItemStack> newView = new ArrayList<>();
            boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;
            boolean terminalSearchToolTips = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;

            // 4a. 名字匹配阶段
            List<IAEItemStack> nameMisses = terminalSearchToolTips && searchCache.valid ? new ArrayList<>() : null;

            for (IAEItemStack is : list) {
                if (myPartitionList != null && !myPartitionList.isListed(is)) {
                    continue;
                }
                if (viewMode == ViewItems.CRAFTABLE && !is.isCraftable()) {
                    continue;
                }
                if (viewMode == ViewItems.STORED && is.getStackSize() == 0L) {
                    continue;
                }

                if (!searchCache.valid) {
                    // 空搜索，直接通过
                    addToView(newView, is, needsZeroCopy);
                } else if (matchesName(is)) {
                    addToView(newView, is, needsZeroCopy);
                } else if (nameMisses != null) {
                    nameMisses.add(is);
                }
            }

            // 4b. tooltip 回退阶段（仅在名字全未匹配且开启 tooltip 搜索时）
            if (nameMisses != null && newView.isEmpty() && !nameMisses.isEmpty()) {
                for (IAEItemStack is : nameMisses) {
                    if (matchesTooltip(is)) {
                        addToView(newView, is, needsZeroCopy);
                    }
                }
            }

            // 5. 排序
            Comparator<IAEItemStack> c = getComparator(sortBy);
            ItemSorters.setDirection((SortDir) sortDir);
            ItemSorters.init();
            newView.sort(c);

            // 6. 更新快照
            viewSnapshot.view = newView;
            viewSnapshot.searchString = currentSearch;
            viewSnapshot.sortBy = sortBy;
            viewSnapshot.sortDir = sortDir;
            viewSnapshot.viewMode = viewMode;
            viewSnapshot.searchMode = searchMode;

            // 7. 同步到父类字段
            VIEW_FIELD.set(this, newView);
            CHANGED_FIELD.set(this, false);
            RESORT_FIELD.set(this, false);
            LAST_VIEW_FIELD.set(this, viewMode);
            LAST_SEARCH_MODE_FIELD.set(this, searchMode);
            LAST_SORT_BY_FIELD.set(this, sortBy);
            LAST_SORT_DIR_FIELD.set(this, sortDir);
            LAST_SEARCH_FIELD.set(this, currentSearch);

            // 8. OmniItemRepo 特有的 activeCrafting 处理
            updateActiveCraftingAndNormalView(newView);

        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void addToView(List<IAEItemStack> view, IAEItemStack is, boolean needsZeroCopy) {
        if (needsZeroCopy) {
            IAEItemStack copy = is.copy();
            copy.setStackSize(0L);
            view.add(copy);
        } else {
            view.add(is);
        }
    }

    private boolean matchesName(IAEItemStack is) {
        String dspName = (searchCache.isModSearch ? Platform.getModId(is) : Platform.getItemDisplayName(is)).toLowerCase();
        for (String term : searchCache.terms) {
            if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                if (!dspName.contains(term.substring(1))) {
                    continue;
                }
                return false;
            }
            if (dspName.contains(term)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean matchesTooltip(IAEItemStack is) {
        if (searchCache.pattern == null) {
            return false;
        }
        List<String> tooltip = Platform.getTooltip(is);
        for (String line : tooltip) {
            if (searchCache.pattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private static Comparator<IAEItemStack> getComparator(Enum<?> sortBy) {
        if (sortBy == SortOrder.MOD) {
            return ItemSorters.CONFIG_BASED_SORT_BY_MOD;
        } else if (sortBy == SortOrder.AMOUNT) {
            return ItemSorters.CONFIG_BASED_SORT_BY_SIZE;
        } else if (sortBy == SortOrder.INVTWEAKS) {
            return InventoryBogoSortModule.isLoaded()
                    ? InventoryBogoSortModule.COMPARATOR
                    : ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS;
        }
        return ItemSorters.CONFIG_BASED_SORT_BY_NAME;
    }

    private void updateActiveCraftingAndNormalView(List<IAEItemStack> view) {
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
    }

    // ==================== 辅助方法 ====================

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

    private boolean isSortedByAmount() {
        try {
            ISortSource src = (ISortSource) SORT_SRC_FIELD.get(this);
            return src.getSortBy() == SortOrder.AMOUNT;
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

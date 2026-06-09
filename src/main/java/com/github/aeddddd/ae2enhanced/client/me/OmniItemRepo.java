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
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniInventoryUpdate;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Omni Terminal 专用 ItemRepo，提供根本性能优化：
 * <ul>
 *   <li><b>扁平化 ArrayList</b>：绕过 IItemList 多层迭代器嵌套的性能灾难</li>
 *   <li><b>倒排索引</b>：将搜索从 O(N) 降到 O(匹配数)</li>
 *   <li><b>后台线程 + 双缓冲</b>：主线程永远不执行视图计算</li>
 *   <li><b>视图快照</b>：相同配置下只更新数量，不重建列表</li>
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
    private boolean isBulkLoading = false;

    // ==================== V4：扁平化缓存 ====================
    private final List<IAEItemStack> flatList = new ArrayList<>();
    private final Object2IntOpenHashMap<IAEItemStack> flatIndex = new Object2IntOpenHashMap<>();
    private long flatListVersion = 0;

    // ==================== V4：倒排索引 ====================
    private final Object2ObjectOpenHashMap<String, IntList> nameIndex = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, IntList> modIndex = new Object2ObjectOpenHashMap<>();

    // ==================== V4：双缓冲 ====================
    private volatile List<IAEItemStack> renderView = new ArrayList<>();
    private final ExecutorService viewExecutor = Executors.newSingleThreadExecutor(
            r -> {
                Thread t = new Thread(r, "AE2E-OmniView-Worker");
                t.setDaemon(true);
                return t;
            }
    );
    private final AtomicBoolean viewUpdatePending = new AtomicBoolean(false);
    private volatile long renderViewVersion = 0;

    // ==================== V3：搜索缓存 ====================
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

    // ==================== V3：视图快照 ====================
    private static class ViewSnapshot {
        List<IAEItemStack> view = new ArrayList<>();
        String searchString = "";
        Enum<?> sortBy = null;
        Enum<?> sortDir = null;
        Enum<?> viewMode = null;
        Enum<?> searchMode = null;
    }

    private final ViewSnapshot viewSnapshot = new ViewSnapshot();

    public OmniItemRepo(IScrollSource src, ISortSource sortSrc) {
        super(src, sortSrc);
        this.scrollSrc = src;
    }

    // ==================== 生命周期 ====================

    public void shutdown() {
        this.viewExecutor.shutdownNow();
    }

    // ==================== 公共访问器 ====================

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

    public long getRenderViewVersion() {
        return this.renderViewVersion;
    }

    // ==================== 同步处理 ====================

    public void handleFullInit(List<PacketOmniInventoryUpdate.Entry> entries) {
        this.registry.clear();
        clearList();
        this.flatList.clear();
        this.flatIndex.clear();
        this.viewSnapshot.view.clear();
        this.normalView.clear();

        for (PacketOmniInventoryUpdate.Entry e : entries) {
            this.registry.register(e.id, e.stack, e.count);
            if (e.count > 0) {
                this.postUpdate(e.stack.copy());
            }
        }

        syncFlatList();
        setChanged(true);
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
        syncFlatList();
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
            syncFlatList();
            if (!this.isBulkLoading) {
                updateViewIfNeeded();
            }
        }
    }

    // ==================== flatList 同步 ====================

    private void syncFlatList() {
        try {
            @SuppressWarnings("unchecked")
            appeng.api.storage.data.IItemList<IAEItemStack> list =
                    (appeng.api.storage.data.IItemList<IAEItemStack>) LIST_FIELD.get(this);

            this.flatList.clear();
            this.flatIndex.clear();
            int idx = 0;
            for (IAEItemStack stack : list) {
                if (stack.isMeaningful()) {
                    this.flatList.add(stack);
                    this.flatIndex.put(stack, idx++);
                }
            }
            this.flatListVersion++;
            rebuildIndex();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void rebuildIndex() {
        this.nameIndex.clear();
        this.modIndex.clear();

        for (int i = 0; i < this.flatList.size(); i++) {
            IAEItemStack stack = this.flatList.get(i);
            String name = Platform.getItemDisplayName(stack).toLowerCase();
            String modId = Platform.getModId(stack).toLowerCase();

            for (String word : splitWords(name)) {
                this.nameIndex.computeIfAbsent(word, k -> new IntArrayList()).add(i);
            }

            this.modIndex.computeIfAbsent(modId, k -> new IntArrayList()).add(i);
        }
    }

    /**
     * 将物品名称分词，支持：空格分隔、下划线分隔、驼峰拆分
     */
    private static List<String> splitWords(String input) {
        List<String> words = new ArrayList<>();
        if (input == null || input.isEmpty()) return words;

        // 按非字母数字字符分割
        String[] tokens = input.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.length() <= 1) continue;
            words.add(token);

            // 驼峰拆分：IronSword → iron, sword
            if (token.length() > 2) {
                StringBuilder current = new StringBuilder();
                current.append(token.charAt(0));
                for (int i = 1; i < token.length(); i++) {
                    char c = token.charAt(i);
                    if (Character.isUpperCase(c)) {
                        String word = current.toString();
                        if (word.length() > 1) {
                            words.add(word);
                        }
                        current = new StringBuilder();
                    }
                    current.append(Character.toLowerCase(c));
                }
                String last = current.toString();
                if (last.length() > 1 && !last.equals(token)) {
                    words.add(last);
                }
            }
        }
        return words;
    }

    // ==================== V4 核心：后台线程 + 双缓冲 ====================

    @Override
    public void updateView() {
        if (this.viewUpdatePending.compareAndSet(false, true)) {
            this.viewExecutor.submit(this::computeView);
        }
    }

    private void computeView() {
        try {
            ISortSource sortSrc = (ISortSource) SORT_SRC_FIELD.get(this);
            Enum<?> viewMode = sortSrc.getSortDisplay();
            Enum<?> searchMode = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
            Enum<?> sortBy = sortSrc.getSortBy();
            Enum<?> sortDir = sortSrc.getSortDir();
            String currentSearch = (String) SEARCH_STRING_FIELD.get(this);

            // JEI 同步
            if (Integrations.jei() != null && isJEISearchMode(searchMode)) {
                Integrations.jei().setSearchText(currentSearch);
            }

            // 搜索缓存
            searchCache.rebuild(currentSearch);

            // 判断是否需要全量重建
            boolean needRebuild = this.viewSnapshot.viewMode != viewMode
                    || this.viewSnapshot.sortBy != sortBy
                    || this.viewSnapshot.sortDir != sortDir
                    || this.viewSnapshot.searchMode != searchMode
                    || !this.viewSnapshot.searchString.equals(currentSearch);

            boolean changed = (boolean) CHANGED_FIELD.get(this);
            boolean resort = (boolean) RESORT_FIELD.get(this);

            if (!needRebuild && !changed && !resort) {
                return;
            }

            List<IAEItemStack> newView;

            if (!needRebuild && changed && !resort && sortBy != SortOrder.AMOUNT) {
                // 只有数量变化，非按数量排序：复用现有 view，只更新数量
                newView = new ArrayList<>(this.viewSnapshot.view.size());
                for (IAEItemStack old : this.viewSnapshot.view) {
                    int idx = this.flatIndex.getInt(old);
                    if (idx >= 0) {
                        IAEItemStack real = this.flatList.get(idx);
                        newView.add(real.copy());
                    }
                }
            } else {
                // 需要全量重建
                newView = buildFullView(viewMode, sortBy, sortDir);
            }

            // 更新快照
            this.viewSnapshot.view = newView;
            this.viewSnapshot.searchString = currentSearch;
            this.viewSnapshot.sortBy = sortBy;
            this.viewSnapshot.sortDir = sortDir;
            this.viewSnapshot.viewMode = viewMode;
            this.viewSnapshot.searchMode = searchMode;

            // 双缓冲原子替换
            this.renderView = newView;
            this.renderViewVersion = this.flatListVersion;

            // 同步到父类字段
            VIEW_FIELD.set(this, newView);
            CHANGED_FIELD.set(this, false);
            RESORT_FIELD.set(this, false);
            LAST_VIEW_FIELD.set(this, viewMode);
            LAST_SEARCH_MODE_FIELD.set(this, searchMode);
            LAST_SORT_BY_FIELD.set(this, sortBy);
            LAST_SORT_DIR_FIELD.set(this, sortDir);
            LAST_SEARCH_FIELD.set(this, currentSearch);

            // activeCrafting 处理
            updateActiveCraftingAndNormalView(newView);

        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] View computation failed", e);
        } finally {
            this.viewUpdatePending.set(false);
        }
    }

    private List<IAEItemStack> buildFullView(Enum<?> viewMode, Enum<?> sortBy, Enum<?> sortDir) {
        // 1. 搜索过滤（使用索引）
        List<IAEItemStack> candidates;
        if (!searchCache.valid) {
            candidates = new ArrayList<>(this.flatList);
        } else if (searchCache.isModSearch) {
            candidates = searchByMod(searchCache.lower);
        } else {
            candidates = searchByNameIndex(searchCache);
        }

        // 2. ViewMode 过滤
        List<IAEItemStack> filtered = new ArrayList<>();
        boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;
        for (IAEItemStack stack : candidates) {
            if (viewMode == ViewItems.CRAFTABLE && !stack.isCraftable()) continue;
            if (viewMode == ViewItems.STORED && stack.getStackSize() == 0L) continue;
            if (needsZeroCopy) {
                IAEItemStack copy = stack.copy();
                copy.setStackSize(0L);
                filtered.add(copy);
            } else {
                filtered.add(stack);
            }
        }

        // 3. 排序
        Comparator<IAEItemStack> c = getComparator(sortBy);
        ItemSorters.setDirection((SortDir) sortDir);
        ItemSorters.init();
        filtered.sort(c);

        return filtered;
    }

    // ==================== 搜索索引查询 ====================

    private List<IAEItemStack> searchByNameIndex(SearchCache cache) {
        if (cache.terms.length == 0) {
            return new ArrayList<>(this.flatList);
        }

        IntList[] sets = new IntList[cache.terms.length];
        int minSize = Integer.MAX_VALUE;
        int minIdx = 0;

        for (int i = 0; i < cache.terms.length; i++) {
            sets[i] = this.nameIndex.get(cache.terms[i]);
            if (sets[i] == null) {
                return Collections.emptyList();
            }
            if (sets[i].size() < minSize) {
                minSize = sets[i].size();
                minIdx = i;
            }
        }

        IntSet result = new IntOpenHashSet(sets[minIdx]);
        for (int i = 0; i < sets.length; i++) {
            if (i == minIdx) continue;
            result.retainAll(sets[i]);
            if (result.isEmpty()) {
                return Collections.emptyList();
            }
        }

        List<IAEItemStack> matches = new ArrayList<>(result.size());
        for (int idx : result) {
            IAEItemStack stack = this.flatList.get(idx);
            if (matchesName(stack)) {
                matches.add(stack);
            }
        }
        return matches;
    }

    private List<IAEItemStack> searchByMod(String modSearch) {
        IntList indices = this.modIndex.get(modSearch);
        if (indices == null) {
            return Collections.emptyList();
        }
        List<IAEItemStack> matches = new ArrayList<>(indices.size());
        for (int i = 0; i < indices.size(); i++) {
            matches.add(this.flatList.get(indices.getInt(i)));
        }
        return matches;
    }

    private boolean matchesName(IAEItemStack is) {
        String dspName = (searchCache.isModSearch ? Platform.getModId(is) : Platform.getItemDisplayName(is)).toLowerCase();
        for (String term : searchCache.terms) {
            if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                if (!dspName.contains(term.substring(1))) continue;
                return false;
            }
            if (dspName.contains(term)) continue;
            return false;
        }
        return true;
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

    private static boolean isJEISearchMode(Enum<?> mode) {
        return mode == SearchBoxMode.JEI_AUTOSEARCH
                || mode == SearchBoxMode.JEI_MANUAL_SEARCH
                || mode == SearchBoxMode.JEI_AUTOSEARCH_KEEP
                || mode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP;
    }

    // ==================== 增量更新（数量变化时）====================

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

    private void updateViewCountsOnly() {
        List<IAEItemStack> newView = new ArrayList<>(this.viewSnapshot.view.size());
        for (IAEItemStack old : this.viewSnapshot.view) {
            int idx = this.flatIndex.getInt(old);
            if (idx >= 0) {
                newView.add(this.flatList.get(idx).copy());
            }
        }

        this.viewSnapshot.view = newView;
        this.renderView = newView;
        this.renderViewVersion = this.flatListVersion;

        try {
            VIEW_FIELD.set(this, newView);
            CHANGED_FIELD.set(this, false);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // activeCrafting 数量同步
        for (CraftingStatus status : this.activeCrafting) {
            int idx = this.flatIndex.getInt(status.output);
            if (idx >= 0) {
                status.output.setStackSize(this.flatList.get(idx).getStackSize());
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

    private void updateActiveCraftingAndNormalView(List<IAEItemStack> view) {
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

    private IAEItemStack findInView(IAEItemStack target, List<IAEItemStack> view) {
        for (IAEItemStack stack : view) {
            if (stack.equals(target)) {
                return stack;
            }
        }
        return null;
    }

    // ==================== 渲染接口 ====================

    @Override
    public IAEItemStack getReferenceItem(int idx) {
        if (this.activeCrafting.isEmpty()) {
            List<IAEItemStack> view = this.renderView;
            int scrollOffset = this.scrollSrc.getCurrentScroll() * this.getRowSize();
            int actualIdx = scrollOffset + idx;
            if (actualIdx >= 0 && actualIdx < view.size()) {
                return view.get(actualIdx);
            }
            return null;
        }

        int rowSize = this.getRowSize();
        int row = idx / rowSize;
        int col = idx % rowSize;

        if (row == 0) {
            if (col < this.activeCrafting.size()) {
                return this.activeCrafting.get(col).output;
            }
            return null;
        }

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

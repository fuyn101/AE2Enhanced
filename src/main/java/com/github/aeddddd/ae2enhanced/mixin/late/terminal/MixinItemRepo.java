package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.config.Settings;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.me.ItemRepo;
import appeng.core.AEConfig;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 优化 ItemRepo：
 * 1. addIAE() 空搜索快速路径：跳过 getDisplayName / Pattern.compile / 字符串匹配等全部过滤逻辑
 * 2. Pattern.compile 缓存：同一 tick 内只编译一次正则
 * 3. updateView() 并行排序：用 Arrays.parallelSort 替代 ArrayList.sort，加速 50 万+ 物品的排序
 */
@Mixin(value = ItemRepo.class, remap = false)
public class MixinItemRepo {

    @Shadow
    private String searchString;

    @Shadow
    private String innerSearch;

    @Shadow
    private IPartitionList<IAEItemStack> myPartitionList;

    @Shadow
    private List<IAEItemStack> view;

    @Unique
    private Pattern ae2enhanced$cachedPattern;

    @Unique
    private String ae2enhanced$cachedPatternString;

    /**
     * 空搜索快速路径 + Pattern 缓存。
     * 当搜索框为空时，直接跳过所有过滤逻辑（getDisplayName、toLowerCase、Pattern.compile、
     * split/contains 遍历），只保留 viewMode / partitionList 的基础过滤。
     * 这是 50 万+ 物品场景下最显著的优化，因为大部分时候玩家不输入搜索词。
     */
    /**
     * @author AE2Enhanced
     * @reason 空搜索快速路径 + Pattern 缓存，避免 50 万+ 物品时每次重建都遍历全部过滤逻辑
     */
    @Overwrite
    private void addIAE(IAEItemStack is, Enum viewMode) {
        // 空搜索快速路径
        if (this.searchString == null || this.searchString.isEmpty()) {
            if (this.myPartitionList != null && !this.myPartitionList.isListed(is)) {
                return;
            }
            if (viewMode == ViewItems.CRAFTABLE && !is.isCraftable()) {
                return;
            }
            if (viewMode == ViewItems.STORED && is.getStackSize() == 0L) {
                return;
            }
            if (viewMode == ViewItems.CRAFTABLE) {
                is = is.copy();
                is.setStackSize(0L);
            }
            this.view.add(is);
            return;
        }

        // 原有逻辑（搜索非空时）
        boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;
        boolean terminalSearchToolTips = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;
        boolean searchMod = false;
        this.innerSearch = this.searchString.toLowerCase();
        if (this.innerSearch.startsWith("@")) {
            searchMod = true;
            this.innerSearch = this.innerSearch.substring(1);
        }

        // Pattern 缓存
        Pattern m;
        if (ae2enhanced$cachedPattern != null && this.innerSearch.equals(ae2enhanced$cachedPatternString)) {
            m = ae2enhanced$cachedPattern;
        } else {
            try {
                m = Pattern.compile(this.innerSearch, 2);
                ae2enhanced$cachedPatternString = this.innerSearch;
                ae2enhanced$cachedPattern = m;
            } catch (Throwable ignore) {
                try {
                    m = Pattern.compile(Pattern.quote(this.innerSearch), 2);
                } catch (Throwable __) {
                    return;
                }
            }
        }

        if (this.myPartitionList != null && !this.myPartitionList.isListed(is)) {
            return;
        }
        if (viewMode == ViewItems.CRAFTABLE && !is.isCraftable()) {
            return;
        }
        if (viewMode == ViewItems.STORED && is.getStackSize() == 0L) {
            return;
        }

        String dspName = (searchMod ? Platform.getModId((IAEItemStack) is) : Platform.getItemDisplayName((Object) is)).toLowerCase();
        boolean foundMatchingItemStack = true;
        for (String term : this.innerSearch.split(" ")) {
            if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                if (!dspName.contains(term = term.substring(1))) continue;
                foundMatchingItemStack = false;
                break;
            }
            if (dspName.contains(term)) continue;
            foundMatchingItemStack = false;
            break;
        }
        if (terminalSearchToolTips && !foundMatchingItemStack) {
            List tooltip = Platform.getTooltip((Object) is);
            for (Object line : tooltip) {
                if (!m.matcher((String) line).find()) continue;
                foundMatchingItemStack = true;
                break;
            }
        }
        if (foundMatchingItemStack) {
            if (needsZeroCopy) {
                is = is.copy();
                is.setStackSize(0L);
            }
            this.view.add(is);
        }
    }

    /**
     * 用并行排序替代 ArrayList.sort，加速 50 万+ 物品的排序。
     * view 在 updateView() 中会被完全替换，因此使用 Arrays.asList 包装排序后的数组是安全的。
     */
    @Redirect(
            method = "updateView",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"
            )
    )
    private void ae2enhanced$parallelSort(List<IAEItemStack> list, Comparator<IAEItemStack> c) {
        IAEItemStack[] array = list.toArray(new IAEItemStack[0]);
        Arrays.parallelSort(array, c);
        this.view = Arrays.asList(array);
    }
}

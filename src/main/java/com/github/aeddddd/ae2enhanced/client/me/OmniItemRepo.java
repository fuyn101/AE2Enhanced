package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 支持合成置顶的 ItemRepo。
 * activeCrafting 物品独占第一行（不受滚动影响），其余物品从第二行开始。
 */
public class OmniItemRepo extends ItemRepo {

    private static final Field VIEW_FIELD;
    private List<IAEItemStack> activeCrafting = Collections.emptyList();
    private List<IAEItemStack> normalView = Collections.emptyList();

    static {
        try {
            VIEW_FIELD = ItemRepo.class.getDeclaredField("view");
            VIEW_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access ItemRepo.view", e);
        }
    }

    private final IScrollSource scrollSrc;

    public OmniItemRepo(IScrollSource src, ISortSource sortSrc) {
        super(src, sortSrc);
        this.scrollSrc = src;
    }

    public void setActiveCrafting(List<IAEItemStack> list) {
        this.activeCrafting = list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    public List<IAEItemStack> getActiveCrafting() {
        return this.activeCrafting;
    }

    @Override
    public void updateView() {
        super.updateView();
        try {
            @SuppressWarnings("unchecked")
            List<IAEItemStack> view = (List<IAEItemStack>) VIEW_FIELD.get(this);
            // 将 activeCrafting 的数量同步为 view 中的实际存储数量
            for (IAEItemStack active : this.activeCrafting) {
                IAEItemStack real = findInView(active, view);
                if (real != null) {
                    active.setStackSize(real.getStackSize());
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

        // 第一行：只显示 activeCrafting（不受 scroll 影响）
        if (row == 0) {
            if (col < this.activeCrafting.size()) {
                return this.activeCrafting.get(col);
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
        for (IAEItemStack active : this.activeCrafting) {
            if (active.equals(stack)) {
                return true;
            }
        }
        return false;
    }
}

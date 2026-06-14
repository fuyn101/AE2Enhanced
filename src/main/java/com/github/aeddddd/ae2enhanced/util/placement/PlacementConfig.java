package com.github.aeddddd.ae2enhanced.util.placement;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 放置工具配置 —— 管理 18 个幽灵槽、当前选中槽、批量放置数量。
 * 所有数据存储在 ItemStack NBT 的 AE2E_Placement 标签下。
 */
public class PlacementConfig {

    public static final String NBT_ROOT = "AE2E_Placement";
    public static final String NBT_ITEMS = "items";
    public static final String NBT_SELECTED_SLOT = "selectedSlot";
    public static final String NBT_PLACEMENT_COUNT = "placementCount";

    public static final int TOTAL_SLOTS = 18;
    public static final int SLOTS_PER_PAGE = 9;
    public static final int MAX_PAGES = TOTAL_SLOTS / SLOTS_PER_PAGE;

    public static final int[] PLACEMENT_COUNTS = {1, 8, 64, 256, 1024};

    private final ItemStack stack;
    private final NBTTagCompound root;

    public PlacementConfig(ItemStack stack) {
        this.stack = stack;
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_ROOT)) {
            tag.setTag(NBT_ROOT, new NBTTagCompound());
        }
        this.root = tag.getCompoundTag(NBT_ROOT);
    }

    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOTS) return ItemStack.EMPTY;
        NBTTagList list = root.getTagList(NBT_ITEMS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getByte("Slot") == slot) {
                return new ItemStack(tag);
            }
        }
        return ItemStack.EMPTY;
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= TOTAL_SLOTS) return;
        NBTTagList list = root.getTagList(NBT_ITEMS, 10);
        // 查找并移除旧槽位
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getByte("Slot") == slot) {
                list.removeTag(i);
                break;
            }
        }
        // 写入新槽位
        if (!stack.isEmpty()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("Slot", (byte) slot);
            stack.writeToNBT(tag);
            list.appendTag(tag);
        }
        root.setTag(NBT_ITEMS, list);
    }

    public int getSelectedSlot() {
        return root.getInteger(NBT_SELECTED_SLOT);
    }

    public void setSelectedSlot(int slot) {
        if (slot < 0) slot = 0;
        if (slot >= TOTAL_SLOTS) slot = TOTAL_SLOTS - 1;
        root.setInteger(NBT_SELECTED_SLOT, slot);
    }

    public int getPlacementCountIndex() {
        return root.getInteger(NBT_PLACEMENT_COUNT);
    }

    public void setPlacementCountIndex(int index) {
        if (index < 0) index = 0;
        if (index >= PLACEMENT_COUNTS.length) index = PLACEMENT_COUNTS.length - 1;
        root.setInteger(NBT_PLACEMENT_COUNT, index);
    }

    public int getPlacementCount() {
        int idx = getPlacementCountIndex();
        if (idx < 0 || idx >= PLACEMENT_COUNTS.length) {
            idx = 0;
            setPlacementCountIndex(idx);
        }
        return PLACEMENT_COUNTS[idx];
    }

    public int getCurrentPage() {
        return getSelectedSlot() / SLOTS_PER_PAGE;
    }

    public void setCurrentPage(int page) {
        if (page < 0) page = 0;
        if (page >= MAX_PAGES) page = MAX_PAGES - 1;
        setSelectedSlot(page * SLOTS_PER_PAGE);
    }

    public int getVisualSlotForPage(int page, int visualIndex) {
        return page * SLOTS_PER_PAGE + visualIndex;
    }

    public boolean isRootEmpty() {
        return root.getSize() == 0;
    }

    public static boolean hasConfig(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_ROOT);
    }
}

package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 物品的轻量描述符,用于在 AE2S 下替代旧的 AEItemKey 聚合 key.
 * 仅比较 item + metadata + NBT,不携带数量.
 */
public final class ItemDescriptor {

    private final Item item;
    private final int metadata;
    private final NBTTagCompound nbt;

    public ItemDescriptor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            this.item = null;
            this.metadata = 0;
            this.nbt = null;
        } else {
            this.item = stack.getItem();
            this.metadata = stack.getMetadata();
            this.nbt = stack.hasTagCompound() ? stack.getTagCompound().copy() : null;
        }
    }

    public ItemDescriptor(Item item, int metadata, @Nullable NBTTagCompound nbt) {
        this.item = item;
        this.metadata = metadata;
        this.nbt = nbt != null ? nbt.copy() : null;
    }

    @Nullable
    public Item getItem() {
        return item;
    }

    public int getMetadata() {
        return metadata;
    }

    @Nullable
    public NBTTagCompound getNbt() {
        return nbt;
    }

    public boolean isEmpty() {
        return item == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemDescriptor)) return false;
        ItemDescriptor that = (ItemDescriptor) o;
        return metadata == that.metadata
                && Objects.equals(item, that.item)
                && Objects.equals(nbt, that.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, metadata, nbt);
    }

    @Override
    public String toString() {
        return "ItemDescriptor{" + item + "@" + metadata + "}";
    }
}

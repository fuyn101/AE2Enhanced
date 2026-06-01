package com.github.aeddddd.ae2enhanced.platform.key;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable key for ItemStack deduplication.
 */
public final class ItemStackKey {

    public final Item item;
    public final int meta;
    @Nullable
    public final NBTTagCompound nbt;
    private final int hash;

    private ItemStackKey(@Nonnull Item item, int meta, @Nullable NBTTagCompound nbt) {
        this.item = item;
        this.meta = meta;
        this.nbt = nbt;
        this.hash = Objects.hash(item.getRegistryName(), meta, nbt != null ? nbt.hashCode() : 0);
    }

    public ItemStackKey(@Nonnull ItemStack stack) {
        this(stack.getItem(), stack.getMetadata(), stack.getTagCompound() != null ? stack.getTagCompound().copy() : null);
    }

    public ItemStack toItemStack(int count) {
        ItemStack stack = new ItemStack(item, count, meta);
        if (nbt != null) {
            stack.setTagCompound(nbt.copy());
        }
        return stack;
    }

    public static ItemStackKey of(@Nonnull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return new ItemStackKey(stack.getItem(), stack.getMetadata(), tag != null ? tag.copy() : null);
    }

    public static ItemStackKey of(@Nonnull IAEItemStack stack) {
        return of(stack.createItemStack());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStackKey)) return false;
        ItemStackKey other = (ItemStackKey) o;
        return this.meta == other.meta
                && this.item == other.item
                && Objects.equals(this.nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    /**
     * Serialize to NBT.
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        ResourceLocation registryName = item.getRegistryName();
        tag.setString("id", registryName != null ? registryName.toString() : "");
        tag.setInteger("meta", meta);
        if (nbt != null) {
            tag.setTag("nbt", nbt.copy());
        }
        return tag;
    }

    /**
     * Deserialize from NBT.
     */
    @Nullable
    public static ItemStackKey readFromNBT(@Nonnull NBTTagCompound tag) {
        String id = tag.getString("id");
        if (id.isEmpty()) return null;
        Item item = Item.REGISTRY.getObject(new ResourceLocation(id));
        if (item == null) return null;
        int meta = tag.getInteger("meta");
        NBTTagCompound nbt = tag.hasKey("nbt") ? tag.getCompoundTag("nbt") : null;
        return new ItemStackKey(item, meta, nbt);
    }
}

package com.github.aeddddd.ae2enhanced.platform.subnet;

import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * Subnet model for the Advanced Platform Controller.
 */
public class Subnet {

    private int id;
    private String name = "";
    private SubnetItemStorage storage = new SubnetItemStorage();
    private final Set<ItemStackKey> allowFromMain = new HashSet<>();
    private final Set<ItemStackKey> allowToMain = new HashSet<>();

    public Subnet(int id, String name) {
        this.id = id;
        this.name = name != null ? name : "";
    }

    public Subnet() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public SubnetItemStorage getStorage() {
        return storage;
    }

    public void setStorage(SubnetItemStorage storage) {
        this.storage = storage != null ? storage : new SubnetItemStorage();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subnet)) return false;
        Subnet other = (Subnet) o;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public Set<ItemStackKey> getAllowFromMain() {
        return allowFromMain;
    }

    public Set<ItemStackKey> getAllowToMain() {
        return allowToMain;
    }

    public void readFromNBT(@Nonnull NBTTagCompound tag) {
        this.id = tag.getInteger("id");
        this.name = tag.getString("name");
        this.storage = new SubnetItemStorage();
        if (tag.hasKey("storage")) {
            this.storage.readFromNBT(tag.getCompoundTag("storage"));
        }
        this.allowFromMain.clear();
        NBTTagList fromMain = tag.getTagList("allowFromMain", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < fromMain.tagCount(); i++) {
            ItemStackKey key = ItemStackKey.readFromNBT(fromMain.getCompoundTagAt(i));
            if (key != null) {
                this.allowFromMain.add(key);
            }
        }
        this.allowToMain.clear();
        NBTTagList toMain = tag.getTagList("allowToMain", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < toMain.tagCount(); i++) {
            ItemStackKey key = ItemStackKey.readFromNBT(toMain.getCompoundTagAt(i));
            if (key != null) {
                this.allowToMain.add(key);
            }
        }
    }

    @Nonnull
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", id);
        tag.setString("name", name);
        tag.setTag("storage", storage.writeToNBT());
        NBTTagList fromMain = new NBTTagList();
        for (ItemStackKey key : allowFromMain) {
            fromMain.appendTag(key.writeToNBT());
        }
        tag.setTag("allowFromMain", fromMain);
        NBTTagList toMain = new NBTTagList();
        for (ItemStackKey key : allowToMain) {
            toMain.appendTag(key.writeToNBT());
        }
        tag.setTag("allowToMain", toMain);
        return tag;
    }
}

package com.github.aeddddd.ae2enhanced.platform.subnet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * WorldSavedData for subnet item persistence.
 */
public class PlatformSubnetData extends WorldSavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_subnets";

    private final Map<Integer, SubnetItemStorage> subnetStorages = new HashMap<>();

    public PlatformSubnetData() {
        super(DATA_NAME);
    }

    public PlatformSubnetData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound nbt) {
        subnetStorages.clear();
        NBTTagList list = nbt.getTagList("storages", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int subnetId = entry.getInteger("subnetId");
            SubnetItemStorage storage = new SubnetItemStorage();
            storage.readFromNBT(entry.getCompoundTag("data"));
            subnetStorages.put(subnetId, storage);
        }
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Integer, SubnetItemStorage> entry : subnetStorages.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("subnetId", entry.getKey());
            tag.setTag("data", entry.getValue().writeToNBT());
            list.appendTag(tag);
        }
        compound.setTag("storages", list);
        return compound;
    }

    @Nonnull
    public Map<Integer, SubnetItemStorage> getSubnetStorages() {
        return subnetStorages;
    }

    @Nonnull
    public SubnetItemStorage getOrCreateStorage(int subnetId) {
        SubnetItemStorage storage = subnetStorages.get(subnetId);
        if (storage == null) {
            storage = new SubnetItemStorage();
            subnetStorages.put(subnetId, storage);
            markDirty();
        }
        return storage;
    }

    public void removeStorage(int subnetId) {
        if (subnetStorages.remove(subnetId) != null) {
            markDirty();
        }
    }

    @Nonnull
    public static PlatformSubnetData get(@Nonnull World world) {
        MapStorage storage = world.getPerWorldStorage();
        PlatformSubnetData data = (PlatformSubnetData) storage.getOrLoadData(
                PlatformSubnetData.class, DATA_NAME);
        if (data == null) {
            data = new PlatformSubnetData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }
}

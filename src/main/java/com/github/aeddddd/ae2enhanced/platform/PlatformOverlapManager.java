package com.github.aeddddd.ae2enhanced.platform;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 平台重叠管理器 —— WorldSavedData,管理世界中所有平台占用的区块索引.
 */
public class PlatformOverlapManager extends WorldSavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_platforms";

    private final Map<String, BlockPos> claimedChunks = new HashMap<>();

    public PlatformOverlapManager() {
        super(DATA_NAME);
    }

    public PlatformOverlapManager(String name) {
        super(name);
    }

    public static PlatformOverlapManager get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        PlatformOverlapManager instance = (PlatformOverlapManager) storage.getOrLoadData(
                PlatformOverlapManager.class, DATA_NAME);
        if (instance == null) {
            instance = new PlatformOverlapManager();
            storage.setData(DATA_NAME, instance);
        }
        return instance;
    }

    public boolean canClaim(BlockPos controllerPos, int platformSizeInChunks) {
        int centerChunkX = controllerPos.getX() >> 4;
        int centerChunkZ = controllerPos.getZ() >> 4;
        int radius = platformSizeInChunks / 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                String key = (centerChunkX + dx) + "," + (centerChunkZ + dz);
                if (claimedChunks.containsKey(key)) {
                    BlockPos existing = claimedChunks.get(key);
                    if (!existing.equals(controllerPos)) return false;
                }
            }
        }
        return true;
    }

    public void registerPlatform(BlockPos controllerPos, int platformSizeInChunks) {
        int centerChunkX = controllerPos.getX() >> 4;
        int centerChunkZ = controllerPos.getZ() >> 4;
        int radius = platformSizeInChunks / 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                claimedChunks.put((centerChunkX + dx) + "," + (centerChunkZ + dz), controllerPos);
            }
        }
        markDirty();
    }

    public void unregisterPlatform(BlockPos controllerPos, int platformSizeInChunks) {
        int centerChunkX = controllerPos.getX() >> 4;
        int centerChunkZ = controllerPos.getZ() >> 4;
        int radius = platformSizeInChunks / 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                String key = (centerChunkX + dx) + "," + (centerChunkZ + dz);
                BlockPos existing = claimedChunks.get(key);
                if (existing != null && existing.equals(controllerPos)) {
                    claimedChunks.remove(key);
                }
            }
        }
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        claimedChunks.clear();
        NBTTagList list = nbt.getTagList("Chunks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String key = entry.getString("Key");
            BlockPos pos = BlockPos.fromLong(entry.getLong("Controller"));
            claimedChunks.put(key, pos);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, BlockPos> entry : claimedChunks.entrySet()) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("Key", entry.getKey());
            nbt.setLong("Controller", entry.getValue().toLong());
            list.appendTag(nbt);
        }
        compound.setTag("Chunks", list);
        return compound;
    }
}

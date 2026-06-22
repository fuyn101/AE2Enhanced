package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家个人维度数据的 WorldSavedData，保存在主世界。
 */
public class PersonalDimensionData extends WorldSavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + ":personal_dimensions";

    private final Map<UUID, PlayerDimEntry> entries = new HashMap<>();
    private final Map<Integer, UUID> dimToPlayer = new HashMap<>();

    public PersonalDimensionData() {
        super(DATA_NAME);
    }

    public PersonalDimensionData(String name) {
        super(name);
    }

    public PlayerDimEntry getEntry(UUID playerId) {
        return entries.computeIfAbsent(playerId, PlayerDimEntry::new);
    }

    public java.util.Collection<PlayerDimEntry> getAllEntries() {
        return entries.values();
    }

    @Nullable
    public PlayerDimEntry getEntryByDimensionId(int dimId) {
        UUID id = dimToPlayer.get(dimId);
        return id != null ? entries.get(id) : null;
    }

    @Nullable
    public UUID getPlayerForDimension(int dimId) {
        return dimToPlayer.get(dimId);
    }

    public void updateDimensionMapping(UUID playerId, int dimId) {
        PlayerDimEntry entry = getEntry(playerId);
        if (entry.dimensionId != Integer.MIN_VALUE && entry.dimensionId != dimId) {
            dimToPlayer.remove(entry.dimensionId);
        }
        entry.dimensionId = dimId;
        dimToPlayer.put(dimId, playerId);
        markDirty();
    }

    public void setRules(UUID playerId, PersonalDimensionRules rules) {
        getEntry(playerId).rules = rules.copy();
        markDirty();
    }

    public void setEntryPoint(UUID playerId, net.minecraft.util.math.BlockPos pos) {
        getEntry(playerId).entryPoint = pos;
        markDirty();
    }

    public void setReturnPoint(UUID playerId, int dim, double x, double y, double z, float yaw, float pitch) {
        PlayerDimEntry entry = getEntry(playerId);
        entry.returnDim = dim;
        entry.returnX = x;
        entry.returnY = y;
        entry.returnZ = z;
        entry.returnYaw = yaw;
        entry.returnPitch = pitch;
        entry.hasReturnPoint = true;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        entries.clear();
        dimToPlayer.clear();
        NBTTagList list = nbt.getTagList("entries", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            UUID id;
            try {
                id = UUID.fromString(tag.getString("playerUUID"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            PlayerDimEntry entry = new PlayerDimEntry(id);
            entry.readFromNBT(tag);
            entries.put(id, entry);
            if (entry.dimensionId != Integer.MIN_VALUE) {
                dimToPlayer.put(entry.dimensionId, id);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (PlayerDimEntry entry : entries.values()) {
            list.appendTag(entry.writeToNBT());
        }
        compound.setTag("entries", list);
        return compound;
    }

    public static PersonalDimensionData get(World world) {
        World overworld = world.getMinecraftServer() != null
                ? world.getMinecraftServer().getWorld(0)
                : world;
        if (overworld == null) overworld = world;
        MapStorage storage = overworld.getMapStorage();
        PersonalDimensionData data = (PersonalDimensionData) storage.getOrLoadData(PersonalDimensionData.class, DATA_NAME);
        if (data == null) {
            data = new PersonalDimensionData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }
}

package com.github.aeddddd.ae2enhanced.platform.zone;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Zone management registry for the Advanced Platform Controller.
 */
public class ZoneRegistry {

    private final Map<Integer, Zone> zonesById = new HashMap<>();
    private final Set<Zone> inputActiveZones = new HashSet<>();
    private final Set<Zone> outputActiveZones = new HashSet<>();

    public void addZone(@Nonnull Zone zone) {
        zonesById.put(zone.getId(), zone);
        reclassifyZone(zone);
    }

    @Nullable
    public Zone removeZone(int id) {
        Zone zone = zonesById.remove(id);
        if (zone != null) {
            inputActiveZones.remove(zone);
            outputActiveZones.remove(zone);
        }
        return zone;
    }

    @Nullable
    public Zone getZone(int id) {
        return zonesById.get(id);
    }

    @Nonnull
    public Collection<Zone> getAllZones() {
        return zonesById.values();
    }

    @Nonnull
    public Set<Zone> getInputActiveZones() {
        return inputActiveZones;
    }

    @Nonnull
    public Set<Zone> getOutputActiveZones() {
        return outputActiveZones;
    }

    /**
     * Updates pre-grouped sets when a zone's config changes.
     */
    public void reclassifyZone(@Nonnull Zone zone) {
        inputActiveZones.remove(zone);
        outputActiveZones.remove(zone);
        if (zone.hasInput() && zone.getActivityLevel() != Zone.ActivityLevel.FROZEN) {
            inputActiveZones.add(zone);
        }
        if (zone.hasOutput() && zone.getActivityLevel() != Zone.ActivityLevel.FROZEN) {
            outputActiveZones.add(zone);
        }
    }

    public void clear() {
        zonesById.clear();
        inputActiveZones.clear();
        outputActiveZones.clear();
    }

    public void readFromNBT(@Nonnull NBTTagCompound tag) {
        clear();
        NBTTagList list = tag.getTagList("zones", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            Zone zone = new Zone();
            zone.readFromNBT(list.getCompoundTagAt(i));
            addZone(zone);
        }
    }

    @Nonnull
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Zone zone : zonesById.values()) {
            list.appendTag(zone.writeToNBT());
        }
        tag.setTag("zones", list);
        return tag;
    }
}

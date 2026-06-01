package com.github.aeddddd.ae2enhanced.platform.zone;

import com.github.aeddddd.ae2enhanced.platform.io.ActivityLevel;
import com.github.aeddddd.ae2enhanced.platform.io.ZoneActivityTracker;
import com.github.aeddddd.ae2enhanced.platform.subnet.Subnet;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Zone model for the Advanced Platform Controller.
 */
public class Zone {

    private int id;
    private String name = "";
    private int subnetId = 0; // 0 = unassigned
    private ZonePositionData positions;
    private final Map<EnumFacing, FaceIoConfig> faceIo = new EnumMap<>(EnumFacing.class);
    private ActivityLevel activityLevel = ActivityLevel.ACTIVE;
    private int ticksSinceLastIo = 0;
    private int consecutiveFailures = 0;
    private final ZoneActivityTracker activityTracker = new ZoneActivityTracker();
    private Subnet outputSubnet;
    private Subnet inputSubnet;

    public Zone(int id, @Nonnull BlockPos min, @Nonnull BlockPos max) {
        this.id = id;
        this.positions = new ZonePositionData(min, max);
        for (EnumFacing facing : EnumFacing.values()) {
            this.faceIo.put(facing, new FaceIoConfig());
        }
    }

    public Zone() {
        this.positions = new ZonePositionData(BlockPos.ORIGIN, BlockPos.ORIGIN);
        for (EnumFacing facing : EnumFacing.values()) {
            this.faceIo.put(facing, new FaceIoConfig());
        }
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

    public int getSubnetId() {
        return subnetId;
    }

    public void setSubnetId(int subnetId) {
        this.subnetId = subnetId;
    }

    public ZonePositionData getPositions() {
        return positions;
    }

    public Map<EnumFacing, FaceIoConfig> getFaceIo() {
        return faceIo;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(ActivityLevel activityLevel) {
        this.activityLevel = activityLevel != null ? activityLevel : ActivityLevel.ACTIVE;
    }

    public ZoneActivityTracker getActivityTracker() {
        return activityTracker;
    }

    public int getTicksSinceLastIo() {
        return ticksSinceLastIo;
    }

    public void setTicksSinceLastIo(int ticksSinceLastIo) {
        this.ticksSinceLastIo = ticksSinceLastIo;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public Subnet getOutputTarget() {
        return outputSubnet;
    }

    public void setOutputTarget(Subnet outputSubnet) {
        this.outputSubnet = outputSubnet;
    }

    public Subnet getInputTarget() {
        return inputSubnet;
    }

    public void setInputTarget(Subnet inputSubnet) {
        this.inputSubnet = inputSubnet;
    }

    public List<FaceIoConfig> getOutputContainers() {
        List<FaceIoConfig> result = new ArrayList<>();
        List<BlockPos> positions = this.positions.getAllPositions();
        for (BlockPos pos : positions) {
            for (Map.Entry<EnumFacing, FaceIoConfig> entry : faceIo.entrySet()) {
                if (entry.getValue().isOutput()) {
                    FaceIoConfig copy = new FaceIoConfig(pos, entry.getKey());
                    copy.setMode(entry.getValue().getMode());
                    copy.getChannels().addAll(entry.getValue().getChannels());
                    copy.getFilter().addAll(entry.getValue().getFilter());
                    result.add(copy);
                }
            }
        }
        return result;
    }

    public List<FaceIoConfig> getInputContainers() {
        List<FaceIoConfig> result = new ArrayList<>();
        List<BlockPos> positions = this.positions.getAllPositions();
        for (BlockPos pos : positions) {
            for (Map.Entry<EnumFacing, FaceIoConfig> entry : faceIo.entrySet()) {
                if (entry.getValue().isInput()) {
                    FaceIoConfig copy = new FaceIoConfig(pos, entry.getKey());
                    copy.setMode(entry.getValue().getMode());
                    copy.getChannels().addAll(entry.getValue().getChannels());
                    copy.getFilter().addAll(entry.getValue().getFilter());
                    result.add(copy);
                }
            }
        }
        return result;
    }

    public Set<ItemStack> getInputFilters() {
        Set<ItemStack> filters = new HashSet<>();
        for (FaceIoConfig config : faceIo.values()) {
            if (config.isInput()) {
                for (com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey key : config.getFilter()) {
                    filters.add(key.toItemStack(1));
                }
            }
        }
        return filters;
    }

    public Set<ItemStack> getOutputFilters() {
        Set<ItemStack> filters = new HashSet<>();
        for (FaceIoConfig config : faceIo.values()) {
            if (config.isOutput()) {
                for (com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey key : config.getFilter()) {
                    filters.add(key.toItemStack(1));
                }
            }
        }
        return filters;
    }

    public boolean hasInput() {
        for (FaceIoConfig config : faceIo.values()) {
            if (config.isInput()) return true;
        }
        return false;
    }

    public boolean hasOutput() {
        for (FaceIoConfig config : faceIo.values()) {
            if (config.isOutput()) return true;
        }
        return false;
    }

    public void readFromNBT(@Nonnull NBTTagCompound tag) {
        this.id = tag.getInteger("id");
        this.name = tag.getString("name");
        this.subnetId = tag.getInteger("subnetId");
        this.activityLevel = com.github.aeddddd.ae2enhanced.platform.io.ActivityLevel.values()[tag.getInteger("activityLevel")];
        this.ticksSinceLastIo = tag.getInteger("ticksSinceLastIo");
        this.consecutiveFailures = tag.getInteger("consecutiveFailures");

        BlockPos min = BlockPos.fromLong(tag.getLong("min"));
        BlockPos max = BlockPos.fromLong(tag.getLong("max"));
        this.positions = new ZonePositionData(min, max);
        if (tag.hasKey("positions")) {
            this.positions.readFromNBT(tag.getCompoundTag("positions"));
        }

        this.faceIo.clear();
        for (EnumFacing facing : EnumFacing.values()) {
            this.faceIo.put(facing, new FaceIoConfig());
        }
        if (tag.hasKey("faceIo")) {
            NBTTagList list = tag.getTagList("faceIo", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                int facingIndex = entry.getInteger("facing");
                if (facingIndex >= 0 && facingIndex < EnumFacing.values().length) {
                    EnumFacing facing = EnumFacing.values()[facingIndex];
                    FaceIoConfig config = new FaceIoConfig();
                    config.readFromNBT(entry.getCompoundTag("config"));
                    this.faceIo.put(facing, config);
                }
            }
        }
    }

    @Nonnull
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", id);
        tag.setString("name", name);
        tag.setInteger("subnetId", subnetId);
        tag.setInteger("activityLevel", activityLevel.ordinal());
        tag.setInteger("ticksSinceLastIo", ticksSinceLastIo);
        tag.setInteger("consecutiveFailures", consecutiveFailures);
        // Store bounds for reconstruction
        tag.setLong("min", BlockPos.ORIGIN.toLong()); // placeholder, actual bounds stored in positions NBT
        tag.setLong("max", BlockPos.ORIGIN.toLong());
        tag.setTag("positions", positions.writeToNBT());

        NBTTagList list = new NBTTagList();
        for (Map.Entry<EnumFacing, FaceIoConfig> entry : faceIo.entrySet()) {
            NBTTagCompound faceTag = new NBTTagCompound();
            faceTag.setInteger("facing", entry.getKey().ordinal());
            faceTag.setTag("config", entry.getValue().writeToNBT());
            list.appendTag(faceTag);
        }
        tag.setTag("faceIo", list);
        return tag;
    }
}

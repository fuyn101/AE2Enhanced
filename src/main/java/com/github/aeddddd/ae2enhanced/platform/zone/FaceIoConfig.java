package com.github.aeddddd.ae2enhanced.platform.zone;

import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Direction-level IO config for platform zones.
 */
public class FaceIoConfig {

    public enum IoMode {
        NONE,
        INPUT,
        OUTPUT,
        BOTH
    }

    public enum IoChannel {
        ITEM,
        FLUID,
        GAS,
        ESSENTIA,
        ENERGY
    }

    private IoMode mode = IoMode.NONE;
    private final EnumSet<IoChannel> channels = EnumSet.noneOf(IoChannel.class);
    private final Set<ItemStackKey> filter = new HashSet<>();
    private BlockPos pos = BlockPos.ORIGIN;
    private EnumFacing face = EnumFacing.NORTH;

    public FaceIoConfig() {
    }

    public FaceIoConfig(BlockPos pos, EnumFacing face) {
        this.pos = pos != null ? pos : BlockPos.ORIGIN;
        this.face = face != null ? face : EnumFacing.NORTH;
    }

    public IoMode getMode() {
        return mode;
    }

    public void setMode(IoMode mode) {
        this.mode = mode != null ? mode : IoMode.NONE;
    }

    public EnumSet<IoChannel> getChannels() {
        return channels;
    }

    public Set<ItemStackKey> getFilter() {
        return filter;
    }

    public boolean isInput() {
        return mode == IoMode.INPUT || mode == IoMode.BOTH;
    }

    public boolean isOutput() {
        return mode == IoMode.OUTPUT || mode == IoMode.BOTH;
    }

    public boolean hasChannel(IoChannel channel) {
        return channels.contains(channel);
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos != null ? pos : BlockPos.ORIGIN;
    }

    public EnumFacing getFace() {
        return face;
    }

    public void setFace(EnumFacing face) {
        this.face = face != null ? face : EnumFacing.NORTH;
    }

    public boolean isEmptyFilter() {
        return filter.isEmpty();
    }

    public boolean accepts(ItemStackKey key) {
        return filter.isEmpty() || filter.contains(key);
    }

    public void readFromNBT(@Nonnull NBTTagCompound tag) {
        this.mode = IoMode.values()[tag.getInteger("mode")];
        this.channels.clear();
        int channelMask = tag.getInteger("channels");
        for (IoChannel ch : IoChannel.values()) {
            if ((channelMask & (1 << ch.ordinal())) != 0) {
                this.channels.add(ch);
            }
        }
        this.filter.clear();
        NBTTagList list = tag.getTagList("filter", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStackKey key = ItemStackKey.readFromNBT(list.getCompoundTagAt(i));
            if (key != null) {
                this.filter.add(key);
            }
        }
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("mode", mode.ordinal());
        int channelMask = 0;
        for (IoChannel ch : channels) {
            channelMask |= 1 << ch.ordinal();
        }
        tag.setInteger("channels", channelMask);
        NBTTagList list = new NBTTagList();
        for (ItemStackKey key : filter) {
            list.appendTag(key.writeToNBT());
        }
        tag.setTag("filter", list);
        return tag;
    }
}

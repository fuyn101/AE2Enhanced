package com.github.aeddddd.ae2enhanced.centralinterface;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * 远程绑定目标的数据结构。
 */
public class TargetBinding {

    public final BlockPos pos;
    public final int dimension;
    public final String blockId;

    public TargetBinding(BlockPos pos, int dimension, String blockId) {
        this.pos = pos;
        this.dimension = dimension;
        this.blockId = blockId;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", pos.toLong());
        tag.setInteger("dim", dimension);
        tag.setString("blockId", blockId);
        return tag;
    }

    public static TargetBinding readFromNBT(NBTTagCompound tag) {
        BlockPos pos = BlockPos.fromLong(tag.getLong("pos"));
        int dim = tag.getInteger("dim");
        String blockId = tag.getString("blockId");
        return new TargetBinding(pos, dim, blockId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetBinding)) return false;
        TargetBinding that = (TargetBinding) o;
        return dimension == that.dimension && pos.equals(that.pos);
    }

    @Override
    public int hashCode() {
        return 31 * pos.hashCode() + dimension;
    }
}

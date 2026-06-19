package com.github.aeddddd.ae2enhanced.central;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 远程机器绑定目标的数据结构.
 *
 * <p>用于中枢 ME 接口记录其远程目标机器(如熔炉、祭坛等)的位置信息.</p>
 */
public class TargetBinding {

    public final BlockPos pos;
    public final int dimension;
    public final String blockId;

    public TargetBinding(@Nonnull BlockPos pos, int dimension, @Nullable String blockId) {
        this.pos = pos;
        this.dimension = dimension;
        this.blockId = blockId;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", pos.toLong());
        tag.setInteger("dim", dimension);
        if (blockId != null) {
            tag.setString("blockId", blockId);
        }
        return tag;
    }

    public static TargetBinding readFromNBT(NBTTagCompound tag) {
        BlockPos pos = BlockPos.fromLong(tag.getLong("pos"));
        int dim = tag.getInteger("dim");
        String blockId = tag.hasKey("blockId") ? tag.getString("blockId") : null;
        return new TargetBinding(pos, dim, blockId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetBinding)) return false;
        TargetBinding that = (TargetBinding) o;
        return dimension == that.dimension && pos.equals(that.pos)
                && (blockId == null ? that.blockId == null : blockId.equals(that.blockId));
    }

    @Override
    public int hashCode() {
        int result = 31 * pos.hashCode() + dimension;
        result = 31 * result + (blockId != null ? blockId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TargetBinding{pos=" + pos + ", dim=" + dimension + ", blockId=" + blockId + "}";
    }
}

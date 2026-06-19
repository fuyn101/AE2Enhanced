package com.github.aeddddd.ae2enhanced.central;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 超维度子网链接的数据结构.
 *
 * <p>描述一个远程 ME 子网的入口节点(通常是量子环或控制器接口),用于
 * {@link HyperdimensionalStorageNode} 将远程子网的存储暴露到本地网格.</p>
 */
public class SubNetLink {

    public final BlockPos pos;
    public final int dimension;
    @Nullable
    public final EnumFacing side;
    @Nullable
    public final String name;

    public SubNetLink(@Nonnull BlockPos pos, int dimension, @Nullable EnumFacing side, @Nullable String name) {
        this.pos = pos;
        this.dimension = dimension;
        this.side = side;
        this.name = name;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", pos.toLong());
        tag.setInteger("dim", dimension);
        if (side != null) {
            tag.setByte("side", (byte) side.getIndex());
        }
        if (name != null) {
            tag.setString("name", name);
        }
        return tag;
    }

    public static SubNetLink readFromNBT(NBTTagCompound tag) {
        BlockPos pos = BlockPos.fromLong(tag.getLong("pos"));
        int dim = tag.getInteger("dim");
        EnumFacing side = tag.hasKey("side") ? EnumFacing.byIndex(tag.getByte("side")) : null;
        String name = tag.hasKey("name") ? tag.getString("name") : null;
        return new SubNetLink(pos, dim, side, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubNetLink)) return false;
        SubNetLink that = (SubNetLink) o;
        return dimension == that.dimension && pos.equals(that.pos)
                && (side == that.side);
    }

    @Override
    public int hashCode() {
        int result = 31 * pos.hashCode() + dimension;
        result = 31 * result + (side != null ? side.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SubNetLink{pos=" + pos + ", dim=" + dimension + ", side=" + side + ", name=" + name + "}";
    }
}

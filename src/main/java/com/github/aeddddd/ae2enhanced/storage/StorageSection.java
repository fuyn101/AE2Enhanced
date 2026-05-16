package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;

/**
 * 超维度仓储中枢的单一存储段（物品/流体/气体/源质）。
 * 封装该段的 NBT key、描述符工厂、加载与保存逻辑。
 *
 * @param <D> 描述符类型，如 ItemDescriptor / FluidDescriptor / GasDescriptor / EssentiaDescriptor
 */
public final class StorageSection<D extends Descriptor> {

    private final String nbtKey;
    private final Function<NBTTagCompound, D> factory;

    public StorageSection(String nbtKey, Function<NBTTagCompound, D> factory) {
        this.nbtKey = nbtKey;
        this.factory = factory;
    }

    /**
     * 从 NBT 根节点加载数据到目标 Map。
     */
    public void load(NBTTagCompound root, Map<D, BigInteger> target) {
        if (root == null || !root.hasKey(nbtKey, 9)) return;
        NBTTagList list = root.getTagList(nbtKey, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            D descriptor = factory.apply(tag);
            if (descriptor == null) continue;
            String countStr = tag.getString("Count");
            try {
                target.put(descriptor, new BigInteger(countStr));
            } catch (NumberFormatException e) {
                // skip invalid count
            }
        }
    }

    /**
     * 将源 Map 的数据序列化到 NBT 根节点。
     */
    public void save(NBTTagCompound root, Map<D, BigInteger> source) {
        NBTTagList list = new NBTTagList();
        if (source != null) {
            for (Map.Entry<D, BigInteger> entry : source.entrySet()) {
                NBTTagCompound tag = entry.getKey().toNBT();
                tag.setString("Count", entry.getValue().toString());
                list.appendTag(tag);
            }
        }
        root.setTag(nbtKey, list);
    }
}

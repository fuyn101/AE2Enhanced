package com.github.aeddddd.ae2enhanced.platform.zone;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BitSet-compressed position storage for platform zones.
 * Stores positions within fixed min/max bounds.
 */
public class ZonePositionData {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final int width;
    private final int depth;
    private final Map<Integer, BitSet> layers = new HashMap<>();

    public ZonePositionData(@Nonnull BlockPos min, @Nonnull BlockPos max) {
        this.minX = min.getX();
        this.minY = min.getY();
        this.minZ = min.getZ();
        this.maxX = max.getX();
        this.maxY = max.getY();
        this.maxZ = max.getZ();
        this.width = this.maxX - this.minX + 1;
        this.depth = this.maxZ - this.minZ + 1;
    }

    private int index(int x, int z) {
        return (z - minZ) * width + (x - minX);
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public void add(@Nonnull BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (!inBounds(x, y, z)) return;
        BitSet bitSet = layers.computeIfAbsent(y, k -> new BitSet(width * depth));
        bitSet.set(index(x, z));
    }

    public boolean contains(@Nonnull BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (!inBounds(x, y, z)) return false;
        BitSet bitSet = layers.get(y);
        if (bitSet == null) return false;
        return bitSet.get(index(x, z));
    }

    public void remove(@Nonnull BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (!inBounds(x, y, z)) return;
        BitSet bitSet = layers.get(y);
        if (bitSet == null) return;
        bitSet.clear(index(x, z));
        if (bitSet.isEmpty()) {
            layers.remove(y);
        }
    }

    public boolean isEmpty() {
        for (BitSet bitSet : layers.values()) {
            if (!bitSet.isEmpty()) return false;
        }
        return true;
    }

    @Nonnull
    public List<BlockPos> getAllPositions() {
        List<BlockPos> result = new ArrayList<>();
        for (Map.Entry<Integer, BitSet> entry : layers.entrySet()) {
            int y = entry.getKey();
            BitSet bitSet = entry.getValue();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                int localX = i % width;
                int localZ = i / width;
                result.add(new BlockPos(minX + localX, y, minZ + localZ));
            }
        }
        return result;
    }

    /**
     * Stores as {@code Map<Y, byte[]>} where byte[] is BitSet.toByteArray().
     */
    public void readFromNBT(@Nonnull NBTTagCompound tag) {
        layers.clear();
        for (String key : tag.getKeySet()) {
            try {
                int y = Integer.parseInt(key);
                byte[] bytes = tag.getByteArray(key);
                BitSet bitSet = BitSet.valueOf(bytes);
                layers.put(y, bitSet);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Nonnull
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        for (Map.Entry<Integer, BitSet> entry : layers.entrySet()) {
            tag.setByteArray(String.valueOf(entry.getKey()), entry.getValue().toByteArray());
        }
        return tag;
    }
}

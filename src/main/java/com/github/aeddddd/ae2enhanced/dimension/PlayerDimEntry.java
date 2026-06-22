package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * 玩家个人维度的持久化条目。
 */
public class PlayerDimEntry {

    public final UUID playerId;
    public int dimensionId = Integer.MIN_VALUE;
    public PersonalDimensionRules rules = new PersonalDimensionRules();
    public BlockPos entryPoint = new BlockPos(0, 65, 0);
    public int returnDim = 0;
    public double returnX, returnY, returnZ;
    public float returnYaw, returnPitch;
    public boolean hasReturnPoint = false;

    public PlayerDimEntry(UUID playerId) {
        this.playerId = playerId;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("playerUUID", playerId.toString());
        tag.setInteger("dimensionId", dimensionId);
        tag.setTag("rules", rules.writeToNBT());
        tag.setLong("entryPoint", entryPoint.toLong());
        tag.setInteger("returnDim", returnDim);
        tag.setDouble("returnX", returnX);
        tag.setDouble("returnY", returnY);
        tag.setDouble("returnZ", returnZ);
        tag.setFloat("returnYaw", returnYaw);
        tag.setFloat("returnPitch", returnPitch);
        tag.setBoolean("hasReturnPoint", hasReturnPoint);
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        dimensionId = tag.getInteger("dimensionId");
        rules.readFromNBT(tag.getCompoundTag("rules"));
        entryPoint = BlockPos.fromLong(tag.getLong("entryPoint"));
        returnDim = tag.getInteger("returnDim");
        returnX = tag.getDouble("returnX");
        returnY = tag.getDouble("returnY");
        returnZ = tag.getDouble("returnZ");
        returnYaw = tag.getFloat("returnYaw");
        returnPitch = tag.getFloat("returnPitch");
        hasReturnPoint = tag.getBoolean("hasReturnPoint");
    }
}

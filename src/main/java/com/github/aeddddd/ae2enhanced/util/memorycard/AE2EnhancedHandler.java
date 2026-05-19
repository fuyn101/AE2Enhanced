package com.github.aeddddd.ae2enhanced.util.memorycard;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 预留：处理本 mod 中不继承 AE2 基类的特殊设备。
 * 第一期本 mod 所有设备均继承 AEBasePart / AEBaseTile，由 AE2PartHandler / AE2TileHandler 覆盖。
 */
public class AE2EnhancedHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        // 预留，第一期无特殊设备需要此 handler
        return false;
    }

    @Override
    public NBTTagCompound copy(Object target) {
        return null;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        return PasteResult.INVALID_MACHINE;
    }

    @Override
    public String getDisplayName(Object target) {
        return target.getClass().getSimpleName();
    }
}

package com.github.aeddddd.ae2enhanced.platform.io;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public class SlotDemand {
    public final BlockPos pos;
    public final EnumFacing face;
    public final int slot;
    public final ItemStack current;
    public final int canAccept;

    public SlotDemand(BlockPos pos, EnumFacing face, int slot, ItemStack current, int canAccept) {
        this.pos = pos;
        this.face = face;
        this.slot = slot;
        this.current = current;
        this.canAccept = canAccept;
    }
}

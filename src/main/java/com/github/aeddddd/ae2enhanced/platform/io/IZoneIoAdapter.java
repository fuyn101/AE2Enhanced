package com.github.aeddddd.ae2enhanced.platform.io;

import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Map;

public interface IZoneIoAdapter {
    boolean collectOutputs(IItemHandler handler, Map<ItemStackKey, Long> out);
    boolean collectInputDemands(IItemHandler handler, List<SlotDemand> demands);
    boolean matches(World world, BlockPos pos, TileEntity te);
    int getPriority();
}

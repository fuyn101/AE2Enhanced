package com.github.aeddddd.ae2enhanced.platform.io;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ZoneIoAdapterRegistry {
    private final List<IZoneIoAdapter> adapters = new ArrayList<>();

    public void register(IZoneIoAdapter adapter) {
        if (adapter != null) {
            this.adapters.add(adapter);
            this.adapters.sort(Comparator.comparingInt(IZoneIoAdapter::getPriority).reversed());
        }
    }

    public IZoneIoAdapter getAdapter(World world, BlockPos pos, TileEntity te) {
        if (te == null) {
            return null;
        }
        for (IZoneIoAdapter adapter : this.adapters) {
            if (adapter.matches(world, pos, te)) {
                return adapter;
            }
        }
        return null;
    }

    public List<IZoneIoAdapter> getAdapters() {
        return Collections.unmodifiableList(this.adapters);
    }
}

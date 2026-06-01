package com.github.aeddddd.ae2enhanced.platform.io;

import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;
import com.github.aeddddd.ae2enhanced.platform.subnet.Subnet;
import com.github.aeddddd.ae2enhanced.platform.zone.FaceIoConfig;
import com.github.aeddddd.ae2enhanced.platform.zone.Zone;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Map;

public class OutputProcessor {

    public void process(Zone zone, PlatformIoCache cache, ZoneIoAdapterRegistry adapters,
                        Map<Subnet, Map<ItemStackKey, Long>> accumulator) {
        List<FaceIoConfig> containers = zone.getOutputContainers();
        if (containers == null || containers.isEmpty()) {
            return;
        }

        Subnet targetSubnet = zone.getOutputTarget();
        if (targetSubnet == null) {
            return;
        }

        Map<ItemStackKey, Long> subnetAccumulator = accumulator.computeIfAbsent(targetSubnet, k -> new java.util.HashMap<>());

        for (FaceIoConfig config : containers) {
            BlockPos pos = config.getPos();
            EnumFacing face = config.getFace();

            TileEntity te = cache.getTile(pos);
            if (te == null || te.isInvalid()) {
                continue;
            }

            IItemHandler handler = cache.getHandler(pos, face);
            if (handler == null) {
                continue;
            }

            IZoneIoAdapter adapter = adapters.getAdapter(te.getWorld(), pos, te);
            boolean handled = false;
            if (adapter != null) {
                handled = adapter.collectOutputs(handler, subnetAccumulator);
            }

            if (!handled) {
                genericScan(handler, subnetAccumulator);
            }
        }
    }

    private void genericScan(IItemHandler handler, Map<ItemStackKey, Long> out) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStackKey key = new ItemStackKey(stack);
            long current = out.getOrDefault(key, 0L);
            out.put(key, current + stack.getCount());
        }
    }
}

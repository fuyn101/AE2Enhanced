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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InputProcessor {

    public void process(Zone zone, PlatformIoCache cache, ZoneIoAdapterRegistry adapters,
                        Map<Subnet, Map<ItemStackKey, Long>> demandAccumulator) {
        List<FaceIoConfig> containers = zone.getInputContainers();
        if (containers == null || containers.isEmpty()) {
            return;
        }

        Subnet targetSubnet = zone.getInputTarget();
        if (targetSubnet == null) {
            return;
        }

        Map<ItemStackKey, Long> subnetDemand = demandAccumulator.computeIfAbsent(targetSubnet, k -> new java.util.HashMap<>());
        Set<ItemStack> filters = zone.getInputFilters();

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
                List<SlotDemand> demands = new ArrayList<>();
                handled = adapter.collectInputDemands(handler, demands);
                if (handled) {
                    for (SlotDemand demand : demands) {
                        if (filters != null && !filters.isEmpty() && !matchesFilter(demand.current, filters)) {
                            continue;
                        }
                        ItemStackKey key = new ItemStackKey(demand.current);
                        long current = subnetDemand.getOrDefault(key, 0L);
                        subnetDemand.put(key, current + demand.canAccept);
                    }
                }
            }

            if (!handled) {
                genericScan(handler, filters, subnetDemand);
            }
        }
    }

    private void genericScan(IItemHandler handler, Set<ItemStack> filters, Map<ItemStackKey, Long> demand) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                if (filters == null || filters.isEmpty()) {
                    continue;
                }
                int maxStack = Math.min(64, handler.getSlotLimit(i));
                for (ItemStack filter : filters) {
                    ItemStackKey key = new ItemStackKey(filter);
                    long current = demand.getOrDefault(key, 0L);
                    demand.put(key, current + maxStack);
                }
            } else {
                if (filters != null && !filters.isEmpty() && !matchesFilter(stack, filters)) {
                    continue;
                }
                int maxStack = Math.min(stack.getMaxStackSize(), handler.getSlotLimit(i));
                int space = maxStack - stack.getCount();
                if (space > 0) {
                    ItemStackKey key = new ItemStackKey(stack);
                    long current = demand.getOrDefault(key, 0L);
                    demand.put(key, current + space);
                }
            }
        }
    }

    private boolean matchesFilter(ItemStack stack, Set<ItemStack> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (ItemStack filter : filters) {
            if (ItemStack.areItemsEqual(stack, filter) && ItemStack.areItemStackTagsEqual(stack, filter)) {
                return true;
            }
        }
        return false;
    }
}

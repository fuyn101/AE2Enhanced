package com.github.aeddddd.ae2enhanced.platform.io;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;
import com.github.aeddddd.ae2enhanced.platform.subnet.Subnet;
import com.github.aeddddd.ae2enhanced.platform.zone.FaceIoConfig;
import com.github.aeddddd.ae2enhanced.platform.zone.Zone;
import com.github.aeddddd.ae2enhanced.platform.zone.ZoneRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlatformIoScheduler implements IGridTickable {
    private final com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController controller;
    private final ZoneRegistry zones;
    private final PlatformIoCache cache;
    private final ZoneIoAdapterRegistry adapters;
    private final IActionSource actionSource;

    // Per-tick reusable accumulators
    private final Map<Subnet, Map<ItemStackKey, Long>> outputAccumulator = new HashMap<>();
    private final Map<Subnet, Map<ItemStackKey, Long>> inputDemandAccumulator = new HashMap<>();

    // Rotation indices for input distribution to prevent slot bias
    private final Map<Subnet, Integer> subnetZoneRotation = new HashMap<>();
    private final Map<Zone, Integer> zoneContainerRotation = new HashMap<>();
    private final Map<FaceIoConfig, Integer> containerSlotRotation = new HashMap<>();

    // Flush tracking
    private long tickCounter = 0;
    private long lastOutputFlushTick = 0;
    private static final long OUTPUT_FLUSH_INTERVAL = 5;
    private static final long OUTPUT_FLUSH_THRESHOLD = 1;

    public PlatformIoScheduler(com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController controller,
                               ZoneRegistry zones, PlatformIoCache cache,
                               ZoneIoAdapterRegistry adapters, IActionSource actionSource) {
        this.controller = controller;
        this.zones = zones;
        this.cache = cache;
        this.adapters = adapters;
        this.actionSource = actionSource;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        this.tickCounter++;

        // Phase A: Scan all OUTPUT zones (pure local)
        OutputProcessor outputProcessor = new OutputProcessor();
        for (Zone zone : this.zones.getOutputActiveZones()) {
            if (!zone.getActivityTracker().shouldScanThisTick(this.tickCounter)) {
                continue;
            }
            try {
                outputProcessor.process(zone, this.cache, this.adapters, this.outputAccumulator);
                zone.getActivityTracker().onSuccessfulIo();
            } catch (Exception e) {
                zone.getActivityTracker().onFailure();
            }
        }

        // Phase B: Scan all INPUT zones (pure local)
        InputProcessor inputProcessor = new InputProcessor();
        for (Zone zone : this.zones.getInputActiveZones()) {
            if (!zone.getActivityTracker().shouldScanThisTick(this.tickCounter)) {
                continue;
            }
            try {
                inputProcessor.process(zone, this.cache, this.adapters, this.inputDemandAccumulator);
                zone.getActivityTracker().onSuccessfulIo();
            } catch (Exception e) {
                zone.getActivityTracker().onFailure();
            }
        }

        // Phase C: Flush outputs
        try {
            flushOutputs(node);
        } catch (GridAccessException e) {
            // Network unavailable, items stay in containers
        }

        // Phase D: Batch input extraction
        try {
            extractInputs(node);
        } catch (GridAccessException e) {
            // Network unavailable
        }

        // Phase E: Update activity
        updateActivityLevels();

        // Clean accumulators
        this.outputAccumulator.clear();
        this.inputDemandAccumulator.clear();

        return TickRateModulation.SAME;
    }

    private void flushOutputs(IGridNode node) throws GridAccessException {
        boolean forceFlush = this.tickCounter - this.lastOutputFlushTick >= OUTPUT_FLUSH_INTERVAL;
        boolean thresholdMet = false;

        if (!forceFlush && !this.outputAccumulator.isEmpty()) {
            for (Map<ItemStackKey, Long> items : this.outputAccumulator.values()) {
                for (long amount : items.values()) {
                    if (amount >= OUTPUT_FLUSH_THRESHOLD) {
                        thresholdMet = true;
                        break;
                    }
                }
                if (thresholdMet) {
                    break;
                }
            }
        }

        if (!forceFlush && !thresholdMet) {
            return;
        }

        IMEMonitor<IAEItemStack> networkInv = getNetworkInventory(node);
        if (networkInv == null) {
            return;
        }

        for (Zone zone : this.zones.getOutputActiveZones()) {
            Subnet subnet = zone.getOutputTarget();
            Map<ItemStackKey, Long> subnetAcc = this.outputAccumulator.get(subnet);
            if (subnetAcc == null || subnetAcc.isEmpty()) {
                continue;
            }

            // 获取输出过滤集
            java.util.Set<ItemStackKey> allowFilter = (subnet.getId() == 0)
                    ? controller.getMainNetAllowTo()
                    : subnet.getAllowToMain();

            List<FaceIoConfig> containers = zone.getOutputContainers();
            if (containers == null) {
                continue;
            }

            for (FaceIoConfig config : containers) {
                BlockPos pos = config.getPos();
                EnumFacing face = config.getFace();
                IItemHandler handler = this.cache.getHandler(pos, face);
                if (handler == null) {
                    continue;
                }

                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) {
                        continue;
                    }

                    ItemStackKey key = new ItemStackKey(stack);

                    // 子网级过滤
                    if (!allowFilter.isEmpty() && !allowFilter.contains(key)) {
                        continue;
                    }
                    // 面级过滤(空过滤=全部通过)
                    if (!config.accepts(key)) {
                        continue;
                    }

                    Long accAmount = subnetAcc.get(key);
                    if (accAmount == null || accAmount <= 0) {
                        continue;
                    }

                    int toExtract = (int) Math.min(stack.getCount(), Math.min(accAmount, Integer.MAX_VALUE));
                    ItemStack extracted = handler.extractItem(i, toExtract, false);
                    if (extracted.isEmpty()) {
                        continue;
                    }

                    IAEItemStack aeExtracted = AEItemStack.fromItemStack(extracted);
                    if (aeExtracted == null) {
                        handler.insertItem(i, extracted, false);
                        continue;
                    }

                    IAEItemStack notInserted = networkInv.injectItems(aeExtracted, Actionable.MODULATE, this.actionSource);
                    if (notInserted != null && notInserted.getStackSize() > 0) {
                        ItemStack returnStack = notInserted.createItemStack();
                        handler.insertItem(i, returnStack, false);
                    }

                    long injected = extracted.getCount() - (notInserted != null ? notInserted.getStackSize() : 0);
                    long remaining = accAmount - injected;
                    if (remaining > 0) {
                        subnetAcc.put(key, remaining);
                    } else {
                        subnetAcc.remove(key);
                    }
                }
            }
        }

        this.lastOutputFlushTick = this.tickCounter;
    }

    private void extractInputs(IGridNode node) throws GridAccessException {
        if (this.inputDemandAccumulator.isEmpty()) {
            return;
        }

        IMEMonitor<IAEItemStack> networkInv = getNetworkInventory(node);
        if (networkInv == null) {
            return;
        }

        for (Map.Entry<Subnet, Map<ItemStackKey, Long>> subnetEntry : this.inputDemandAccumulator.entrySet()) {
            Subnet subnet = subnetEntry.getKey();
            Map<ItemStackKey, Long> demands = subnetEntry.getValue();

            // 获取输入过滤集
            java.util.Set<ItemStackKey> allowFilter = (subnet.getId() == 0)
                    ? controller.getMainNetAllowFrom()
                    : subnet.getAllowFromMain();

            for (Map.Entry<ItemStackKey, Long> demandEntry : demands.entrySet()) {
                ItemStackKey key = demandEntry.getKey();
                long demand = demandEntry.getValue();
                if (demand <= 0) {
                    continue;
                }

                // 如果过滤非空,且物品不在过滤集中,跳过
                if (!allowFilter.isEmpty() && !allowFilter.contains(key)) {
                    continue;
                }

                ItemStack requestStack = key.toItemStack((int) Math.min(demand, Integer.MAX_VALUE));
                IAEItemStack request = AEItemStack.fromItemStack(requestStack);
                if (request == null) {
                    continue;
                }
                request.setStackSize(demand);

                IAEItemStack extracted = networkInv.extractItems(request, Actionable.MODULATE, this.actionSource);
                if (extracted == null || extracted.getStackSize() <= 0) {
                    continue;
                }

                long remaining = extracted.getStackSize();
                List<Zone> inputZones = this.zones.getInputZonesForSubnet(subnet);
                if (inputZones == null || inputZones.isEmpty()) {
                    networkInv.injectItems(extracted, Actionable.MODULATE, this.actionSource);
                    continue;
                }

                int rotation = this.subnetZoneRotation.getOrDefault(subnet, 0);
                int startIndex = rotation;
                int zoneCount = inputZones.size();

                for (int i = 0; i < zoneCount && remaining > 0; i++) {
                    int zoneIndex = (startIndex + i) % zoneCount;
                    Zone zone = inputZones.get(zoneIndex);
                    long placed = tryDistributeToZone(zone, key, remaining);
                    if (placed > 0) {
                        remaining -= placed;
                        startIndex = (zoneIndex + 1) % zoneCount;
                    }
                }

                this.subnetZoneRotation.put(subnet, startIndex);

                if (remaining > 0) {
                    IAEItemStack leftover = extracted.copy();
                    leftover.setStackSize(remaining);
                    networkInv.injectItems(leftover, Actionable.MODULATE, this.actionSource);
                }
            }
        }
    }

    private long tryDistributeToZone(Zone zone, ItemStackKey key, long amount) {
        List<FaceIoConfig> containers = zone.getInputContainers();
        if (containers == null || containers.isEmpty()) {
            return 0;
        }

        ItemStack toInsert = key.toItemStack((int) Math.min(amount, Integer.MAX_VALUE));
        if (toInsert.isEmpty()) {
            return 0;
        }

        int containerRotation = this.zoneContainerRotation.getOrDefault(zone, 0);
        int startContainer = containerRotation;
        int containerCount = containers.size();
        long remaining = amount;

        for (int c = 0; c < containerCount && remaining > 0; c++) {
            int containerIndex = (startContainer + c) % containerCount;
            FaceIoConfig config = containers.get(containerIndex);

            BlockPos pos = config.getPos();
            EnumFacing face = config.getFace();
            // 面级过滤(空过滤=全部通过)
            if (!config.accepts(key)) {
                continue;
            }
            IItemHandler handler = this.cache.getHandler(pos, face);
            if (handler == null) {
                continue;
            }

            int slotRotation = this.containerSlotRotation.getOrDefault(config, 0);
            int startSlot = slotRotation;
            int slotCount = handler.getSlots();
            boolean placedInContainer = false;

            for (int s = 0; s < slotCount && remaining > 0; s++) {
                int slotIndex = (startSlot + s) % slotCount;
                ItemStack result = handler.insertItem(slotIndex, toInsert.copy(), false);
                int inserted = toInsert.getCount() - result.getCount();
                if (inserted > 0) {
                    remaining -= inserted;
                    placedInContainer = true;
                    if (remaining > 0) {
                        toInsert.setCount((int) Math.min(remaining, Integer.MAX_VALUE));
                    }
                    startSlot = (slotIndex + 1) % slotCount;
                }
            }

            if (placedInContainer) {
                this.containerSlotRotation.put(config, startSlot);
                startContainer = (containerIndex + 1) % containerCount;
            }
        }

        this.zoneContainerRotation.put(zone, startContainer);
        return amount - remaining;
    }

    private void updateActivityLevels() {
        for (Zone zone : this.zones.getAllZones()) {
            zone.getActivityTracker().onTick();
        }
    }

    private IMEMonitor<IAEItemStack> getNetworkInventory(IGridNode node) throws GridAccessException {
        appeng.api.networking.storage.IStorageGrid storageGrid =
                node.getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
        return storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
    }
}

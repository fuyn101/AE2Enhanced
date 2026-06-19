package com.github.aeddddd.ae2enhanced.recycler;

import ae2.api.config.Actionable;
import ae2.api.networking.IGrid;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ME 网络回收节点向 AE2 网络注册的单一 handler.
 *
 * <p>对 AE2 网络表现为一个存储源,实际回收操作把目标机器产物写入当前网络存储.</p>
 */
public class RecyclerNetworkHandler implements MEStorage, IStorageProvider {

    private final TileMENetworkRecycler tile;
    private final RecyclerIndex index = new RecyclerIndex();
    private final Map<TargetManager.TargetRef, TargetAdapter> adapters = new Object2ObjectOpenHashMap<>();
    private final Map<TargetManager.TargetRef, RecyclerIndex.TargetAdapterSnapshot> snapshots = new Object2ObjectOpenHashMap<>();
    private final BulkCollector collector = new BulkCollector();
    private final HyperStorageLink hyperStorageLink = new HyperStorageLink();

    private long lastRecycledCount = 0;
    private int tickCounter = 0;

    public RecyclerNetworkHandler(TileMENetworkRecycler tile) {
        this.tile = tile;
    }

    public void onLoad() {
        // handler 由 tile 在接入网络时注册
    }

    public void onInvalidate() {
        hyperStorageLink.invalidate();
        adapters.values().forEach(TargetAdapter::invalidate);
        adapters.clear();
        snapshots.clear();
        index.clear();
    }

    /**
     * 尝试把机器产物直接注入当前网络存储。
     *
     * @param output 产物堆叠
     * @return 未能注入的部分；全部注入成功返回 {@link ItemStack#EMPTY}
     */
    public ItemStack tryInjectMachineOutput(ItemStack output) {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }

        IManagedGridNode node = tile.getMainNode();
        if (!node.isActive()) {
            return output;
        }

        MEStorage storage = hyperStorageLink.find(node, tile.getWorld().getTotalWorldTime());
        if (storage == null) {
            if (AE2EnhancedConfig.recycler.requireHyperStorageForRedirect) {
                return output;
            }
            IGrid grid = node.getGrid();
            if (grid == null) return output;
            IStorageService storageService = grid.getService(IStorageService.class);
            if (storageService == null) return output;
            storage = storageService.getInventory();
        }

        if (storage != null) {
            syncStorageAdapter(storage);
        }

        AEItemKey key = AEItemKey.of(output);
        if (key == null) return output;

        long remaining = storage.insert(key, output.getCount(), Actionable.MODULATE, getActionSource());
        if (remaining >= output.getCount()) {
            return output;
        }
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        return key.toStack((int) remaining);
    }

    /**
     * 把当前找到的网络存储同步给 RecyclerBindingRegistry，
     * 使机器 Hook 侧无需实时查询网格即可快速判断是否能直注。
     */
    private void syncStorageAdapter(MEStorage storage) {
        RecyclerBindingRegistry registry = RecyclerBindingRegistry.getInstance();
        for (TargetManager.TargetRef ref : tile.getTargetManager().getTargets()) {
            registry.updateAdapter(ref, storage);
        }
    }

    public void tick(int tickCounter) {
        this.tickCounter = tickCounter;
        if (!tile.isActive()) return;

        // 1. 检查并维护目标适配器
        refreshAdapters();

        // 2. 执行回收
        collectFromTargets();

        // 3. 批量注入
        flushCollector();

        // 4. 全量校验（低频）
        int fullScanInterval = AE2EnhancedConfig.recycler.fullScanIntervalTicks;
        if (fullScanInterval > 0 && tickCounter % fullScanInterval == 0) {
            rebuildIndex();
        }
    }

    public long getLastRecycledCount() {
        return lastRecycledCount;
    }

    // ---- 目标管理 ----

    private void refreshAdapters() {
        // 移除失效目标
        adapters.entrySet().removeIf(entry -> {
            TargetManager.TargetRef ref = entry.getKey();
            if (!tile.getTargetManager().getTargets().contains(ref)) {
                entry.getValue().invalidate();
                snapshots.remove(ref);
                return true;
            }
            return false;
        });

        // 添加新目标
        for (TargetManager.TargetRef ref : tile.getTargetManager().getTargets()) {
            if (adapters.containsKey(ref)) continue;
            TargetAdapter adapter = createAdapter(ref);
            if (adapter != null) {
                adapters.put(ref, adapter);
            }
        }
    }

    private TargetAdapter createAdapter(TargetManager.TargetRef ref) {
        World targetWorld = DimensionManager.getWorld(ref.dimId);
        if (targetWorld == null || !targetWorld.isBlockLoaded(ref.pos)) return null;
        TileEntity te = targetWorld.getTileEntity(ref.pos);
        if (te == null) return null;
        return AdapterFactory.create(te, ref.face);
    }

    private void rebuildIndex() {
        snapshots.clear();
        for (Map.Entry<TargetManager.TargetRef, TargetAdapter> entry : adapters.entrySet()) {
            List<ItemStack> contents = entry.getValue().scan(true);
            snapshots.put(entry.getKey(), new RecyclerIndex.TargetAdapterSnapshot(
                    tile.getWorld().getTotalWorldTime(), contents));
        }
        index.rebuild(snapshots);
    }

    // ---- 回收逻辑 ----

    private void collectFromTargets() {
        long currentTick = tile.getWorld().getTotalWorldTime();
        boolean heartbeat = tickCounter % AE2EnhancedConfig.recycler.heartbeatIntervalTicks == 0;

        for (Map.Entry<TargetManager.TargetRef, TargetAdapter> entry : adapters.entrySet()) {
            TargetManager.TargetRef ref = entry.getKey();
            TargetAdapter adapter = entry.getValue();

            List<ItemStack> current = adapter.scan(true);
            RecyclerIndex.TargetAdapterSnapshot oldSnapshot = snapshots.get(ref);

            if (!heartbeat && oldSnapshot != null && isSnapshotEqual(oldSnapshot.contents, current)) {
                continue; // 无变化，跳过
            }

            for (ItemStack stack : current) {
                if (stack.isEmpty()) continue;
                AEItemKey key = AEItemKey.of(stack);
                if (key == null) continue;
                ItemStack extracted = adapter.extract(key, false);
                if (extracted != null && !extracted.isEmpty()) {
                    collector.add(extracted);
                }
            }

            // 提取后重新扫描，更新快照
            List<ItemStack> afterExtract = adapter.scan(true);
            snapshots.put(ref, new RecyclerIndex.TargetAdapterSnapshot(currentTick, afterExtract));
        }
    }

    private boolean isSnapshotEqual(List<ItemStack> old, List<ItemStack> current) {
        if (old.size() != current.size()) return false;
        // 简化比较：只比较总数量
        long oldCount = 0, newCount = 0;
        for (ItemStack s : old) oldCount += s.getCount();
        for (ItemStack s : current) newCount += s.getCount();
        return oldCount == newCount;
    }

    private void flushCollector() {
        if (collector.isEmpty()) return;

        IManagedGridNode node = tile.getMainNode();
        if (!node.isActive()) return;

        IGrid grid = node.getGrid();
        if (grid == null) return;
        IStorageService storageService = grid.getService(IStorageService.class);
        if (storageService == null) return;
        MEStorage storage = storageService.getInventory();
        syncStorageAdapter(storage);

        List<Object2LongMap.Entry<AEItemKey>> changes = collector.drain();
        long totalInjected = 0;
        for (Object2LongMap.Entry<AEItemKey> entry : changes) {
            AEItemKey key = entry.getKey();
            long amount = entry.getLongValue();
            long remaining = storage.insert(key, amount, Actionable.MODULATE, getActionSource());
            totalInjected += amount - remaining;
        }
        lastRecycledCount = totalInjected;
    }

    // ---- MEStorage ----

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // 回收节点不接受外部注入
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!(what instanceof AEItemKey)) return 0;
        AEItemKey itemKey = (AEItemKey) what;
        List<TargetManager.TargetRef> refs = new ArrayList<>(index.getTargets(itemKey));
        if (refs.isEmpty()) return 0;

        long requested = amount;
        long collected = 0;

        for (TargetManager.TargetRef ref : refs) {
            TargetAdapter adapter = adapters.get(ref);
            if (adapter == null) continue;
            int requestAmount = (int) Math.min(requested, Integer.MAX_VALUE);
            AEItemKey request = AEItemKey.of(itemKey.toStack(requestAmount));
            ItemStack got = adapter.extract(request, mode == Actionable.SIMULATE);
            if (got == null || got.isEmpty()) continue;

            collected += got.getCount();
            requested -= got.getCount();
            if (requested <= 0) break;
        }

        if (collected > 0 && mode == Actionable.MODULATE) {
            rebuildIndexForRef(null); // 简化处理：下次心跳重建
        }
        return collected;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (AEItemKey key : index.getAllTypes()) {
            long total = 0;
            for (TargetManager.TargetRef ref : index.getTargets(key)) {
                RecyclerIndex.TargetAdapterSnapshot snap = snapshots.get(ref);
                if (snap == null) continue;
                for (ItemStack stack : snap.contents) {
                    AEItemKey stackKey = AEItemKey.of(stack);
                    if (key.equals(stackKey)) {
                        total += stack.getCount();
                    }
                }
            }
            if (total > 0) {
                out.add(key, total);
            }
        }
    }

    @Override
    public TextComponentString getDescription() {
        return new TextComponentString("ME Network Recycler");
    }

    // ---- IStorageProvider ----

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(this, 0);
    }

    // ---- 辅助 ----

    private IActionSource getActionSource() {
        return IActionSource.ofMachine(tile);
    }

    private void rebuildIndexForRef(TargetManager.TargetRef ref) {
        // 简化：下次心跳重建
    }
}

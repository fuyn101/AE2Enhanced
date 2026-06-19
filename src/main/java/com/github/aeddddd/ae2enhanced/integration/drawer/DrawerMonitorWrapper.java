package com.github.aeddddd.ae2enhanced.integration.drawer;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IBaseMonitor;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.storage.IMEInventoryHandler;
import ae2.api.storage.MEStorage;
import ae2.api.storage.IMEMonitorHandlerReceiver;
import ae2.api.storage.AEKeyType;
import ae2.me.storage.ITickingMonitor;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽屉模组监视器包装器.
 *
 * <p>将 {@link IDrawerIndexAdapter} 包装为 AE2 的 {@link MEStorage} + {@link ITickingMonitor},
 * 统一处理 onTick() 差异检测、listener 通知、以及主动操作后的增量更新.</p>
 *
 * <p>本类是无条件加载的公共类,不引用任何第三方抽屉模组类,常量池安全.</p>
 */
public class DrawerMonitorWrapper implements MEStorage<AEItemKey>, ITickingMonitor {

    private final IDrawerIndexAdapter adapter;
    private final AEKeyType<AEItemKey> channel;
    private final Map<IMEMonitorHandlerReceiver<AEItemKey>, Object> listeners = new ConcurrentHashMap<>();
    private IActionSource mySource;
    private KeyCounter<AEItemKey> currentlyCached;

    public DrawerMonitorWrapper(IDrawerIndexAdapter adapter, AEKeyType<AEItemKey> channel) {
        this.adapter = adapter;
        this.channel = channel;
        this.currentlyCached = channel.createList();
        this.adapter.getAvailableItems(this.currentlyCached);
    }

    // ---- IBaseMonitor ----

    @Override
    public void addListener(IMEMonitorHandlerReceiver<AEItemKey> l, Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<AEItemKey> l) {
        this.listeners.remove(l);
    }

    private void postDifference(Iterable<AEItemKey> changes) {
        for (Map.Entry<IMEMonitorHandlerReceiver<AEItemKey>, Object> entry : this.listeners.entrySet()) {
            IMEMonitorHandlerReceiver<AEItemKey> receiver = entry.getKey();
            if (receiver.isValid(entry.getValue())) {
                receiver.postChange(this, changes, this.mySource);
            }
        }
    }

    // ---- ITickingMonitor ----

    @Override
    public TickRateModulation onTick() {
        KeyCounter<AEItemKey> currentList = this.channel.createList();
        this.adapter.getAvailableItems(currentList);

        for (AEItemKey cached : this.currentlyCached) {
            cached.setStackSize(-cached.getStackSize());
        }
        for (AEItemKey current : currentList) {
            this.currentlyCached.add(current);
        }

        List<AEItemKey> changes = new ArrayList<>();
        for (AEItemKey entry : this.currentlyCached) {
            if (entry.getStackSize() != 0) {
                changes.add(entry);
            }
        }
        this.currentlyCached = currentList;

        if (!changes.isEmpty()) {
            this.postDifference(changes);
            return TickRateModulation.URGENT;
        }
        return TickRateModulation.SLOWER;
    }

    @Override
    public void setActionSource(IActionSource source) {
        this.mySource = source;
    }

    // ---- IMEInventoryHandler (via MEStorage) ----

    @Override
    public AEItemKey injectItems(AEItemKey input, Actionable type, IActionSource src) {
        AEItemKey result = this.adapter.injectItems(input, type, src);
        if (type == Actionable.MODULATE && result != null && result.getStackSize() < input.getStackSize()) {
            long injected = input.getStackSize() - result.getStackSize();
            AEItemKey diff = input.copy();
            diff.setStackSize(injected);
            this.currentlyCached.add(diff);
            this.postDifference(Collections.singletonList(diff));
        } else if (type == Actionable.MODULATE && result == null) {
            AEItemKey diff = input.copy();
            this.currentlyCached.add(diff);
            this.postDifference(Collections.singletonList(diff));
        }
        return result;
    }

    @Override
    public AEItemKey extractItems(AEItemKey request, Actionable mode, IActionSource src) {
        AEItemKey result = this.adapter.extractItems(request, mode, src);
        if (mode == Actionable.MODULATE && result != null) {
            AEItemKey diff = result.copy();
            diff.setStackSize(-result.getStackSize());
            this.currentlyCached.add(diff);
            this.postDifference(Collections.singletonList(diff));
        }
        return result;
    }

    @Override
    public KeyCounter<AEItemKey> getAvailableItems(KeyCounter<AEItemKey> out) {
        return this.adapter.getAvailableItems(out);
    }

    @Override
    public AEKeyType<AEItemKey> getChannel() {
        return this.channel;
    }

    @Override
    public AccessRestriction getAccess() {
        return this.adapter.getAccess();
    }

    @Override
    public boolean isPrioritized(AEItemKey input) {
        return this.adapter.isPrioritized(input);
    }

    @Override
    public boolean canAccept(AEItemKey input) {
        return this.adapter.canAccept(input);
    }

    @Override
    public int getPriority() {
        return this.adapter.getPriority();
    }

    @Override
    public int getSlot() {
        return this.adapter.getSlot();
    }

    @Override
    public boolean validForPass(int pass) {
        return this.adapter.validForPass(pass);
    }

    @Override
    public KeyCounter<AEItemKey> getStorageList() {
        KeyCounter<AEItemKey> list = this.channel.createList();
        return this.adapter.getAvailableItems(list);
    }
}

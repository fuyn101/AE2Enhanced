package com.github.aeddddd.ae2enhanced.integration.drawer;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.me.storage.ITickingMonitor;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * 抽屉模组监视器包装器.
 *
 * <p>将 {@link IDrawerIndexAdapter} 包装为 AE2S 的 {@link MEStorage} + {@link ITickingMonitor},
 * 统一处理 onTick() 差异检测.主动操作后同步更新缓存计数器.</p>
 *
 * <p>本类是无条件加载的公共类,不引用任何第三方抽屉模组类,常量池安全.</p>
 */
public class DrawerMonitorWrapper implements MEStorage, ITickingMonitor {

    private final IDrawerIndexAdapter adapter;
    private KeyCounter currentlyCached;

    public DrawerMonitorWrapper(IDrawerIndexAdapter adapter, AEKeyType channel) {
        this.adapter = adapter;
        this.currentlyCached = new KeyCounter();
        this.adapter.getAvailableStacks(this.currentlyCached);
    }

    // ---- ITickingMonitor ----

    @Override
    public TickRateModulation onTick() {
        KeyCounter currentList = new KeyCounter();
        this.adapter.getAvailableStacks(currentList);

        KeyCounter changes = new KeyCounter();
        for (Object2LongMap.Entry<AEKey> entry : currentList) {
            changes.add(entry.getKey(), entry.getValue());
        }
        for (Object2LongMap.Entry<AEKey> entry : this.currentlyCached) {
            changes.add(entry.getKey(), -entry.getValue());
        }
        changes.removeZeros();

        this.currentlyCached = currentList;

        if (!changes.isEmpty()) {
            return TickRateModulation.URGENT;
        }
        return TickRateModulation.SLOWER;
    }

    // ---- MEStorage ----

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!(what instanceof AEItemKey)) {
            return amount;
        }
        long leftover = this.adapter.injectItems((AEItemKey) what, amount, mode, source);
        if (mode == Actionable.MODULATE && leftover < amount) {
            this.currentlyCached.add(what, amount - leftover);
        }
        return leftover;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!(what instanceof AEItemKey)) {
            return 0;
        }
        long extracted = this.adapter.extractItems((AEItemKey) what, amount, mode, source);
        if (mode == Actionable.MODULATE && extracted > 0) {
            this.currentlyCached.add(what, -extracted);
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        this.adapter.getAvailableStacks(out);
    }

    @Override
    public ITextComponent getDescription() {
        return new TextComponentString("Drawer Network");
    }
}

package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;

import javax.annotation.Nonnull;

/**
 * ME 网络回收节点向 AE2 网络注册的单一 handler.
 *
 * <p>对 AE2 网络表现为一个存储源,实际回收操作直接写入超维度仓储中枢.</p>
 */
public class RecyclerNetworkHandler implements IMEInventoryHandler<IAEItemStack>, IMEMonitor<IAEItemStack> {

    private final TileMENetworkRecycler tile;
    private long lastRecycledCount = 0;

    public RecyclerNetworkHandler(TileMENetworkRecycler tile) {
        this.tile = tile;
    }

    public void onLoad() {
        // TODO: 注册到 AE2 网络
    }

    public void onInvalidate() {
        // TODO: 从 AE2 网络注销
    }

    public void tick(int tickCounter) {
        // TODO: 执行回收逻辑
    }

    public long getLastRecycledCount() {
        return lastRecycledCount;
    }

    // ---- IMEInventoryHandler ----

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        // 回收节点不真正接受外部注入
        return input;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable type, IActionSource src) {
        // TODO: 从目标容器提取物品
        return null;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        // TODO: 返回目标容器中的物品视图
        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        return getAvailableItems(getChannel().createList());
    }

    // ---- IMEMonitor ----

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEItemStack> l, Object verificationToken) {
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEItemStack> l) {
    }
}

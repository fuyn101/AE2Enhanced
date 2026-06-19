package com.github.aeddddd.ae2enhanced.tile;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.util.AECableType;
import ae2.tile.grid.AENetworkedTile;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;

/**
 * AE2S 网络 TileEntity 基类.
 *
 * 继承 {@link AENetworkedTile}，统一由 AE2S 管理 {@code IManagedGridNode} 生命周期。
 * 子类如需自定义节点行为，可覆盖 {@link #createMainNode()}；
 * 如需在节点失效/区块卸载时执行额外清理，可覆盖 {@link #onProxyInvalidate()} /
 * {@link #onProxyChunkUnload()}（命名保留以兼容旧代码语义）。
 */
public abstract class TileAENetworkBase extends AENetworkedTile implements IInWorldGridNodeHost {

    protected boolean needsReady = false;

    /**
     * proxy invalidate 时的额外清理回调。
     */
    protected void onProxyInvalidate() {
    }

    /**
     * proxy chunk unload 时的额外清理回调。
     */
    protected void onProxyChunkUnload() {
    }

    @Override
    public IGridNode getGridNode(@Nonnull EnumFacing dir) {
        return getMainNode().getNode();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        onProxyInvalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        onProxyChunkUnload();
    }

    // ---- 结构控制 ----

    public abstract void disassemble();

    // ---- 状态访问 ----

    public boolean needsReady() {
        return needsReady;
    }

    public void clearNeedsReady() {
        needsReady = false;
    }
}

package com.github.aeddddd.ae2enhanced.central;

import ae2.api.config.Actionable;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import ae2.me.storage.NullInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 超维度存储节点.
 *
 * <p>实现 {@link IStorageProvider},将远程子网的全部存储通过一个
 * {@link RemoteMEStorage} 暴露到本地网格.远程子网由 {@link SubNetLink} 描述.</p>
 */
public class HyperdimensionalStorageNode implements IStorageProvider {

    private final HyperdimensionalStorageHost host;

    public HyperdimensionalStorageNode(@Nonnull HyperdimensionalStorageHost host) {
        this.host = host;
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(new RemoteMEStorage(), 0);
    }

    /**
     * 当宿主链接发生变化时调用,请求 AE2S 刷新本节点的存储供应.
     */
    public void requestUpdate(@Nullable IManagedGridNode node) {
        if (node != null) {
            IStorageProvider.requestUpdate(node);
        }
    }

    /**
     * 获取远程子网当前暴露的 {@link IGridNode},若未加载或不存在则返回 null.
     */
    @Nullable
    public IGridNode resolveRemoteNode() {
        SubNetLink link = host.getSubNetLink();
        if (link == null) {
            return null;
        }
        World remoteWorld = getRemoteWorld(link.dimension);
        if (remoteWorld == null) {
            return null;
        }
        return GridHelper.getExposedNode(remoteWorld, link.pos, link.side);
    }

    @Nullable
    private World getRemoteWorld(int dimension) {
        World ownWorld = host.getWorld();
        if (ownWorld != null && ownWorld.provider.getDimension() == dimension) {
            return ownWorld;
        }
        return DimensionManager.getWorld(dimension);
    }

    /**
     * 宿主必须提供的信息.
     */
    public interface HyperdimensionalStorageHost {
        @Nullable
        SubNetLink getSubNetLink();

        @Nullable
        World getWorld();

        @Nonnull
        BlockPos getPos();

        void saveChanges();
    }

    private class RemoteMEStorage implements MEStorage {

        @Override
        public ITextComponent getDescription() {
            return new TextComponentTranslation("gui.ae2enhanced.hyperdimensional_storage");
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage remote = getRemoteInventory(source);
            if (remote == null) {
                return amount;
            }
            return remote.insert(what, amount, mode, source);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage remote = getRemoteInventory(source);
            if (remote == null) {
                return 0;
            }
            return remote.extract(what, amount, mode, source);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            MEStorage remote = getRemoteInventory(null);
            if (remote != null) {
                remote.getAvailableStacks(out);
            }
        }

        @Nullable
        private MEStorage getRemoteInventory(@Nullable IActionSource source) {
            IGridNode remoteNode = resolveRemoteNode();
            if (remoteNode == null || remoteNode.getGrid() == null) {
                return null;
            }
            IGrid remoteGrid = remoteNode.getGrid();

            // 防止同网格循环引用(本地与远程实际是同一个网格时拒绝操作)
            if (source != null) {
                IGrid sourceGrid = source.machine()
                        .map(IActionHost::getActionableNode)
                        .map(IGridNode::getGrid)
                        .orElse(null);
                if (sourceGrid == remoteGrid) {
                    return null;
                }
            }

            IStorageService storage = remoteGrid.getStorageService();
            return storage != null ? storage.getInventory() : null;
        }
    }
}

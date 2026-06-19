package com.github.aeddddd.ae2enhanced.tile;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IManagedGridNode;
import ae2.api.storage.IStorageProvider;
import ae2.api.util.AECableType;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRecyclerSync;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerBindingRegistry;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerNetworkHandler;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager.TargetRef;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ME 网络回收节点的 TileEntity.
 *
 * <p>接入 AE2 网络,管理远程/跨维度回收目标,通过 RecyclerNetworkHandler
 * 向网络暴露存储视图,实际产物直接写入网络存储.</p>
 */
public class TileMENetworkRecycler extends TileAENetworkBase implements ITickable {

    private final TargetManager targetManager = new TargetManager();
    private final RecyclerNetworkHandler networkHandler = new RecyclerNetworkHandler(this);

    private boolean lastPowered = false;
    private boolean lastActive = false;
    private int clientFlags = 0;
    private int tickCounter = 0;

    // 客户端同步字段
    private int clientTargetCount = 0;
    private long clientLastRecycledCount = 0;
    private boolean clientActive = false;
    private boolean clientPowered = false;

    public TileMENetworkRecycler() {
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(AE2EnhancedConfig.recycler.idlePower)
                .setVisualRepresentation(ae2.api.stacks.AEItemKey.of(new ItemStack(BlockRegistry.ME_NETWORK_RECYCLER)))
                .addService(IStorageProvider.class, networkHandler);
    }

    // ---- TileAENetworkBase ----

    @Override
    public void disassemble() {
        if (world != null && !world.isRemote) {
            dropContents();
        }
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull EnumFacing dir) {
        return AECableType.SMART;
    }

    // ---- 生命周期 ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote) {
            networkHandler.onLoad();
            registerAllBindings();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (networkHandler != null) {
            networkHandler.onInvalidate();
        }
        unregisterAllBindings();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (networkHandler != null) {
            networkHandler.onInvalidate();
        }
        unregisterAllBindings();
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        syncClientState();

        tickCounter++;
        if (isActive()) {
            networkHandler.tick(tickCounter);
        }

        if (tickCounter % 10 == 0) {
            syncToWatchingPlayers();
        }
    }

    private void syncClientState() {
        boolean powered = isPowered();
        boolean active = isActive();
        if (powered != lastPowered || active != lastActive) {
            lastPowered = powered;
            lastActive = active;
            int flags = 0;
            if (powered) flags |= 1;
            if (active) flags |= 2;
            this.clientFlags = flags;
            if (world != null) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 2);
            }
        }
    }

    // ---- 目标管理 ----

    public TargetManager getTargetManager() {
        return targetManager;
    }

    public RecyclerNetworkHandler getNetworkHandler() {
        return networkHandler;
    }

    public boolean isPowered() {
        return getMainNode().isPowered();
    }

    public boolean isActive() {
        return getMainNode().isActive();
    }

    public int getClientFlags() {
        return clientFlags;
    }

    public int getClientTargetCount() {
        return world.isRemote ? clientTargetCount : targetManager.getTargetCount();
    }

    public long getClientLastRecycledCount() {
        return world.isRemote ? clientLastRecycledCount : networkHandler.getLastRecycledCount();
    }

    public boolean isClientActive() {
        return world.isRemote ? clientActive : isActive();
    }

    public boolean isClientPowered() {
        return world.isRemote ? clientPowered : isPowered();
    }

    public void handleSyncPacket(PacketRecyclerSync packet) {
        this.clientTargetCount = packet.getTargetCount();
        this.clientLastRecycledCount = packet.getLastRecycledCount();
        this.clientActive = packet.isActive();
        this.clientPowered = packet.isPowered();
    }

    private void syncToWatchingPlayers() {
        if (world == null || world.isRemote) return;
        PacketRecyclerSync packet = new PacketRecyclerSync(
                pos,
                targetManager.getTargetCount(),
                networkHandler.getLastRecycledCount(),
                isActive(),
                isPowered()
        );
        for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities) {
            if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                double distSq = player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (distSq < 64 * 64) {
                    AE2Enhanced.network.sendTo(packet, (net.minecraft.entity.player.EntityPlayerMP) player);
                }
            }
        }
    }

    public void dropContents() {
        // TODO: 破坏方块时掉落绑定工具或内部物品（如有）
    }

    // ---- 绑定管理 ----

    public boolean tryBindTarget(@Nonnull TargetRef target) {
        if (targetManager.getTargetCount() >= AE2EnhancedConfig.recycler.maxTargets) {
            return false;
        }
        if (targetManager.addTarget(target)) {
            RecyclerBindingRegistry.getInstance().register(target, networkHandler);
            markDirty();
            return true;
        }
        return false;
    }

    public boolean tryUnbindTarget(@Nonnull TargetRef target) {
        if (targetManager.removeTarget(target)) {
            RecyclerBindingRegistry.getInstance().unregister(target);
            markDirty();
            return true;
        }
        return false;
    }

    public void clearTargets() {
        RecyclerBindingRegistry.getInstance().unregisterAll(networkHandler);
        targetManager.clear();
        markDirty();
    }

    private void unregisterAllBindings() {
        RecyclerBindingRegistry.getInstance().unregisterAll(networkHandler);
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Targets", Constants.NBT.TAG_LIST)) {
            targetManager.deserializeNBT(compound.getTagList("Targets", Constants.NBT.TAG_COMPOUND));
        }
    }

    private void registerAllBindings() {
        for (TargetRef target : targetManager.getTargets()) {
            RecyclerBindingRegistry.getInstance().register(target, networkHandler);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        NBTTagList list = targetManager.serializeNBT();
        if (list != null && list.tagCount() > 0) {
            compound.setTag("Targets", list);
        }
        return compound;
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("ClientFlags", clientFlags);
        return tag;
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt.getNbtCompound() != null) {
            this.clientFlags = pkt.getNbtCompound().getInteger("ClientFlags");
            if (world != null) {
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
        }
    }
}

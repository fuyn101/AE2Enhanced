package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerBindingRegistry;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerBindingState;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerNetworkHandler;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager.TargetRef;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;

/**
 * ME 网络回收节点的 TileEntity.
 *
 * <p>接入 AE2 网络,管理远程/跨维度回收目标,通过单一 RecyclerNetworkHandler
 * 向网络暴露存储视图,实际产物直接写入超维度仓储中枢.</p>
 */
public class TileMENetworkRecycler extends TileAENetworkBase implements ITickable, ICellContainer {

    private final TargetManager targetManager = new TargetManager();
    private final RecyclerNetworkHandler networkHandler = new RecyclerNetworkHandler(this);

    private boolean lastPowered = false;
    private boolean lastActive = false;
    private int clientFlags = 0;
    private int tickCounter = 0;
    private final RecyclerBindingState bindingState = new RecyclerBindingState();
    private static final int BINDING_DURATION_TICKS = 600; // 30 秒

    public TileMENetworkRecycler() {
    }

    // ---- TileAENetworkBase ----

    @Override
    protected String getProxyName() {
        return "me_network_recycler";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.ME_NETWORK_RECYCLER);
    }

    @Override
    public void disassemble() {
        if (world != null && !world.isRemote) {
            dropContents();
        }
    }

    @Override
    public void securityBreak() {
        if (world != null && !world.isRemote) {
            world.destroyBlock(pos, false);
        }
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        return getProxy().getNode();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // ---- ICellContainer ----

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
        if (channel instanceof IItemStorageChannel) {
            return Collections.singletonList(networkHandler);
        }
        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void blinkCell(int slot) {
    }

    @Override
    public void saveChanges(ICellInventory<?> inv) {
    }

    // ---- 生命周期 ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote) {
            networkHandler.onLoad();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (networkHandler != null) {
            networkHandler.onInvalidate();
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (networkHandler != null) {
            networkHandler.onInvalidate();
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (needsReady()) {
            clearNeedsReady();
            getProxy().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
            getProxy().setIdlePowerUsage(AE2EnhancedConfig.recycler.idlePower);
            getProxy().onReady();
        }

        syncClientState();

        if (!isActive()) return;

        tickCounter++;
        networkHandler.tick(tickCounter);
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

    // ---- AE 网络事件 ----

    @MENetworkEventSubscribe
    public void chanRender(MENetworkChannelsChanged c) {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @MENetworkEventSubscribe
    public void powerRender(MENetworkPowerStatusChange c) {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
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
        return getProxy().isPowered();
    }

    public boolean isActive() {
        return getProxy().isActive();
    }

    public int getClientFlags() {
        return clientFlags;
    }

    public void dropContents() {
        // TODO: 破坏方块时掉落绑定工具或内部物品（如有）
    }

    // ---- 绑定管理 ----

    public boolean toggleBindingMode(@Nonnull java.util.UUID playerId) {
        if (bindingState.isBinding(playerId, this.world.getTotalWorldTime())) {
            bindingState.clear();
            RecyclerBindingRegistry.clearBinding(playerId);
            return false;
        }
        bindingState.start(playerId, this.world.getTotalWorldTime(), BINDING_DURATION_TICKS);
        RecyclerBindingRegistry.setBinding(playerId, this.world.provider.getDimension(), this.pos);
        return true;
    }

    public boolean isBinding(@Nonnull java.util.UUID playerId) {
        return bindingState.isBinding(playerId, this.world.getTotalWorldTime());
    }

    public boolean tryBindTarget(@Nonnull TargetRef target) {
        if (targetManager.getTargetCount() >= AE2EnhancedConfig.recycler.maxTargets) {
            return false;
        }
        return targetManager.addTarget(target);
    }

    public boolean tryUnbindTarget(@Nonnull TargetRef target) {
        return targetManager.removeTarget(target);
    }

    public void clearTargets() {
        targetManager.clear();
        markDirty();
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Targets", Constants.NBT.TAG_LIST)) {
            targetManager.deserializeNBT(compound.getTagList("Targets", Constants.NBT.TAG_COMPOUND));
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

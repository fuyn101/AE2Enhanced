package com.github.aeddddd.ae2enhanced.tile;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.AECableType;
import ae2.me.helpers.MachineSource;
import ae2.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 区块供电节点的 TileEntity.
 *
 * <p>本应消耗 1 个 AE 频道，从连接的 ME 网络能量/物品通道提取能量，
 * 向所在区块(16×16)内所有可接收 Forge Energy 的设备供能。</p>
 *
 * <p>AE2S 迁移期间：原 RF/Energy 存储通道实现已不存在，供能逻辑暂时存根。
 * 仍然保持网络节点、朝向、状态同步与目标缓存功能。</p>
 */
public class TileChunkPowerNode extends TileAENetworkBase implements ITickable, IActionHost {

    private static final int CACHE_REFRESH_INTERVAL = 20;

    /** 区块供电黑名单：这些方块不会被供能（避免自我循环或兼容问题） */
    private static final Set<String> BLACKLIST = new HashSet<>();
    static {
        BLACKLIST.add("ae2enhanced:rf_access_node");
    }

    private EnumFacing forward = EnumFacing.NORTH;
    private MachineSource machineSource;

    // 目标设备缓存(只存 BlockPos,每 tick 重新获取 TE 和 cap)
    protected final List<BlockPos> cachedTargets = new ArrayList<>();
    private int cacheRefreshCooldown = 0;

    /**
     * 获取当前缓存的供电目标位置列表（副本）.
     */
    public List<BlockPos> getCachedTargets() {
        return new ArrayList<>(cachedTargets);
    }

    // 客户端同步
    private int clientFlags = 0;
    private boolean lastPowered = false;
    private boolean lastActive = false;

    public TileChunkPowerNode() {
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(32)
                .setExposedOnSides(getExposedSides())
                .setVisualRepresentation(AEItemKey.of(new ItemStack(BlockRegistry.CHUNK_POWER_NODE)));
    }

    private java.util.Set<EnumFacing> getExposedSides() {
        return EnumSet.of(this.forward.getOpposite());
    }

    private void updateExposedSides() {
        getMainNode().setExposedOnSides(getExposedSides());
    }

    // ---------- 朝向与代理 ----------

    public void setForward(EnumFacing facing) {
        this.forward = facing != null ? facing : EnumFacing.NORTH;
        updateExposedSides();
        markDirty();
    }

    public EnumFacing getForward() {
        return this.forward;
    }

    @Override
    public void disassemble() {
        // 无需额外清理
    }

    public void securityBreak() {
        if (world != null && !world.isRemote) {
            world.destroyBlock(pos, true);
        }
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull EnumFacing dir) {
        if (this.forward != null && dir == this.forward.getOpposite()) {
            return AECableType.SMART;
        }
        return AECableType.NONE;
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public IGridNode getGridNode(@Nonnull EnumFacing dir) {
        if (this.forward != null && dir == this.forward.getOpposite()) {
            return getMainNode().getNode();
        }
        return null;
    }

    // ---------- 初始化与 tick ----------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (!isActive()) return;

        doPowerTick();
        syncClientState();
    }

    private void doPowerTick() {
        if (cacheRefreshCooldown <= 0) {
            refreshTargetCache();
            cacheRefreshCooldown = CACHE_REFRESH_INTERVAL;
        } else {
            cacheRefreshCooldown--;
        }

        if (cachedTargets.isEmpty()) return;

        // TODO: optional mod dependency — AE2S 不再提供 RF/Energy 存储通道。
        // 如需恢复供能，需要接入 AE2S 的能量服务或外部 RF 网络，
        // 并替换下面的 energyMonitor 提取/注入逻辑。
        for (BlockPos targetPos : cachedTargets) {
            // 保留 TE 与 cap 探测代码，便于后续实现
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(targetPos);
            if (te == null || te.isInvalid()) continue;

            IEnergyStorage cap = null;
            for (EnumFacing facing : EnumFacing.values()) {
                if (te.hasCapability(CapabilityEnergy.ENERGY, facing)) {
                    IEnergyStorage c = te.getCapability(CapabilityEnergy.ENERGY, facing);
                    if (c != null && c.canReceive()) {
                        cap = c;
                        break;
                    }
                }
            }
            if (cap == null) continue;

            // Stub: 实际供能逻辑待能量通道适配完成后实现。
        }
    }

    /**
     * 扫描本区块内所有可接收能量的 TileEntity,缓存其位置.
     *
     * <p>优化：直接读取当前 chunk 的 {@code tileEntities} 映射,避免每 20 tick
     * 遍历全图 {@code world.loadedTileEntityList}.</p>
     *
     * <p>某些模组(如 Mekanism)的 {@code IEnergyStorage} capability 只在特定朝向
     * 上暴露为可接收({@code canReceive() == true}).因此需要遍历 6 个面查找有效输入面,
     * 而非直接传 {@code null}.</p>
     */
    protected void refreshTargetCache() {
        cachedTargets.clear();
        Chunk chunk = world.getChunk(pos);
        if (chunk == null) return;

        for (net.minecraft.tileentity.TileEntity te : chunk.getTileEntityMap().values()) {
            if (te == null || te.isInvalid()) continue;
            if (te == this) continue;

            BlockPos tp = te.getPos();
            boolean canReceive = false;
            for (EnumFacing facing : EnumFacing.values()) {
                if (te.hasCapability(CapabilityEnergy.ENERGY, facing)) {
                    IEnergyStorage cap = te.getCapability(CapabilityEnergy.ENERGY, facing);
                    if (cap != null && cap.canReceive()) {
                        canReceive = true;
                        break;
                    }
                }
            }
            if (!canReceive) continue;

            // 黑名单检查（仅对找到可接收面的目标才获取 blockId）
            String blockId = world.getBlockState(tp).getBlock().getRegistryName().toString();
            if (BLACKLIST.contains(blockId)) continue;

            cachedTargets.add(tp.toImmutable());
        }
    }

    // ---------- 状态同步 ----------

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
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    public boolean isPowered() {
        if (world != null && world.isRemote) {
            return (this.clientFlags & 1) == 1;
        }
        return getMainNode().isPowered();
    }

    public boolean isActive() {
        if (world != null && world.isRemote) {
            return isPowered() && (this.clientFlags & 2) == 2;
        }
        return getMainNode().isActive();
    }

    // ---------- 辅助 ----------

    private MachineSource getMachineSource() {
        if (this.machineSource == null) {
            this.machineSource = new MachineSource(this);
        }
        return this.machineSource;
    }

    // ---------- NBT ----------

    @Override
    public void readFromNBT(net.minecraft.nbt.NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.forward = EnumFacing.byIndex(compound.getInteger("forward"));
        this.clientFlags = compound.getInteger("clientFlags");
        updateExposedSides();
    }

    @Override
    public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("forward", this.forward.getIndex());
        compound.setInteger("clientFlags", this.clientFlags);
        return compound;
    }

    // ---------- 网络同步 ----------

    @Override
    public net.minecraft.nbt.NBTTagCompound getUpdateTag() {
        net.minecraft.nbt.NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("forward", this.forward.getIndex());
        tag.setInteger("clientFlags", this.clientFlags);
        return tag;
    }

    @Override
    public void handleUpdateTag(net.minecraft.nbt.NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        this.forward = EnumFacing.byIndex(tag.getInteger("forward"));
        this.clientFlags = tag.getInteger("clientFlags");
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }
}

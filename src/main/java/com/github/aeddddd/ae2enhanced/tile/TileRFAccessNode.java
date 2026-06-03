package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import appeng.api.networking.storage.IStorageGrid;
import appeng.me.GridAccessException;

/**
 * RF 访问节点 TileEntity。
 * 纯桥接器：将外部 RF 网络（Forge Energy）接入 AE2 ME 网络的 RF 存储通道。
 * 本身不保留任何本地缓存，所有 RF 直接进出 ME 网络。
 */
public class TileRFAccessNode extends TileAENetworkBase
        implements IGridTickable, IActionHost, IEnergyStorage, ITickable {

    private MachineSource machineSource;
    private int creativeBoostCooldown = 0;

    // === TileAENetworkBase ===

    @Override
    protected String getProxyName() {
        return "rf_access_node";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.RF_ACCESS_NODE);
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public void disassemble() {
        // RF 节点不参与多方块结构，无需解体逻辑
    }

    @Override
    public void securityBreak() {
        this.world.destroyBlock(this.pos, true);
    }

    // === IActionHost ===

    @Override
    public IGridNode getActionableNode() {
        return this.getProxy().getNode();
    }

    // === ITickable (原版 tick，用于初始化 AE 网络代理) ===

    @Override
    public void update() {
        if (!this.world.isRemote && this.needsReady()) {
            this.clearNeedsReady();
            this.getProxy().onReady();
        }
        if (!this.world.isRemote) {
            doCreativeBoostTick();
        }
    }

    private void doCreativeBoostTick() {
        if (!AE2EnhancedConfig.energy.creativeRfSourceBoostEnabled) return;
        if (creativeBoostCooldown-- > 0) return;
        creativeBoostCooldown = 20;

        if (!isAdjacentToCreativeRfSource()) return;

        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return;

        long amount = AE2EnhancedConfig.energy.getParsedBoostAmount();
        if (amount <= 0) return;

        // 先模拟注入看看能被接受多少
        IAEEnergyStack rejected = monitor.injectItems(
                AEEnergyStack.create(amount), Actionable.SIMULATE, getMachineSource());
        long canAccept = amount - (rejected != null ? rejected.getStackSize() : 0);
        if (canAccept <= 0) return;

        monitor.injectItems(AEEnergyStack.create(canAccept), Actionable.MODULATE, getMachineSource());
    }

    private boolean isAdjacentToCreativeRfSource() {
        Block creativeBlock = Block.getBlockFromName("draconicevolution:creative_rf_source");
        if (creativeBlock == null) return false;
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos adjacent = this.pos.offset(facing);
            if (this.world.isBlockLoaded(adjacent) && this.world.getBlockState(adjacent).getBlock() == creativeBlock) {
                return true;
            }
        }
        return false;
    }

    // === IGridTickable ===

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return TickRateModulation.SLEEP;
    }

    // === Forge IEnergyStorage ===

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return 0;

        Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        IAEEnergyStack rejected = monitor.injectItems(
                AEEnergyStack.create(maxReceive), mode, getMachineSource());
        long rejectedSize = rejected != null ? rejected.getStackSize() : 0;
        return (int) (maxReceive - rejectedSize);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) return 0;
        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return 0;

        Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        IAEEnergyStack extracted = monitor.extractItems(
                AEEnergyStack.create(maxExtract), mode, getMachineSource());
        return extracted != null ? (int) Math.min(extracted.getStackSize(), Integer.MAX_VALUE) : 0;
    }

    @Override
    public int getEnergyStored() {
        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return 0;

        IItemList<IAEEnergyStack> list = monitor.getStorageList();
        long total = 0;
        for (IAEEnergyStack stack : list) {
            if (stack != null) total += stack.getStackSize();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        // ME 网络中无固定容量上限（超维度存储为无限），返回极大值
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    // === Capability 暴露 ===

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }

    // === NBT ===

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        return super.writeToNBT(compound);
    }

    // === 辅助 ===

    private IMEMonitor<IAEEnergyStack> getEnergyMonitor() {
        try {
            IStorageGrid storageGrid = getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return null;
            return storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    private MachineSource getMachineSource() {
        if (this.machineSource == null) {
            this.machineSource = new MachineSource(this);
        }
        return this.machineSource;
    }

    public void onBreak() {
        // 破坏时清理（如有必要）
    }
}

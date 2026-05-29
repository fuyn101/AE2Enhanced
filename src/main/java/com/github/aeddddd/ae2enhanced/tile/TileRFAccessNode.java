package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * RF 访问节点 TileEntity。
 * 通用独立的 ME-RF 桥接器，将外部 RF 能量网络接入 AE2 ME 网络的 RF 存储通道。
 * 不绑定任何平台系统，可放置于任意位置。
 */
public class TileRFAccessNode extends TileAENetworkBase
        implements IGridTickable, ICellContainer, IActionHost, IEnergyStorage, ITickable {

    private EnergyStorageAdapter energyStorage;
    private MachineSource machineSource;

    public TileRFAccessNode() {
        updateCapacityFromConfig();
    }

    private void updateCapacityFromConfig() {
        long capacity = AE2EnhancedConfig.advancedPlatform.rfNodeCapacity;
        if (this.energyStorage == null) {
            this.energyStorage = new EnergyStorageAdapter(capacity);
        } else {
            this.energyStorage.setCapacityRF(capacity);
        }
    }

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

    // === ICellContainer ===

    @Override
    public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
        List<IMEInventoryHandler> list = new ArrayList<>();
        if (channel instanceof IEnergyStorageChannel) {
            list.add(this.energyStorage);
        }
        return list;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void blinkCell(int slot) {
    }

    @Override
    public void saveChanges(ICellInventory<?> cellInventory) {
        this.markDirty();
    }

    // === Forge IEnergyStorage ===

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        appeng.api.config.Actionable mode = simulate
                ? appeng.api.config.Actionable.SIMULATE
                : appeng.api.config.Actionable.MODULATE;
        appeng.api.storage.data.IAEStack<?> rejected = this.energyStorage.injectItems(
                com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack.create(maxReceive),
                mode, getMachineSource());
        return maxReceive - (int) (rejected != null ? rejected.getStackSize() : 0);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) return 0;
        appeng.api.config.Actionable mode = simulate
                ? appeng.api.config.Actionable.SIMULATE
                : appeng.api.config.Actionable.MODULATE;
        com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack extracted =
                this.energyStorage.extractItems(
                        com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack.create(maxExtract),
                        mode, getMachineSource());
        return extracted != null ? (int) Math.min(extracted.getStackSize(), Integer.MAX_VALUE) : 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(this.energyStorage.getStoredRF(), Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(this.energyStorage.getCapacityRF(), Integer.MAX_VALUE);
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
        long stored = compound.getLong("StoredRF");
        this.energyStorage.addRF(stored);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setLong("StoredRF", this.energyStorage.getStoredRF());
        return compound;
    }

    // === 辅助 ===

    private MachineSource getMachineSource() {
        if (this.machineSource == null) {
            this.machineSource = new MachineSource(this);
        }
        return this.machineSource;
    }

    public void onBreak() {
        // 破坏时清理（如有必要）
    }

    public EnergyStorageAdapter getEnergyStorage() {
        return energyStorage;
    }
}

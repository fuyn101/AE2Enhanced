package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
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
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlatformEnergySync;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 先进中枢平台控制器 TileEntity。
 * 平台核心：ME 网络节点、RF 能量缓冲与均分分发、设施缓存管理、区块加载。
 */
public class TileAdvancedPlatformController extends TileAENetworkBase
        implements IGridTickable, ITickable, ICellContainer, IActionHost {

    // ===== 平台边界（相对控制器坐标系） =====
    private int platformSizeInChunks = 5;
    private int relativeSurfaceY = 0;
    private boolean isPlatformActive = false;

    // ===== RF 本地缓冲 =====
    private long rfBuffer = 0;
    private long rfBufferCapacity;
    private long rfExtractPerTick;

    // ===== 用电/发电设施缓存 =====
    private final List<net.minecraftforge.energy.IEnergyStorage> energyReceivers = new ArrayList<>();
    private final List<net.minecraftforge.energy.IEnergyStorage> energyProviders = new ArrayList<>();
    private int cacheRefreshCooldown = 0;

    // ===== ForgeChunkManager =====
    private ForgeChunkManager.Ticket chunkTicket = null;

    // ===== 向 ME 网络暴露的存储 =====
    private EnergyStorageAdapter networkEnergyStorage;
    private MachineSource machineSource;

    // ===== 客户端同步计数器 =====
    private int syncCooldown = 0;

    public TileAdvancedPlatformController() {
        updateConfigValues();
    }

    private void updateConfigValues() {
        this.rfBufferCapacity = AE2EnhancedConfig.advancedPlatform.controllerRfBufferCapacity;
        this.rfExtractPerTick = AE2EnhancedConfig.advancedPlatform.controllerRfExtractPerTick;
        if (this.networkEnergyStorage == null) {
            this.networkEnergyStorage = new EnergyStorageAdapter(this.rfBufferCapacity);
        } else {
            this.networkEnergyStorage.setCapacityRF(this.rfBufferCapacity);
        }
    }

    // === TileAENetworkBase ===

    @Override
    protected String getProxyName() {
        return "advanced_platform_controller";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.ADVANCED_PLATFORM_CONTROLLER);
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public void disassemble() {
        deactivatePlatform();
    }

    @Override
    public void securityBreak() {
        this.world.destroyBlock(this.pos, true);
    }

    // === IActionHost ===

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    // === ITickable ===

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (needsReady()) {
            clearNeedsReady();
            getProxy().onReady();
        }
        if (isPlatformActive && cacheRefreshCooldown > 0) {
            cacheRefreshCooldown--;
        }
    }

    // === IGridTickable ===

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isPlatformActive) return TickRateModulation.SLEEP;
        try {
            if (getProxy().getEnergy().extractAEPower(1, Actionable.SIMULATE, appeng.api.config.PowerMultiplier.CONFIG) < 1) {
                return TickRateModulation.SLEEP;
            }
        } catch (GridAccessException e) {
            return TickRateModulation.SLEEP;
        }

        doEnergyTick();
        return TickRateModulation.SAME;
    }

    // === ICellContainer ===

    @Override
    public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
        List<IMEInventoryHandler> list = new ArrayList<>();
        if (channel instanceof IEnergyStorageChannel && networkEnergyStorage != null) {
            list.add(networkEnergyStorage);
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

    // === 能量 Tick 逻辑 ===

    private void doEnergyTick() {
        if (cacheRefreshCooldown <= 0) {
            refreshFacilityCache();
            cacheRefreshCooldown = 100;
        }

        if (rfBuffer < rfBufferCapacity) {
            extractRFFromNetwork();
        }

        collectRFFromProviders();
        distributeRFToReceivers();
        injectRFBackToNetwork();
        syncEnergyToClients();
    }

    private void extractRFFromNetwork() {
        try {
            appeng.api.networking.storage.IStorageGrid storageGrid =
                    getProxy().getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            long want = Math.min(rfExtractPerTick, rfBufferCapacity - rfBuffer);
            if (want <= 0) return;

            IAEEnergyStack request = AEEnergyStack.create(want);
            IAEEnergyStack extracted = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class)
            ).extractItems(request, Actionable.MODULATE, getMachineSource());
            if (extracted != null) {
                rfBuffer += extracted.getStackSize();
            }
        } catch (GridAccessException e) {
            // 断网
        }
    }

    private void collectRFFromProviders() {
        for (net.minecraftforge.energy.IEnergyStorage provider : energyProviders) {
            int canExtract = provider.extractEnergy(Integer.MAX_VALUE, true);
            if (canExtract > 0) {
                int actual = provider.extractEnergy(canExtract, false);
                rfBuffer = Math.min(rfBuffer + actual, rfBufferCapacity);
            }
        }
    }

    private void distributeRFToReceivers() {
        if (energyReceivers.isEmpty() || rfBuffer <= 0) return;

        long perReceiver = rfBuffer / energyReceivers.size();
        if (perReceiver <= 0) perReceiver = rfBuffer;

        for (net.minecraftforge.energy.IEnergyStorage receiver : energyReceivers) {
            if (rfBuffer <= 0) break;
            long toSend = Math.min(perReceiver, rfBuffer);
            int accepted = receiver.receiveEnergy((int) Math.min(toSend, Integer.MAX_VALUE), false);
            rfBuffer -= accepted;
        }
    }

    private void injectRFBackToNetwork() {
        if (rfBuffer < rfBufferCapacity * 0.9) return;
        try {
            appeng.api.networking.storage.IStorageGrid storageGrid =
                    getProxy().getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            long excess = rfBuffer - (rfBufferCapacity / 2);
            if (excess <= 0) return;

            IAEEnergyStack toInject = AEEnergyStack.create(excess);
            IAEEnergyStack leftover = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class)
            ).injectItems(toInject, Actionable.MODULATE, getMachineSource());
            long injected = excess - (leftover != null ? leftover.getStackSize() : 0);
            rfBuffer -= injected;
        } catch (GridAccessException e) {
            // 断网
        }
    }

    private void syncEnergyToClients() {
        if (world == null || world.isRemote) return;
        syncCooldown--;
        if (syncCooldown > 0) return;
        syncCooldown = 20; // 每秒同步一次

        long networkStored = 0;
        try {
            appeng.api.networking.storage.IStorageGrid storageGrid =
                    getProxy().getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid != null) {
                IAEEnergyStack first = storageGrid.getInventory(
                        AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class)
                ).getStorageList().getFirstItem();
                if (first != null) networkStored = first.getStackSize();
            }
        } catch (GridAccessException e) {
            // ignore
        }

        AE2Enhanced.network.sendToAllTracking(
                new PacketPlatformEnergySync(pos, rfBuffer, rfBufferCapacity, networkStored),
                new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(
                        world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 64));
    }

    // === 设施缓存刷新 ===

    public void refreshFacilityCache() {
        energyReceivers.clear();
        energyProviders.clear();
        if (!isPlatformActive) return;

        BlockPos min = getPlatformMin();
        BlockPos max = getPlatformMax();
        int surfaceY = getPlatformSurfaceY();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int scanRange = AE2EnhancedConfig.advancedPlatform.facilityScanVerticalRange;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = surfaceY; y <= surfaceY + scanRange; y++) {
                    mutable.setPos(x, y, z);
                    TileEntity te = world.getTileEntity(mutable);
                    if (te != null && te.hasCapability(CapabilityEnergy.ENERGY, EnumFacing.UP)) {
                        net.minecraftforge.energy.IEnergyStorage cap =
                                te.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
                        if (cap != null) {
                            if (cap.canReceive()) energyReceivers.add(cap);
                            if (cap.canExtract()) energyProviders.add(cap);
                        }
                    }
                }
            }
        }
    }

    // === ForgeChunkManager ===

    public void loadPlatformChunks() {
        if (!AE2EnhancedConfig.advancedPlatform.platformChunkLoading) return;
        if (chunkTicket != null) return;
        chunkTicket = ForgeChunkManager.requestTicket(AE2Enhanced.instance, world, ForgeChunkManager.Type.NORMAL);
        if (chunkTicket != null) {
            chunkTicket.getModData().setInteger("controllerX", pos.getX());
            chunkTicket.getModData().setInteger("controllerY", pos.getY());
            chunkTicket.getModData().setInteger("controllerZ", pos.getZ());
            int radius = (platformSizeInChunks - 1) / 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    net.minecraft.util.math.ChunkPos cp =
                            new net.minecraft.util.math.ChunkPos((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz);
                    ForgeChunkManager.forceChunk(chunkTicket, cp);
                }
            }
        }
    }

    public void unloadPlatformChunks() {
        if (chunkTicket != null) {
            ForgeChunkManager.releaseTicket(chunkTicket);
            chunkTicket = null;
        }
    }

    public void rebindChunkTicket(ForgeChunkManager.Ticket ticket) {
        this.chunkTicket = ticket;
    }

    // === 平台激活/停用 ===

    public void activatePlatform(int sizeInChunks, int relativeSurfaceY) {
        this.platformSizeInChunks = sizeInChunks;
        this.relativeSurfaceY = relativeSurfaceY;
        this.isPlatformActive = true;
        updateConfigValues();
        loadPlatformChunks();
        refreshFacilityCache();
        markDirty();
    }

    public void deactivatePlatform() {
        this.isPlatformActive = false;
        unloadPlatformChunks();
        energyReceivers.clear();
        energyProviders.clear();
        markDirty();
    }

    // === NBT ===

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.platformSizeInChunks = compound.getInteger("PlatformSize");
        if (this.platformSizeInChunks <= 0) this.platformSizeInChunks = 5;
        this.relativeSurfaceY = compound.getInteger("RelativeSurfaceY");
        this.isPlatformActive = compound.getBoolean("PlatformActive");
        this.rfBuffer = compound.getLong("RFBuffer");
        this.rfBufferCapacity = compound.getLong("RFBufferCapacity");
        if (this.networkEnergyStorage != null) {
            this.networkEnergyStorage.setCapacityRF(this.rfBufferCapacity);
            this.networkEnergyStorage.addRF(compound.getLong("NetworkEnergyStored"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("PlatformSize", this.platformSizeInChunks);
        compound.setInteger("RelativeSurfaceY", this.relativeSurfaceY);
        compound.setBoolean("PlatformActive", this.isPlatformActive);
        compound.setLong("RFBuffer", this.rfBuffer);
        compound.setLong("RFBufferCapacity", this.rfBufferCapacity);
        if (this.networkEnergyStorage != null) {
            compound.setLong("NetworkEnergyStored", this.networkEnergyStorage.getStoredRF());
        }
        return compound;
    }

    // === 状态查询 ===

    public boolean isPlatformActive() {
        return isPlatformActive;
    }

    public BlockPos getPlatformMin() {
        int halfChunks = (platformSizeInChunks - 1) / 2;
        int offset = 7 + halfChunks * 16;
        int surfaceY = pos.getY() + relativeSurfaceY;
        return new BlockPos(pos.getX() - offset, surfaceY, pos.getZ() - offset);
    }

    public BlockPos getPlatformMax() {
        int halfChunks = (platformSizeInChunks - 1) / 2;
        int offset = 7 + halfChunks * 16;
        int surfaceY = pos.getY() + relativeSurfaceY;
        return new BlockPos(
                pos.getX() - offset + platformSizeInChunks * 16 - 1,
                surfaceY,
                pos.getZ() - offset + platformSizeInChunks * 16 - 1);
    }

    public int getPlatformSurfaceY() {
        return pos.getY() + relativeSurfaceY;
    }

    public int getPlatformSizeInChunks() {
        return platformSizeInChunks;
    }

    public long getRfBuffer() {
        return rfBuffer;
    }

    public long getRfBufferCapacity() {
        return rfBufferCapacity;
    }

    // === 辅助 ===

    private MachineSource getMachineSource() {
        if (this.machineSource == null) {
            this.machineSource = new MachineSource(this);
        }
        return this.machineSource;
    }
}

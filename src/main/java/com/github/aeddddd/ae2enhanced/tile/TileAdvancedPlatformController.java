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
import com.github.aeddddd.ae2enhanced.platform.EnergyFacility;
import com.github.aeddddd.ae2enhanced.platform.energy.EnergyAdapterRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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
import com.github.aeddddd.ae2enhanced.platform.subnet.Subnet;
import com.github.aeddddd.ae2enhanced.platform.zone.FaceIoConfig;
import com.github.aeddddd.ae2enhanced.platform.zone.Zone;
import com.github.aeddddd.ae2enhanced.platform.zone.ZoneRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.github.aeddddd.ae2enhanced.platform.key.ItemStackKey;

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

    // ===== 能量设施缓存 =====
    private final List<EnergyFacility> energyFacilities = new ArrayList<>();
    private int cacheRefreshCooldown = 0;

    // ===== ForgeChunkManager =====
    private ForgeChunkManager.Ticket chunkTicket = null;

    // ===== 向 ME 网络暴露的存储 =====
    private EnergyStorageAdapter networkEnergyStorage;
    private MachineSource machineSource;

    // ===== 客户端同步计数器 =====
    private int syncCooldown = 0;

    // ===== 子网与选区管理 =====
    public static final Subnet MAIN_NET_SUBNET = new Subnet(0, "MainNet");
    private final Map<Integer, Subnet> subnets = new HashMap<>();
    private final ZoneRegistry zoneRegistry = new ZoneRegistry();
    private int nextZoneId = 1;
    private int nextSubnetId = 1;
    // 主网不过滤：始终返回空集，不持久化
    private static final Set<ItemStackKey> MAIN_NET_EMPTY_FILTER = java.util.Collections.unmodifiableSet(new HashSet<>());

    // ===== IO 引擎 =====
    private com.github.aeddddd.ae2enhanced.platform.io.PlatformIoCache ioCache;
    private final com.github.aeddddd.ae2enhanced.platform.io.ZoneIoAdapterRegistry adapterRegistry = new com.github.aeddddd.ae2enhanced.platform.io.ZoneIoAdapterRegistry();
    private com.github.aeddddd.ae2enhanced.platform.io.PlatformIoScheduler ioScheduler;

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
            if (ioCache == null) {
                ioCache = new com.github.aeddddd.ae2enhanced.platform.io.PlatformIoCache(world);
                ioScheduler = new com.github.aeddddd.ae2enhanced.platform.io.PlatformIoScheduler(this, zoneRegistry, ioCache, adapterRegistry, getMachineSource());
            }
        }
        if (isPlatformActive && cacheRefreshCooldown > 0) {
            cacheRefreshCooldown--;
        }
        syncEnergyToClients();
    }

    // === IGridTickable ===

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        if (isPlatformActive && ioScheduler != null) {
            return ioScheduler.getTickingRequest(node);
        }
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
        if (ioScheduler != null) {
            return ioScheduler.tickingRequest(node, ticksSinceLastCall);
        }
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

    // === 能量 Tick 逻辑（P2：按需直供） ===

    private void doEnergyTick() {
        if (cacheRefreshCooldown <= 0) {
            refreshFacilityCache();
            cacheRefreshCooldown = 100;
        }

        // 网络正常时按需直供；断网时回退到 rfBuffer 应急
        supplyReceiversFromNetwork();

        // 网络恢复时优先清空本地缓冲回注 ME 网络
        handleBufferRecovery();

        syncEnergyToClients();
    }

    /**
     * 从 ME 网络按需提取能量，通过单 tick 多次调用策略注入接收设施。
     * 断网时自动回退到 rfBuffer 应急供能。
     */
    private void supplyReceiversFromNetwork() {
        if (energyFacilities.isEmpty()) return;

        appeng.api.networking.storage.IStorageGrid storageGrid = null;
        boolean networkAvailable = false;
        try {
            storageGrid = getProxy().getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
            networkAvailable = storageGrid != null;
        } catch (GridAccessException e) {
            // 断网
        }

        if (!networkAvailable) {
            distributeRFBufferToReceivers();
            return;
        }

        for (EnergyFacility facility : energyFacilities) {
            if (!facility.isReceiver()) continue;

            TileEntity tile = world.getTileEntity(facility.pos);
            if (tile == null || tile.isInvalid()) continue;

            IEnergyStorage cap = tile.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
            if (cap == null || !cap.canReceive()) continue;

            int demand = facility.adapter.getReceiveableEnergy(tile, cap);
            if (demand <= 0) continue;

            // 一次性提取全部需求，不受 rfExtractPerTick 限制
            IAEEnergyStack request = AEEnergyStack.create(demand);
            IAEEnergyStack extracted = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class)
            ).extractItems(request, Actionable.MODULATE, getMachineSource());

            if (extracted == null || extracted.getStackSize() <= 0) continue;

            int toInject = (int) Math.min(extracted.getStackSize(), Integer.MAX_VALUE);
            int actual = facility.adapter.injectEnergy(tile, cap, toInject, false);

            // 未用完的能量返还 ME 网络
            long leftover = extracted.getStackSize() - actual;
            if (leftover > 0) {
                IAEEnergyStack leftoverStack = AEEnergyStack.create(leftover);
                storageGrid.getInventory(
                        AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class)
                ).injectItems(leftoverStack, Actionable.MODULATE, getMachineSource());
            }
        }
    }

    /**
     * 断网应急：使用 rfBuffer 给接收设施供能。
     */
    private void distributeRFBufferToReceivers() {
        if (rfBuffer <= 0 || energyFacilities.isEmpty()) return;

        long perReceiver = rfBuffer / energyFacilities.size();
        if (perReceiver <= 0) perReceiver = rfBuffer;

        for (EnergyFacility facility : energyFacilities) {
            if (!facility.isReceiver()) continue;
            if (rfBuffer <= 0) break;

            TileEntity tile = world.getTileEntity(facility.pos);
            if (tile == null || tile.isInvalid()) continue;

            IEnergyStorage cap = tile.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
            if (cap == null || !cap.canReceive()) continue;

            long toSend = Math.min(perReceiver, rfBuffer);
            int accepted = facility.adapter.injectEnergy(tile, cap, (int) Math.min(toSend, Integer.MAX_VALUE), false);
            rfBuffer -= accepted;
        }
    }

    /**
     * 网络恢复时，将 rfBuffer 中的能量优先回注 ME 网络。
     */
    private void handleBufferRecovery() {
        if (rfBuffer <= 0) return;
        try {
            appeng.api.networking.storage.IStorageGrid storageGrid =
                    getProxy().getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;

            long toRecover = Math.min(rfBuffer, rfExtractPerTick);
            if (toRecover <= 0) return;

            IAEEnergyStack toInject = AEEnergyStack.create(toRecover);
            IAEEnergyStack leftover = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class)
            ).injectItems(toInject, Actionable.MODULATE, getMachineSource());

            long injected = toRecover - (leftover != null ? leftover.getStackSize() : 0);
            rfBuffer -= injected;
        } catch (GridAccessException e) {
            // 仍然断网，保持缓冲
        }
    }

    public void syncEnergyToClients() {
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
        energyFacilities.clear();
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
                        IEnergyStorage cap = te.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
                        if (cap != null && cap.canReceive()) {
                            String blockId = world.getBlockState(mutable).getBlock().getRegistryName().toString();
                            energyFacilities.add(new EnergyFacility(
                                    mutable.toImmutable(), cap, blockId,
                                    EnergyAdapterRegistry.findAdapter(blockId),
                                    EnergyFacility.Type.RECEIVER));
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
        energyFacilities.clear();
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
            this.networkEnergyStorage.setStoredRF(compound.getLong("NetworkEnergyStored"));
        }

        this.nextZoneId = compound.getInteger("NextZoneId");
        if (this.nextZoneId <= 0) this.nextZoneId = 1;
        this.nextSubnetId = compound.getInteger("NextSubnetId");
        if (this.nextSubnetId <= 0) this.nextSubnetId = 1;

        this.zoneRegistry.clear();
        if (compound.hasKey("Zones")) {
            this.zoneRegistry.readFromNBT(compound.getCompoundTag("Zones"));
        }

        this.subnets.clear();
        if (compound.hasKey("Subnets")) {
            NBTTagList subnetList = compound.getTagList("Subnets", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < subnetList.tagCount(); i++) {
                Subnet subnet = new Subnet();
                subnet.readFromNBT(subnetList.getCompoundTagAt(i));
                this.subnets.put(subnet.getId(), subnet);
            }
        }

        // 主网不过滤：不读取旧数据
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

        compound.setTag("Zones", this.zoneRegistry.writeToNBT());
        compound.setInteger("NextZoneId", this.nextZoneId);
        compound.setInteger("NextSubnetId", this.nextSubnetId);
        NBTTagList subnetList = new NBTTagList();
        for (Subnet subnet : this.subnets.values()) {
            subnetList.appendTag(subnet.writeToNBT());
        }
        compound.setTag("Subnets", subnetList);

        // 主网不过滤：不写入数据
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

    public MachineSource getMachineSource() {
        if (this.machineSource == null) {
            this.machineSource = new MachineSource(this);
        }
        return this.machineSource;
    }

    // ===== 子网管理 =====

    public boolean hasHyperdimensionalController() {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return false;
            for (appeng.api.networking.IGridNode node : grid.getNodes()) {
                if (node != null && node.getMachine() instanceof TileHyperdimensionalController) {
                    return true;
                }
            }
        } catch (GridAccessException e) {
            return false;
        }
        return false;
    }

    public Subnet createSubnet(String name) {
        if (!hasHyperdimensionalController()) return null;
        int id = nextSubnetId++;
        Subnet subnet = new Subnet(id, name);
        subnets.put(id, subnet);
        markDirty();
        return subnet;
    }

    public boolean deleteSubnet(int id) {
        if (!subnets.containsKey(id)) return false;
        for (Zone zone : zoneRegistry.getAllZones()) {
            if (zone.getSubnetId() == id) {
                zone.setSubnetId(0);
                zone.setInputTarget(null);
                zone.setOutputTarget(null);
                zoneRegistry.reclassifyZone(zone);
            }
        }
        subnets.remove(id);
        markDirty();
        return true;
    }

    public void renameSubnet(int id, String name) {
        Subnet subnet = subnets.get(id);
        if (subnet != null) {
            subnet.setName(name);
            markDirty();
        }
    }

    public Collection<Subnet> getSubnets() {
        return subnets.values();
    }

    @Nullable
    public Subnet getSubnet(int id) {
        return subnets.get(id);
    }

    public Set<ItemStackKey> getMainNetAllowFrom() {
        return MAIN_NET_EMPTY_FILTER;
    }

    public Set<ItemStackKey> getMainNetAllowTo() {
        return MAIN_NET_EMPTY_FILTER;
    }

    public ZoneRegistry getZoneRegistry() {
        return zoneRegistry;
    }

    public Zone createZone(String name, java.util.Set<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return null;
        int id = nextZoneId++;
        BlockPos min = null;
        BlockPos max = null;
        for (BlockPos p : positions) {
            if (min == null) {
                min = p;
                max = p;
            } else {
                min = new BlockPos(
                        Math.min(min.getX(), p.getX()),
                        Math.min(min.getY(), p.getY()),
                        Math.min(min.getZ(), p.getZ()));
                max = new BlockPos(
                        Math.max(max.getX(), p.getX()),
                        Math.max(max.getY(), p.getY()),
                        Math.max(max.getZ(), p.getZ()));
            }
        }
        Zone zone = new Zone(id, min, max);
        zone.setName(name != null ? name : "");
        for (BlockPos p : positions) {
            zone.getPositions().add(p);
        }
        this.zoneRegistry.addZone(zone);
        markDirty();
        return zone;
    }

    public boolean deleteZone(int id) {
        Zone zone = this.zoneRegistry.removeZone(id);
        if (zone != null) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean assignZoneToSubnet(int zoneId, int subnetId) {
        Zone zone = zoneRegistry.getZone(zoneId);
        if (zone == null) return false;
        if (subnetId == 0) {
            zone.setSubnetId(0);
            zone.setInputTarget(MAIN_NET_SUBNET);
            zone.setOutputTarget(MAIN_NET_SUBNET);
            zoneRegistry.reclassifyZone(zone);
            markDirty();
            return true;
        }
        Subnet subnet = subnets.get(subnetId);
        if (subnet == null) return false;
        zone.setSubnetId(subnetId);
        zone.setInputTarget(subnet);
        zone.setOutputTarget(subnet);
        zoneRegistry.reclassifyZone(zone);
        markDirty();
        return true;
    }

    public void setZoneFaceIoConfig(int zoneId, EnumFacing face, FaceIoConfig config) {
        Zone zone = zoneRegistry.getZone(zoneId);
        if (zone != null && config != null && face != null) {
            FaceIoConfig target = zone.getFaceIo().get(face);
            if (target != null) {
                target.setMode(config.getMode());
                if (!config.getChannels().isEmpty()) {
                    target.getChannels().clear();
                    target.getChannels().addAll(config.getChannels());
                }
                if (!config.getFilter().isEmpty()) {
                    target.getFilter().clear();
                    target.getFilter().addAll(config.getFilter());
                }
                zoneRegistry.reclassifyZone(zone);
                markDirty();
            }
        }
    }

    public Map<Integer, Long> getSubnetStorageUsage() {
        Map<Integer, Long> usage = new HashMap<>();
        for (Subnet subnet : subnets.values()) {
            if (subnet.getStorage() != null) {
                usage.put(subnet.getId(), subnet.getStorage().getStoredItemCount());
            } else {
                usage.put(subnet.getId(), 0L);
            }
        }
        return usage;
    }

    public void sendPlatformInitToPlayer(net.minecraft.entity.player.EntityPlayerMP player) {
        java.util.List<com.github.aeddddd.ae2enhanced.network.packet.platform.PacketPlatformInit.SubnetData> subnetList = new java.util.ArrayList<>();
        for (Subnet subnet : subnets.values()) {
            subnetList.add(new com.github.aeddddd.ae2enhanced.network.packet.platform.PacketPlatformInit.SubnetData(subnet.getId(), subnet.getName()));
        }
        java.util.List<com.github.aeddddd.ae2enhanced.network.packet.platform.PacketPlatformInit.ZoneSummary> zoneList = new java.util.ArrayList<>();
        for (Zone zone : zoneRegistry.getAllZones()) {
            int blockCount = zone.getPositions().getAllPositions().size();
            int[] faceModes = new int[6];
            for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.values()) {
                FaceIoConfig config = zone.getFaceIo().get(facing);
                faceModes[facing.ordinal()] = config != null ? config.getMode().ordinal() : 0;
            }
            zoneList.add(new com.github.aeddddd.ae2enhanced.network.packet.platform.PacketPlatformInit.ZoneSummary(
                    zone.getId(), zone.getName(), zone.getSubnetId(), blockCount, faceModes));
        }
        AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.platform.PacketPlatformInit(pos, subnetList, zoneList), player);
    }

    public void sendPlatformInitToAllViewingPlayers() {
        if (world == null || world.isRemote) return;
        for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities) {
            if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                net.minecraft.entity.player.EntityPlayerMP mp = (net.minecraft.entity.player.EntityPlayerMP) player;
                if (mp.openContainer instanceof com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformController) {
                    com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformController c =
                            (com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformController) mp.openContainer;
                    if (c.getTile() == this) {
                        sendPlatformInitToPlayer(mp);
                    }
                } else if (mp.openContainer instanceof com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformSubmenu) {
                    com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformSubmenu c =
                            (com.github.aeddddd.ae2enhanced.container.platform.ContainerAdvancedPlatformSubmenu) mp.openContainer;
                    if (c.getTile() == this) {
                        sendPlatformInitToPlayer(mp);
                    }
                }
            }
        }
    }
}

package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.collector.CollectorRegistry;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import appeng.parts.automation.StackUpgradeInventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * 先进 ME 收集器的 TileEntity.
 *
 * <p>接入 AE 网络,维护一个 63 槽的过滤库存(7×9)和 5 个升级槽(容量卡扩展过滤行).
 * 在物品实体生成前,通过事件/Mixin 拦截并直接注入网络.</p>
 */
public class TileAdvancedMECollector extends TileAENetworkBase
        implements ITickable, IAEAppEngInventory, appeng.api.implementations.IUpgradeableHost,
        appeng.api.networking.security.IActionHost, appeng.util.IConfigManagerHost {

    public static final int CONFIG_SIZE = 63;
    public static final int UPGRADE_SLOTS = 5;

    private final AppEngInternalAEInventory config;
    private StackUpgradeInventory upgrades;

    private int range = AE2EnhancedConfig.collector.defaultRange;
    private int tickCounter = 0;
    private int clientFlags = 0;
    private boolean lastPowered = false;
    private boolean lastActive = false;

    public TileAdvancedMECollector() {
        this.config = new AppEngInternalAEInventory(this, CONFIG_SIZE);
    }

    // ---- TileAENetworkBase ----

    @Override
    protected String getProxyName() {
        return "advanced_me_collector";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.ADVANCED_ME_COLLECTOR);
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

    // ---- 生命周期 ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote) {
            CollectorRegistry.register(this);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        CollectorRegistry.unregister(this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        CollectorRegistry.unregister(this);
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (needsReady()) {
            clearNeedsReady();
            getProxy().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
            getProxy().setIdlePowerUsage(AE2EnhancedConfig.collector.idlePower);
            getProxy().onReady();
        }

        syncClientState();

        // 每 5 tick 兜底扫描一次范围内已存在的物品实体
        if (++tickCounter >= 5) {
            tickCounter = 0;
            collectExistingItems();
        }
    }

    private void collectExistingItems() {
        if (!isActive()) return;
        AxisAlignedBB area = getCollectionArea();
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, area);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            tryCollect(item);
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

    // ---- 状态访问 ----

    public boolean isPowered() {
        if (world != null && world.isRemote) {
            return (this.clientFlags & 1) == 1;
        }
        try {
            return getProxy().getEnergy().isNetworkPowered();
        } catch (GridAccessException e) {
            return false;
        }
    }

    public boolean isActive() {
        if (world != null && world.isRemote) {
            return isPowered() && (this.clientFlags & 2) == 2;
        }
        try {
            return getProxy().isActive();
        } catch (Exception e) {
            return false;
        }
    }

    public int getRange() {
        return this.range;
    }

    public void setRange(int range) {
        int max = AE2EnhancedConfig.collector.maxRange;
        int newRange = Math.max(0, Math.min(range, max));
        if (newRange != this.range) {
            this.range = newRange;
            markDirty();
            if (world != null && !world.isRemote) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 2);
            }
        }
    }

    public int getActualSideLength() {
        return this.range * 2 + 1;
    }

    public AxisAlignedBB getCollectionArea() {
        int r = this.range;
        return new AxisAlignedBB(
                pos.getX() - r, pos.getY() - r, pos.getZ() - r,
                pos.getX() + r + 1, pos.getY() + r + 1, pos.getZ() + r + 1
        );
    }

    // ---- 过滤库存与升级槽 ----

    public AppEngInternalAEInventory getConfig() {
        return this.config;
    }

    private StackUpgradeInventory getUpgrades() {
        if (this.upgrades == null) {
            this.upgrades = new StackUpgradeInventory(getProxy().getMachineRepresentation(), this, UPGRADE_SLOTS);
        }
        return this.upgrades;
    }

    @Override
    public int getInstalledUpgrades(Upgrades u) {
        return getUpgrades().getInstalledUpgrades(u);
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("config".equals(name)) {
            return this.config;
        }
        if ("upgrades".equals(name)) {
            return getUpgrades();
        }
        return null;
    }

    @Override
    public net.minecraft.tileentity.TileEntity getTile() {
        return this;
    }

    public net.minecraft.tileentity.TileEntity getTileEntity() {
        return this;
    }

    private appeng.util.ConfigManager configManager;

    @Override
    public appeng.api.util.IConfigManager getConfigManager() {
        if (this.configManager == null) {
            this.configManager = new appeng.util.ConfigManager(this);
        }
        return this.configManager;
    }

    @Override
    public void updateSetting(appeng.api.util.IConfigManager manager, Enum settingName, Enum newValue) {
        this.markDirty();
    }

    @Override
    public appeng.api.networking.IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public void saveChanges() {
        this.markDirty();
    }

    // ---- 收集逻辑 ----

    /**
     * 尝试收集单个物品实体.成功时销毁实体并返回 true.
     */
    public boolean tryCollect(EntityItem entityItem) {
        if (world == null || world.isRemote) return false;
        if (!isActive()) return false;

        ItemStack stack = entityItem.getItem();
        if (stack.isEmpty()) return false;

        if (!matchesFilter(stack)) return false;

        try {
            IItemStorageChannel channel = appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            IAEItemStack toInsert = channel.createStack(stack);
            if (toInsert == null) return false;

            IAEItemStack remaining = Platform.poweredInsert(
                    getProxy().getEnergy(),
                    getProxy().getStorage().getInventory(channel),
                    toInsert,
                    new MachineSource(this)
            );

            if (remaining != null && remaining.getStackSize() > 0) {
                ItemStack leftover = remaining.createItemStack();
                if (leftover.getCount() < stack.getCount()) {
                    entityItem.setItem(leftover);
                    return false; // 部分收集,不取消实体
                }
                return false; // 完全无法收集
            }

            entityItem.setDead();
            return true;
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Advanced ME Collector failed to inject item", e);
            return false;
        }
    }

    /**
     * 尝试收集一个 ItemStack(非实体场景).返回剩余未收集的 stack.
     */
    @Nullable
    public ItemStack tryCollectStack(ItemStack stack) {
        if (world == null || world.isRemote) return stack;
        if (!isActive()) return stack;
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!matchesFilter(stack)) return stack;

        try {
            IItemStorageChannel channel = appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            IAEItemStack toInsert = channel.createStack(stack);
            if (toInsert == null) return stack;

            IAEItemStack remaining = Platform.poweredInsert(
                    getProxy().getEnergy(),
                    getProxy().getStorage().getInventory(channel),
                    toInsert,
                    new MachineSource(this)
            );

            if (remaining != null && remaining.getStackSize() > 0) {
                return remaining.createItemStack();
            }
            return ItemStack.EMPTY;
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Advanced ME Collector failed to inject stack", e);
            return stack;
        }
    }

    /**
     * 判断物品是否匹配过滤槽.过滤槽为空时收集所有物品.
     */
    public boolean matchesFilter(ItemStack stack) {
        if (stack.isEmpty()) return false;
        int activeSlots = getAvailableFilterSlots();
        boolean hasAnyFilter = false;
        for (int i = 0; i < activeSlots; i++) {
            IAEItemStack filter = this.config.getAEStackInSlot(i);
            if (filter == null) continue;
            hasAnyFilter = true;
            if (isFilterMatch(filter, stack)) {
                return true;
            }
        }
        return !hasAnyFilter;
    }

    private boolean isFilterMatch(IAEItemStack filter, ItemStack stack) {
        if (filter == null) return false;
        ItemStack filterStack = filter.createItemStack();
        if (filterStack.isEmpty()) return false;

        // 基础物品匹配
        if (filterStack.getItem() != stack.getItem()) return false;

        // 耐久/meta 匹配(若过滤物品有耐久)
        if (filterStack.getHasSubtypes() && filterStack.getMetadata() != stack.getMetadata()) {
            return false;
        }

        // NBT 匹配:过滤物品有 NBT 时要求一致
        if (filterStack.hasTagCompound()) {
            if (!filterStack.getTagCompound().equals(stack.getTagCompound())) {
                return false;
            }
        }

        return true;
    }

    public int getAvailableFilterSlots() {
        int capacityUpgrades = getInstalledUpgrades(Upgrades.CAPACITY);
        return Math.min(18 + capacityUpgrades * 9, CONFIG_SIZE);
    }

    // ---- 物品栏变化回调 ----

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        this.markDirty();
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.range = compound.getInteger("range");
        this.config.readFromNBT(compound, "config");
        getUpgrades().readFromNBT(compound, "upgrades");
        this.clientFlags = compound.getInteger("clientFlags");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("range", this.range);
        this.config.writeToNBT(compound, "config");
        getUpgrades().writeToNBT(compound, "upgrades");
        compound.setInteger("clientFlags", this.clientFlags);
        return compound;
    }

    // ---- 客户端同步 ----

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("range", this.range);
        tag.setInteger("clientFlags", this.clientFlags);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        this.range = tag.getInteger("range");
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

    // ---- 掉落物 ----

    public void dropContents() {
        if (world == null || world.isRemote) return;
        dropInventory(this.config, world, pos);
        dropInventory(getUpgrades(), world, pos);
    }

    private static void dropInventory(IItemHandler inv, World world, BlockPos pos) {
        if (inv == null) return;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, java.util.Collections.singletonList(stack));
            }
        }
    }
}

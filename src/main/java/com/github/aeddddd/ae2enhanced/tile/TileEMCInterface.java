package com.github.aeddddd.ae2enhanced.tile;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.IStorageProvider;
import ae2.api.util.AECableType;
import ae2.api.util.AEPartLocation;
import ae2.api.util.DimensionalBlockPos;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.integration.projecte.EMCInventoryHandler;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * EMC 接口 TileEntity.
 *
 * <p>将绑定玩家的 ProjectE EMC 余额作为 AE 网络物品源.单向输出,不接收物品.</p>
 */
public class TileEMCInterface extends TileAENetworkBase implements ITickable, InternalInventoryHost, IActionHost {

    public static final int WHITELIST_PAGES = 20;
    public static final int WHITELIST_SLOTS_PER_PAGE = 102; // 17×6，与 3.png 顶部网格一致
    public static final int WHITELIST_SIZE = WHITELIST_PAGES * WHITELIST_SLOTS_PER_PAGE; // 2040
    private static final int WARNING_INTERVAL = 1200; // 60 秒警告冷却

    private final EMCInventoryHandler handler = new EMCInventoryHandler(this);
    private final AppEngInternalInventory config;
    private final ItemStack[] whitelist = new ItemStack[WHITELIST_SIZE];
    private final Set<AEItemKey> whitelistSet = new HashSet<>();

    @Nullable
    private UUID ownerUUID;
    private String ownerName = "";

    private boolean registeredEvents = false;

    public void invalidateHandlerCache() {
        handler.invalidateAvailableCache();
    }

    public void invalidateEmcCache() {
        handler.invalidateEmcCache();
    }
    private int tickCounter = 0;
    private long lastWarningTick = -WARNING_INTERVAL;

    public TileEMCInterface() {
        this.config = new AppEngInternalInventory(this, WHITELIST_SIZE);
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            whitelist[i] = ItemStack.EMPTY;
        }
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(AE2EnhancedConfig.emcInterface.idlePower)
                .setVisualRepresentation(AEItemKey.of(BlockRegistry.EMC_INTERFACE))
                .addService(IStorageProvider.class, handler);
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalBlockPos getLocation() {
        return new DimensionalBlockPos(this);
    }

    @Override
    public IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public void securityBreak() {
        // 安全破坏时不掉落,仅解绑
        ownerUUID = null;
        ownerName = "";
        handler.invalidateAvailableCache();
        markDirty();
    }

    // ---- 玩家绑定 ----

    public boolean isBound() {
        return ownerUUID != null && ProjectEHelper.isAvailable();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(@Nullable EntityPlayer player) {
        if (player == null) {
            this.ownerUUID = null;
            this.ownerName = "";
        } else {
            this.ownerUUID = player.getUniqueID();
            this.ownerName = player.getName();
        }
        handler.invalidateAvailableCache();
        markDirty();
        notifyStorageUpdate();
    }

    @Nullable
    public Object getKnowledgeProvider() {
        if (ownerUUID == null) return null;
        return ProjectEHelper.getKnowledgeProvider(ownerUUID);
    }

    // ---- 白名单 ----

    public AppEngInternalInventory getConfig() {
        return config;
    }

    public Set<AEItemKey> getWhitelistKeys() {
        return Collections.unmodifiableSet(new HashSet<>(whitelistSet));
    }

    public ItemStack getWhitelistSlot(int index) {
        return whitelist[index].copy();
    }

    public void setWhitelistSlot(int index, @Nonnull ItemStack stack) {
        whitelist[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        whitelist[index].setCount(1);
        config.setItemDirect(index, whitelist[index]);
        rebuildWhitelistSet();
        handler.invalidateAvailableCache();
        markDirty();
        notifyStorageUpdate();
    }

    public ItemStack[] getWhitelist() {
        return whitelist;
    }

    public boolean isWhitelisted(@Nonnull ItemStack stack) {
        if (whitelistSet.isEmpty()) return false; // 空白名单 = 不暴露任何物品
        AEItemKey key = AEItemKey.of(stack);
        return key != null && whitelistSet.contains(key);
    }

    public boolean isWhitelistActive() {
        return !whitelistSet.isEmpty();
    }

    private void rebuildWhitelistSet() {
        whitelistSet.clear();
        for (ItemStack stack : whitelist) {
            if (!stack.isEmpty()) {
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) whitelistSet.add(key);
            }
        }
    }

    // ---- 生命周期 ----

    @Override
    public void validate() {
        super.validate();
        if (world != null && !world.isRemote) {
            registerProjectEEvents();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        unregisterProjectEEvents();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        unregisterProjectEEvents();
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        tickCounter++;
        if (tickCounter % 40 == 0) {
            checkDuplicateInterfaces();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasUniqueId("OwnerUUID")) {
            ownerUUID = compound.getUniqueId("OwnerUUID");
        } else {
            ownerUUID = null;
        }
        ownerName = compound.getString("OwnerName");

        NBTTagList list = compound.getTagList("Whitelist", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            whitelist[i] = ItemStack.EMPTY;
        }
        for (int i = 0; i < list.tagCount() && i < WHITELIST_SIZE; i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getShort("Slot") & 0xFFFF;
            if (slot < WHITELIST_SIZE) {
                whitelist[slot] = new ItemStack(tag);
            }
        }
        rebuildWhitelistSet();
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            config.setItemDirect(i, whitelist[i]);
        }
        handler.invalidateAvailableCache();
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (ownerUUID != null) {
            compound.setUniqueId("OwnerUUID", ownerUUID);
        }
        compound.setString("OwnerName", ownerName);

        NBTTagList list = new NBTTagList();
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            if (!whitelist[i].isEmpty()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setShort("Slot", (short) i);
                whitelist[i].writeToNBT(tag);
                list.appendTag(tag);
            }
        }
        compound.setTag("Whitelist", list);
        return compound;
    }

    // ---- 客户端同步 ----

    @Override
    @Nonnull
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        if (ownerUUID != null) {
            tag.setUniqueId("OwnerUUID", ownerUUID);
        }
        tag.setString("OwnerName", ownerName);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        NBTTagCompound tag = pkt.getNbtCompound();
        if (tag.hasUniqueId("OwnerUUID")) {
            ownerUUID = tag.getUniqueId("OwnerUUID");
        } else {
            ownerUUID = null;
        }
        ownerName = tag.getString("OwnerName");
    }

    // ---- 内部辅助 ----

    private void notifyStorageUpdate() {
        IStorageProvider.requestUpdate(getMainNode());
    }

    private void registerProjectEEvents() {
        if (registeredEvents || !ProjectEHelper.isAvailable()) return;
        registeredEvents = true;
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEEventHandler");
            clazz.getMethod("registerTile", TileEMCInterface.class).invoke(null, this);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to register ProjectE event listeners", e);
        }
    }

    private void unregisterProjectEEvents() {
        if (!registeredEvents) return;
        registeredEvents = false;
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEEventHandler");
            clazz.getMethod("unregisterTile", TileEMCInterface.class).invoke(null, this);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to unregister ProjectE event tile", e);
        }
    }

    private void checkDuplicateInterfaces() {
        if (!isBound()) return;
        long now = world.getTotalWorldTime();
        if (now - lastWarningTick < WARNING_INTERVAL) return;
        IGrid grid = getMainNode().getGrid();
        if (grid == null) return;
        Set<TileEMCInterface> duplicates = new HashSet<>();
        for (IGridNode node : grid.getNodes()) {
            Object host = node.getOwner();
            if (host instanceof TileEMCInterface && host != this) {
                TileEMCInterface other = (TileEMCInterface) host;
                if (other.isBound() && ownerUUID.equals(other.ownerUUID)) {
                    duplicates.add(other);
                }
            }
        }
        if (!duplicates.isEmpty()) {
            lastWarningTick = now;
            for (EntityPlayer player : world.playerEntities) {
                if (player.getUniqueID().equals(ownerUUID)) {
                    player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.emc_interface.duplicate", ownerName));
                }
            }
        }
    }

    @Override
    public void disassemble() {
        // EMC 接口无结构,解绑即可
        ownerUUID = null;
        ownerName = "";
        handler.invalidateAvailableCache();
        markDirty();
    }

    // ---- InternalInventoryHost ----

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        markDirty();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == config && slot >= 0 && slot < WHITELIST_SIZE) {
            ItemStack added = config.getStackInSlot(slot);
            whitelist[slot] = added.isEmpty() ? ItemStack.EMPTY : added.copy();
            whitelist[slot].setCount(1);
            config.setItemDirect(slot, whitelist[slot]);
            rebuildWhitelistSet();
            handler.invalidateAvailableCache();
            markDirty();
            notifyStorageUpdate();
        }
    }

    @Override
    public boolean isClientSide() {
        return world != null && world.isRemote;
    }
}

package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.util.inv.IInventoryDestination;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.centralinterface.DualityCentralInterface;
import com.github.aeddddd.ae2enhanced.centralinterface.ICentralInterfaceHost;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 中枢 ME 接口的 TileEntity。
 *
 * 委托所有核心逻辑给 {@link DualityCentralInterface}，
 * 自身仅承担生命周期管理与 AE2 接口适配。
 */
public class TileCentralMEInterface extends TileAENetworkBase
    implements ICentralInterfaceHost, IGridTickable, ICraftingProvider,
               IAEAppEngInventory, IInventoryDestination, IConfigManagerHost,
               appeng.api.implementations.IUpgradeableHost, ITickable {

    private DualityCentralInterface duality;
    private StackUpgradeInventory upgrades;

    private static final Set<TileCentralMEInterface> ACTIVE_INTERFACES = Collections.newSetFromMap(new WeakHashMap<>());

    public TileCentralMEInterface() {
    }

    public static Set<TileCentralMEInterface> getActiveInterfaces() {
        return ACTIVE_INTERFACES;
    }

    // ---- ICentralInterfaceHost ----

    @Override
    public DualityCentralInterface getInterfaceDuality() {
        if (this.duality == null) {
            this.duality = new DualityCentralInterface(this);
        }
        return this.duality;
    }

    @Override
    public List<TargetBinding> getTargets() {
        return getInterfaceDuality().getBindings();
    }

    @Override
    public void saveChanges() {
        this.markDirty();
    }

    // ---- TileAENetworkBase ----

    @Override
    protected String getProxyName() {
        return "central_me_interface";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(ModBlocks.CENTRAL_ME_INTERFACE);
    }

    @Override
    public void disassemble() {
        if (world != null && !world.isRemote) {
            getInterfaceDuality().clearContents();
        }
    }

    @Override
    public void securityBreak() {
        if (world != null && !world.isRemote) {
            getInterfaceDuality().clearContents();
            world.destroyBlock(pos, true);
        }
    }

    @Override
    public appeng.api.networking.IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    // ---- IGridTickable ----

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return getInterfaceDuality().getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return getInterfaceDuality().tickingRequest(node, ticksSinceLastCall);
    }

    // ---- ICraftingProvider ----

    @Override
    public void provideCrafting(appeng.api.networking.crafting.ICraftingProviderHelper craftingTracker) {
        getInterfaceDuality().provideCrafting(craftingTracker);
    }

    @Override
    public boolean pushPattern(appeng.api.networking.crafting.ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        return getInterfaceDuality().pushPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        return getInterfaceDuality().isBusy();
    }

    // ---- IAEAppEngInventory ----

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, appeng.util.inv.InvOperation mc, ItemStack removed, ItemStack added) {
        getInterfaceDuality().onChangeInventory(inv, slot, mc, removed, added);
    }

    // ---- IInventoryDestination ----

    @Override
    public boolean canInsert(ItemStack stack) {
        return getInterfaceDuality().canInsert(stack);
    }

    // ---- IConfigManagerHost ----

    @Override
    public IConfigManager getConfigManager() {
        return getInterfaceDuality().getConfigManager();
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        this.markDirty();
    }

    // ---- IUpgradeableHost (via IConfigManagerHost + extra methods) ----

    public int getInstalledUpgrades(Upgrades u) {
        return getUpgrades().getInstalledUpgrades(u);
    }

    public IItemHandler getInventoryByName(String name) {
        if ("upgrades".equals(name)) {
            return getUpgrades();
        }
        return getInterfaceDuality().getInventoryByName(name);
    }

    @Override
    public net.minecraft.tileentity.TileEntity getTile() {
        return this;
    }

    private StackUpgradeInventory getUpgrades() {
        if (this.upgrades == null) {
            this.upgrades = new StackUpgradeInventory(
                    getProxy().getMachineRepresentation(), this, 4);
        }
        return this.upgrades;
    }

    // ---- ITickable (Minecraft native tick) ----

    @Override
    public appeng.api.util.AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return appeng.api.util.AECableType.SMART;
    }

    @Override
    public net.minecraft.tileentity.TileEntity getTileEntity() {
        return this;
    }

    @Override
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @javax.annotation.Nullable EnumFacing facing) {
        T result = getInterfaceDuality().getCapability(capability, facing);
        if (result != null) {
            return result;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void validate() {
        super.validate();
        ACTIVE_INTERFACES.add(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        ACTIVE_INTERFACES.remove(this);
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (needsReady()) {
            clearNeedsReady();
            getProxy().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
            getProxy().onReady();
            getInterfaceDuality().initialize();
        }
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        getInterfaceDuality().readFromNBT(compound);
        if (compound.hasKey("upgrades")) {
            getUpgrades().readFromNBT(compound.getCompoundTag("upgrades"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        getInterfaceDuality().writeToNBT(compound);
        NBTTagCompound upgradesTag = new NBTTagCompound();
        getUpgrades().writeToNBT(upgradesTag, "upgrades");
        compound.setTag("upgrades", upgradesTag);
        return compound;
    }

    // ---- 绑定管理 ----

    public void addBinding(TargetBinding binding) {
        getInterfaceDuality().addBinding(binding);
        this.markDirty();
        syncToClient();
    }

    public void removeBinding(TargetBinding binding) {
        getInterfaceDuality().removeBinding(binding);
        this.markDirty();
        syncToClient();
    }

    public void clearBindings() {
        getInterfaceDuality().clearBindings();
        this.markDirty();
        syncToClient();
    }

    private void syncToClient() {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    public String getBoundBlockId() {
        return getInterfaceDuality().getBoundBlockId();
    }

    // ---- 客户端同步 ----

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        NBTTagCompound dualityTag = new NBTTagCompound();
        getInterfaceDuality().writeToNBT(dualityTag);
        tag.setTag("duality", dualityTag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        if (tag.hasKey("duality")) {
            NBTTagCompound dualityTag = tag.getCompoundTag("duality");
            getInterfaceDuality().readFromNBT(dualityTag);
        }
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, -1, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }
}

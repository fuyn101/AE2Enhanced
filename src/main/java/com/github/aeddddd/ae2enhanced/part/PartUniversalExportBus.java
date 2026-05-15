package com.github.aeddddd.ae2enhanced.part;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.core.settings.TickRates;
import appeng.fluids.util.AEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import com.google.common.collect.ImmutableList;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.util.FakeFluids;
import com.github.aeddddd.ae2enhanced.util.FakeGases;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * E1b：通用输出总线。
 * 同时支持物品、流体、气体（Mekanism）、源质（Thaumcraft）的输出。
 */
public class PartUniversalExportBus extends PartUpgradeable implements IGridTickable {

    @PartModels
    public static final ResourceLocation[] MODELS = new ResourceLocation[]{
            new ResourceLocation(AE2Enhanced.MOD_ID, "part/universal_export_bus_base"),
            new ResourceLocation("appliedenergistics2", "part/export_bus_off"),
            new ResourceLocation("appliedenergistics2", "part/export_bus_on"),
            new ResourceLocation("appliedenergistics2", "part/export_bus_has_channel")
    };

    public static final IPartModel MODELS_OFF = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[1]});
    public static final IPartModel MODELS_ON = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[2]});
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[3]});

    public enum BusMode {
        SEQUENTIAL,
        ROUND_ROBIN,
        RANDOM
    }

    private static final Random RAND = new Random();

    private BusMode busMode = BusMode.SEQUENTIAL;
    private int roundRobinIndex = 0;

    private final AppEngInternalAEInventory config;
    private final IActionSource source;

    // Capability cache
    private boolean hasItemCap = false;
    private boolean hasFluidCap = false;
    private boolean hasGasCap = false;
    private boolean hasEssentiaCap = false;

    public PartUniversalExportBus(ItemStack is) {
        super(is);
        this.config = new AppEngInternalAEInventory(this, 63);
        this.source = new MachineSource(this);
        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public TickingRequest getTickingRequest(@Nonnull IGridNode node) {
        return new TickingRequest(TickRates.ExportBus.getMin(), TickRates.ExportBus.getMax(), this.isSleeping(), false);
    }

    protected boolean canDoBusWork() {
        TileEntity self = this.getHost().getTile();
        BlockPos targetPos = self.getPos().offset(this.getSide().getFacing());
        net.minecraft.world.World world = self.getWorld();
        return world != null && world.getChunkProvider().getLoadedChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4) != null;
    }

    @Override
    public void upgradesChanged() {
        this.updateState();
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        this.updateState();
    }

    private void updateState() {
        try {
            if (!this.isSleeping()) {
                this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
            } else {
                this.getProxy().getTick().sleepDevice(this.getProxy().getNode());
            }
        } catch (GridAccessException ignored) {
        }
    }

    @Override
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLastCall) {
        return this.doBusWork();
    }

    protected TickRateModulation doBusWork() {
        if (!this.getProxy().isActive() || !this.canDoBusWork()) {
            return TickRateModulation.IDLE;
        }
        if (this.isSleeping()) {
            return TickRateModulation.IDLE;
        }

        TileEntity self = this.getHost().getTile();
        TileEntity target = self.getWorld().getTileEntity(self.getPos().offset(this.getSide().getFacing()));
        if (target == null) {
            return TickRateModulation.SLOWER;
        }

        EnumFacing opposite = this.getSide().getFacing().getOpposite();

        this.hasItemCap = target.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, opposite);
        this.hasFluidCap = target.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        this.hasGasCap = Loader.isModLoaded("mekanism") && Loader.isModLoaded("mekeng")
                && target.hasCapability(getGasCapability(), opposite);
        this.hasEssentiaCap = false;
        if (Loader.isModLoaded("thaumcraft") && Loader.isModLoaded("thaumicenergistics")) {
            try {
                Class<?> ieTransportClass = Class.forName("thaumcraft.api.aspects.IEssentiaTransport");
                this.hasEssentiaCap = ieTransportClass.isInstance(target);
            } catch (ClassNotFoundException e) {
                this.hasEssentiaCap = false;
            }
        }

        if (!this.hasItemCap && !this.hasFluidCap && !this.hasGasCap && !this.hasEssentiaCap) {
            return TickRateModulation.SLOWER;
        }

        List<Integer> activeSlots = new ArrayList<>();
        int maxSlot = this.availableSlots();
        for (int i = 0; i < maxSlot; i++) {
            if (this.config.getAEStackInSlot(i) != null) {
                activeSlots.add(i);
            }
        }

        boolean worked = false;

        try {
            if (activeSlots.isEmpty()) {
                if (this.hasItemCap) worked |= this.exportItemsUnfiltered(target, opposite);
                if (this.hasFluidCap) worked |= this.exportFluidsUnfiltered(target, opposite);
                if (this.hasGasCap) worked |= this.exportGasesUnfiltered(target, opposite);
                if (this.hasEssentiaCap) worked |= this.exportEssentiasUnfiltered(target, opposite);
            } else {
                List<Integer> order = this.getSlotOrder(activeSlots);
                for (int slot : order) {
                    IAEItemStack filter = this.config.getAEStackInSlot(slot);
                    if (filter == null) continue;

                    ResourceType type = this.getSlotType(filter);
                    if (type == null) continue;

                    switch (type) {
                        case ITEM:
                            if (this.hasItemCap) worked |= this.exportItemSlot(target, opposite, filter);
                            break;
                        case FLUID:
                            if (this.hasFluidCap) worked |= this.exportFluidSlot(target, opposite, filter);
                            break;
                        case GAS:
                            if (this.hasGasCap) worked |= this.exportGasSlot(target, opposite, filter);
                            break;
                        case ESSENTIA:
                            if (this.hasEssentiaCap) worked |= this.exportEssentiaSlot(target, opposite, filter);
                            break;
                    }
                }
            }
        } catch (GridAccessException e) {
            return TickRateModulation.SLOWER;
        }

        return worked ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    // region Slot Ordering

    private enum ResourceType { ITEM, FLUID, GAS, ESSENTIA }

    private ResourceType getSlotType(IAEItemStack filter) {
        if (filter == null) return null;
        ItemStack stack = filter.createItemStack();
        if (stack.isEmpty()) return null;
        if (ItemFluidDrop.isFluidDrop(stack)) return ResourceType.FLUID;
        if (ItemGasDrop.isGasDrop(stack)) return ResourceType.GAS;
        if (ItemEssentiaDrop.isEssentiaDrop(stack)) return ResourceType.ESSENTIA;
        return ResourceType.ITEM;
    }

    private List<Integer> getSlotOrder(List<Integer> slots) {
        switch (this.busMode) {
            case SEQUENTIAL:
                return slots;
            case ROUND_ROBIN:
                List<Integer> rotated = new ArrayList<>(slots);
                if (rotated.size() > 1) {
                    int idx = this.roundRobinIndex % rotated.size();
                    Collections.rotate(rotated, -idx);
                    this.roundRobinIndex = (this.roundRobinIndex + 1) % rotated.size();
                }
                return rotated;
            case RANDOM:
                List<Integer> shuffled = new ArrayList<>(slots);
                Collections.shuffle(shuffled, RAND);
                return shuffled;
            default:
                return slots;
        }
    }

    // endregion

    // region Item Export

    private boolean exportItemSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws GridAccessException {
        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, opposite);
        if (adaptor == null) return false;

        IMEMonitor<IAEItemStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        IEnergyGrid energy = this.getProxy().getEnergy();
        FuzzyMode fzMode = (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE);

        long itemsToSend = this.calculateItemsToSend();
        boolean worked = false;

        IAEItemStack toExtract = filter.copy();
        toExtract.setStackSize(itemsToSend);

        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            for (IAEItemStack o : ImmutableList.copyOf(inv.getStorageList().findFuzzy(toExtract, fzMode))) {
                if (o.getStackSize() <= 0) continue;
                long sent = this.pushItemIntoTarget(adaptor, energy, inv, o, itemsToSend);
                if (sent > 0) {
                    itemsToSend -= sent;
                    worked = true;
                }
                if (itemsToSend <= 0) break;
            }
        } else {
            IAEItemStack o = inv.getStorageList().findPrecise(toExtract);
            if (o != null && o.getStackSize() > 0) {
                long sent = this.pushItemIntoTarget(adaptor, energy, inv, o, itemsToSend);
                if (sent > 0) {
                    itemsToSend -= sent;
                    worked = true;
                }
            }
        }

        return worked;
    }

    private boolean exportItemsUnfiltered(TileEntity target, EnumFacing opposite) throws GridAccessException {
        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, opposite);
        if (adaptor == null) return false;

        IMEMonitor<IAEItemStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        IEnergyGrid energy = this.getProxy().getEnergy();

        long itemsToSend = this.calculateItemsToSend();
        boolean worked = false;

        for (IAEItemStack o : ImmutableList.copyOf(inv.getStorageList())) {
            if (o == null || o.getStackSize() <= 0 || isFakeItemFilter(o)) continue;
            if (itemsToSend <= 0) break;
            long sent = this.pushItemIntoTarget(adaptor, energy, inv, o, itemsToSend);
            if (sent > 0) {
                itemsToSend -= sent;
                worked = true;
            }
        }

        return worked;
    }

    private long pushItemIntoTarget(InventoryAdaptor d, IEnergyGrid energy,
                                     IMEMonitor<IAEItemStack> inv, IAEItemStack org, long maxSend) {
        ItemStack inputStack = org.getCachedItemStack(org.getStackSize());
        ItemStack remaining = d.simulateAdd(inputStack);
        if (!remaining.isEmpty()) {
            org.setCachedItemStack(remaining);
            if (remaining == inputStack) {
                return 0;
            }
        }
        long canFit = Math.min(maxSend, org.getStackSize() - (long) remaining.getCount());
        if (canFit <= 0) return 0;

        IAEItemStack ais = org.copy();
        ais.setStackSize(canFit);
        IAEItemStack itemsToAdd = Platform.poweredExtraction(energy, inv, ais, this.source);
        if (itemsToAdd != null) {
            inputStack.setCount((int) Math.min(Integer.MAX_VALUE, itemsToAdd.getStackSize()));
            ItemStack failed = d.addItems(inputStack);
            if (!failed.isEmpty()) {
                ais.setStackSize(failed.getCount());
                inv.injectItems(ais, Actionable.MODULATE, this.source);
                return itemsToAdd.getStackSize() - failed.getCount();
            }
            return itemsToAdd.getStackSize();
        } else {
            org.setCachedItemStack(inputStack);
            return 0;
        }
    }

    private long calculateItemsToSend() {
        int speedUpgrades = this.getInstalledUpgrades(Upgrades.SPEED);
        return Math.min((long) Math.pow(2, speedUpgrades), 64);
    }

    private int availableSlots() {
        int capacityUpgrades = this.getInstalledUpgrades(Upgrades.CAPACITY);
        return Math.min(18 + capacityUpgrades * 9, 63);
    }

    // endregion

    // region Fluid Export

    private boolean exportFluidSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws GridAccessException {
        IFluidHandler fh = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        if (fh == null) return false;

        IMEMonitor<IAEFluidStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        IAEFluidStack wanted = FakeFluids.unpackFluid(filter);
        if (wanted == null || wanted.getFluid() == null) return false;

        IAEFluidStack toExtract = wanted.copy();
        toExtract.setStackSize(this.calculateFluidToSend());

        IAEFluidStack out = inv.extractItems(toExtract, Actionable.SIMULATE, this.source);
        if (out == null || out.getStackSize() <= 0) return false;

        int wasInserted = fh.fill(out.getFluidStack(), false);
        if (wasInserted <= 0) return false;

        toExtract.setStackSize(wasInserted);
        inv.extractItems(toExtract, Actionable.MODULATE, this.source);

        FluidStack toFill = new FluidStack(out.getFluid(), wasInserted);
        fh.fill(toFill, true);
        return true;
    }

    private boolean exportFluidsUnfiltered(TileEntity target, EnumFacing opposite) throws GridAccessException {
        IFluidHandler fh = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        if (fh == null) return false;

        IMEMonitor<IAEFluidStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        for (IAEFluidStack fluid : ImmutableList.copyOf(inv.getStorageList())) {
            if (fluid == null || fluid.getStackSize() <= 0) continue;
            IAEFluidStack toExtract = fluid.copy();
            toExtract.setStackSize(this.calculateFluidToSend());

            IAEFluidStack out = inv.extractItems(toExtract, Actionable.SIMULATE, this.source);
            if (out == null || out.getStackSize() <= 0) continue;

            int wasInserted = fh.fill(out.getFluidStack(), false);
            if (wasInserted <= 0) continue;

            toExtract.setStackSize(wasInserted);
            inv.extractItems(toExtract, Actionable.MODULATE, this.source);

            FluidStack toFill = new FluidStack(out.getFluid(), wasInserted);
            fh.fill(toFill, true);
            return true;
        }

        return false;
    }

    private long calculateFluidToSend() {
        int speedUpgrades = this.getInstalledUpgrades(Upgrades.SPEED);
        return Math.min((long) Math.pow(2, speedUpgrades) * 100L, 8000L);
    }

    // endregion

    // region Gas Export

    private boolean exportGasSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws GridAccessException {
        if (!Loader.isModLoaded("mekanism") || !Loader.isModLoaded("mekeng")) return false;

        Object gasHandler = target.getCapability(getGasCapability(), opposite);
        if (gasHandler == null) return false;

        try {
            return exportGasSlotReflective(gasHandler, opposite, filter);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Gas export slot failed", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean exportGasSlotReflective(Object gasHandler, EnumFacing opposite, IAEItemStack filter) throws Exception {
        Class<?> gasHandlerClass = Class.forName("mekanism.api.gas.IGasHandler");
        Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
        java.lang.reflect.Field amountField = gasStackClass.getField("amount");
        java.lang.reflect.Method receiveGas = gasHandlerClass.getMethod("receiveGas", EnumFacing.class, gasStackClass, boolean.class);

        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv = null;
        try {
            Class<?> gasChannelClass = Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel");
            java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
            Object gasChannel = getChannel.invoke(AEApi.instance().storage(), gasChannelClass);
            java.lang.reflect.Method getInv = this.getProxy().getStorage().getClass().getMethod("getInventory", appeng.api.storage.IStorageChannel.class);
            inv = (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) getInv.invoke(this.getProxy().getStorage(), gasChannel);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to get gas inventory", e);
            return false;
        }

        com.mekeng.github.common.me.data.IAEGasStack wanted = FakeGases.unpackGas(filter);
        if (wanted == null || wanted.getGas() == null) return false;

        com.mekeng.github.common.me.data.IAEGasStack toExtract = wanted.copy();
        toExtract.setStackSize(this.calculateGasToSend());

        com.mekeng.github.common.me.data.IAEGasStack out = inv.extractItems(toExtract, Actionable.SIMULATE, this.source);
        if (out == null || out.getStackSize() <= 0) return false;

        Object gasStack = out.getGasStack();
        int wasInserted = (int) receiveGas.invoke(gasHandler, opposite, gasStack, false);
        if (wasInserted <= 0) return false;

        toExtract.setStackSize(wasInserted);
        inv.extractItems(toExtract, Actionable.MODULATE, this.source);

        Object actualGas = gasStackClass.getConstructor(gasStackClass.getClasses()[0], int.class)
                .newInstance(out.getGasStack().getGas(), wasInserted);
        receiveGas.invoke(gasHandler, opposite, actualGas, true);
        return true;
    }

    private boolean exportGasesUnfiltered(TileEntity target, EnumFacing opposite) throws GridAccessException {
        if (!Loader.isModLoaded("mekanism") || !Loader.isModLoaded("mekeng")) return false;

        Object gasHandler = target.getCapability(getGasCapability(), opposite);
        if (gasHandler == null) return false;

        try {
            return exportGasesUnfilteredReflective(gasHandler, opposite);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Gas export unfiltered failed", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean exportGasesUnfilteredReflective(Object gasHandler, EnumFacing opposite) throws Exception {
        Class<?> gasHandlerClass = Class.forName("mekanism.api.gas.IGasHandler");
        Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
        java.lang.reflect.Field amountField = gasStackClass.getField("amount");
        java.lang.reflect.Method receiveGas = gasHandlerClass.getMethod("receiveGas", EnumFacing.class, gasStackClass, boolean.class);

        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv = null;
        try {
            Class<?> gasChannelClass = Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel");
            java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
            Object gasChannel = getChannel.invoke(AEApi.instance().storage(), gasChannelClass);
            java.lang.reflect.Method getInv = this.getProxy().getStorage().getClass().getMethod("getInventory", appeng.api.storage.IStorageChannel.class);
            inv = (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) getInv.invoke(this.getProxy().getStorage(), gasChannel);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to get gas inventory", e);
            return false;
        }

        for (com.mekeng.github.common.me.data.IAEGasStack gas : ImmutableList.copyOf(inv.getStorageList())) {
            if (gas == null || gas.getStackSize() <= 0) continue;

            com.mekeng.github.common.me.data.IAEGasStack toExtract = gas.copy();
            toExtract.setStackSize(this.calculateGasToSend());

            com.mekeng.github.common.me.data.IAEGasStack out = inv.extractItems(toExtract, Actionable.SIMULATE, this.source);
            if (out == null || out.getStackSize() <= 0) continue;

            Object gasStack = out.getGasStack();
            int wasInserted = (int) receiveGas.invoke(gasHandler, opposite, gasStack, false);
            if (wasInserted <= 0) continue;

            toExtract.setStackSize(wasInserted);
            inv.extractItems(toExtract, Actionable.MODULATE, this.source);

            Object actualGas = gasStackClass.getConstructor(gasStackClass.getClasses()[0], int.class)
                    .newInstance(out.getGasStack().getGas(), wasInserted);
            receiveGas.invoke(gasHandler, opposite, actualGas, true);
            return true;
        }

        return false;
    }

    private long calculateGasToSend() {
        int speedUpgrades = this.getInstalledUpgrades(Upgrades.SPEED);
        return Math.min((long) Math.pow(2, speedUpgrades) * 100L, 8000L);
    }

    // endregion

    // region Essentia Export

    private boolean exportEssentiaSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws GridAccessException {
        if (!Loader.isModLoaded("thaumcraft") || !Loader.isModLoaded("thaumicenergistics")) return false;
        try {
            Class<?> ieTransportClass = Class.forName("thaumcraft.api.aspects.IEssentiaTransport");
            if (!ieTransportClass.isInstance(target)) return false;
        } catch (ClassNotFoundException e) {
            return false;
        }

        try {
            Class<?> helperClass = Class.forName("com.github.aeddddd.ae2enhanced.util.EssentiaBusHelper");
            java.lang.reflect.Method method = helperClass.getMethod("exportEssentiaSlot",
                    appeng.api.networking.IGrid.class, TileEntity.class, EnumFacing.class,
                    IAEItemStack.class, appeng.api.networking.security.IActionSource.class);
            return (Boolean) method.invoke(null, this.getProxy().getGrid(), target, opposite, filter, this.source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Essentia export slot failed", e);
            return false;
        }
    }

    private boolean exportEssentiasUnfiltered(TileEntity target, EnumFacing opposite) throws GridAccessException {
        if (!Loader.isModLoaded("thaumcraft") || !Loader.isModLoaded("thaumicenergistics")) return false;
        try {
            Class<?> ieTransportClass = Class.forName("thaumcraft.api.aspects.IEssentiaTransport");
            if (!ieTransportClass.isInstance(target)) return false;
        } catch (ClassNotFoundException e) {
            return false;
        }

        try {
            Class<?> helperClass = Class.forName("com.github.aeddddd.ae2enhanced.util.EssentiaBusHelper");
            java.lang.reflect.Method method = helperClass.getMethod("exportEssentias",
                    appeng.api.networking.IGrid.class, TileEntity.class, EnumFacing.class,
                    appeng.tile.inventory.AppEngInternalAEInventory.class, appeng.api.networking.security.IActionSource.class);
            return (Boolean) method.invoke(null, this.getProxy().getGrid(), target, opposite, this.config, this.source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Essentia export unfiltered failed", e);
            return false;
        }
    }

    // endregion

    // region Helpers

    private boolean isFakeItemFilter(IAEItemStack filter) {
        if (filter == null) return false;
        ItemStack stack = filter.createItemStack();
        return ItemFluidDrop.isFluidDrop(stack) || ItemGasDrop.isGasDrop(stack) || ItemEssentiaDrop.isEssentiaDrop(stack);
    }

    private net.minecraftforge.common.capabilities.Capability<?> getGasCapability() {
        try {
            return (net.minecraftforge.common.capabilities.Capability<?>)
                    Class.forName("mekanism.common.capabilities.Capabilities")
                            .getField("GAS_HANDLER_CAPABILITY")
                            .get(null);
        } catch (Exception e) {
            return null;
        }
    }

    // endregion

    // region NBT & Network

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("busMode")) {
            int mode = data.getInteger("busMode");
            if (mode >= 0 && mode < BusMode.values().length) {
                this.busMode = BusMode.values()[mode];
            }
        }
        if (data.hasKey("roundRobinIndex")) {
            this.roundRobinIndex = data.getInteger("roundRobinIndex");
        }
        this.config.readFromNBT(data, "config");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("busMode", this.busMode.ordinal());
        data.setInteger("roundRobinIndex", this.roundRobinIndex);
        this.config.writeToNBT(data, "config");
    }

    // endregion

    // region GUI

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (!player.world.isRemote) {
            TileEntity te = this.getHost().getTile();
            int guiId = GuiHandler.GUI_UNIVERSAL_EXPORT_BUS | (this.getSide().ordinal() << 8);
            player.openGui(AE2Enhanced.instance, guiId, te.getWorld(), te.getPos().getX(), te.getPos().getY(), te.getPos().getZ());
        }
        return true;
    }

    // endregion

    // region Model & Collision

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6.0, 6.0, 11.0, 10.0, 10.0, 13.0);
        bch.addBox(5.0, 5.0, 13.0, 11.0, 11.0, 14.0);
        bch.addBox(4.0, 4.0, 14.0, 12.0, 12.0, 16.0);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5.0f;
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        }
        if (this.isPowered()) {
            return MODELS_ON;
        }
        return MODELS_OFF;
    }

    // endregion

    // region Getters / Setters

    public BusMode getBusMode() {
        return this.busMode;
    }

    public void setBusMode(BusMode mode) {
        this.busMode = mode;
        this.saveChanges();
    }

    public AppEngInternalAEInventory getConfig() {
        return this.config;
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("config".equals(name)) {
            return this.config;
        }
        return super.getInventoryByName(name);
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);
    }

    // endregion
}

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
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.util.FakeFluids;
import com.github.aeddddd.ae2enhanced.util.FakeGases;
import com.google.common.collect.ImmutableList;
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
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nonnull;
import java.util.List;

public class PartStockingBus extends PartUpgradeable implements IGridTickable {

    @PartModels
    public static final ResourceLocation[] MODELS = new ResourceLocation[]{
            new ResourceLocation(AE2Enhanced.MOD_ID, "part/stocking_bus_base"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_off"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_on"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_has_channel")
    };

    public static final IPartModel MODELS_OFF = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[1]});
    public static final IPartModel MODELS_ON = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[2]});
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[3]});

    public enum StockingMode {
        BIDIRECTIONAL,
        SUPPLY_ONLY,
        RECOVER_ONLY
    }

    private static final int CONFIG_SIZE = 9;

    private final AppEngInternalAEInventory config;
    private final long[] targetAmounts = new long[CONFIG_SIZE];
    private final IActionSource source;

    private StockingMode mode = StockingMode.BIDIRECTIONAL;

    public PartStockingBus(ItemStack is) {
        super(is);
        this.config = new AppEngInternalAEInventory(this, CONFIG_SIZE);
        this.source = new MachineSource(this);
        for (int i = 0; i < CONFIG_SIZE; i++) {
            this.targetAmounts[i] = 1;
        }
        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
        this.getConfigManager().registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
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

        boolean hasItemCap = target.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, opposite);
        boolean hasFluidCap = target.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        boolean hasGasCap = Loader.isModLoaded("mekanism") && Loader.isModLoaded("mekeng")
                && target.hasCapability(getGasCapability(), opposite);
        boolean hasEssentiaCap = false;
        if (Loader.isModLoaded("thaumcraft") && Loader.isModLoaded("thaumicenergistics")) {
            try {
                Class<?> ieTransportClass = Class.forName("thaumcraft.api.aspects.IEssentiaTransport");
                hasEssentiaCap = ieTransportClass.isInstance(target);
            } catch (ClassNotFoundException e) {
                hasEssentiaCap = false;
            }
        }

        if (!hasItemCap && !hasFluidCap && !hasGasCap && !hasEssentiaCap) {
            return TickRateModulation.SLOWER;
        }

        boolean worked = false;
        long maxWork = this.calculateItemsToSend();

        try {
            for (int slot = 0; slot < CONFIG_SIZE && maxWork > 0; slot++) {
                IAEItemStack filter = this.config.getAEStackInSlot(slot);
                if (filter == null) continue;

                long targetAmount = this.targetAmounts[slot];
                if (targetAmount <= 0) continue;

                ItemStack filterStack = filter.createItemStack();
                if (filterStack.isEmpty()) continue;

                if (ItemFluidDrop.isFluidDrop(filterStack)) {
                    if (!hasFluidCap) continue;
                    worked |= this.handleFluidStocking(target, opposite, slot, filter, targetAmount, maxWork);
                } else if (ItemGasDrop.isGasDrop(filterStack)) {
                    if (!hasGasCap) continue;
                    worked |= this.handleGasStocking(target, opposite, slot, filter, targetAmount, maxWork);
                } else if (ItemEssentiaDrop.isEssentiaDrop(filterStack)) {
                    if (!hasEssentiaCap) continue;
                    worked |= this.handleEssentiaStocking(target, opposite, slot, filter, targetAmount, maxWork);
                } else {
                    if (!hasItemCap) continue;
                    worked |= this.handleItemStocking(target, opposite, slot, filter, targetAmount, maxWork);
                }
            }
        } catch (GridAccessException e) {
            return TickRateModulation.SLOWER;
        }

        return worked ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    private long calculateItemsToSend() {
        int speedUpgrades = this.getInstalledUpgrades(Upgrades.SPEED);
        return Math.min((long) Math.pow(2, speedUpgrades), 64);
    }

    // === Item Stocking ===

    private boolean handleItemStocking(TileEntity target, EnumFacing opposite, int configSlot,
                                        IAEItemStack filter, long targetAmount, long maxWork)
            throws GridAccessException {
        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, opposite);
        if (adaptor == null) return false;

        IMEMonitor<IAEItemStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        IEnergyGrid energy = this.getProxy().getEnergy();
        FuzzyMode fzMode = (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE);
        boolean fuzzy = this.getInstalledUpgrades(Upgrades.FUZZY) > 0;

        long actual = countItems(target, opposite, filter, fuzzy, fzMode);
        long delta = targetAmount - actual;
        boolean worked = false;

        if (delta > 0 && this.mode != StockingMode.RECOVER_ONLY) {
            long toSupply = Math.min(delta, maxWork);
            worked |= supplyItems(adaptor, inv, energy, filter, toSupply, fuzzy, fzMode);
        }

        if (delta < 0 && this.mode != StockingMode.SUPPLY_ONLY) {
            long toRecover = Math.min(-delta, maxWork);
            worked |= recoverItems(adaptor, inv, energy, filter, toRecover, fuzzy, fzMode);
        }

        return worked;
    }

    private long countItems(TileEntity target, EnumFacing opposite, IAEItemStack filter, boolean fuzzy, FuzzyMode fzMode) {
        long count = 0;
        ItemStack filterStack = filter == null ? ItemStack.EMPTY : filter.createItemStack();
        IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, opposite);
        if (handler == null) return 0;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (fuzzy) {
                if (filterStack.isEmpty()) continue;
                IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                IAEItemStack aeFilter = AEItemStack.fromItemStack(filterStack);
                if (aeStack != null && aeFilter != null && aeStack.fuzzyComparison(aeFilter, fzMode)) {
                    count += stack.getCount();
                }
            } else {
                if (ItemStack.areItemsEqual(stack, filterStack) && ItemStack.areItemStackTagsEqual(stack, filterStack)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    private boolean supplyItems(InventoryAdaptor adaptor, IMEMonitor<IAEItemStack> inv, IEnergyGrid energy,
                                 IAEItemStack filter, long amount, boolean fuzzy, FuzzyMode fzMode) {
        if (amount <= 0) return false;
        boolean worked = false;
        long remaining = amount;

        if (fuzzy) {
            for (IAEItemStack candidate : ImmutableList.copyOf(inv.getStorageList().findFuzzy(filter, fzMode))) {
                if (candidate.getStackSize() <= 0) continue;
                long sent = pushItemIntoTarget(adaptor, energy, inv, candidate, remaining);
                if (sent > 0) {
                    remaining -= sent;
                    worked = true;
                }
                if (remaining <= 0) break;
            }
        } else {
            IAEItemStack precise = inv.getStorageList().findPrecise(filter);
            if (precise != null && precise.getStackSize() > 0) {
                long sent = pushItemIntoTarget(adaptor, energy, inv, precise, remaining);
                if (sent > 0) worked = true;
            }
        }
        return worked;
    }

    private boolean recoverItems(InventoryAdaptor adaptor, IMEMonitor<IAEItemStack> inv, IEnergyGrid energy,
                                  IAEItemStack filter, long amount, boolean fuzzy, FuzzyMode fzMode) {
        if (amount <= 0) return false;
        boolean worked = false;
        ItemStack filterStack = filter.createItemStack();
        long remaining = amount;

        while (remaining > 0) {
            int toRemove = (int) Math.min(remaining, 64);
            ItemStack removed = adaptor.removeItems(toRemove, filterStack, null);
            if (removed.isEmpty()) break;

            IAEItemStack aeRemoved = AEItemStack.fromItemStack(removed);
            if (aeRemoved == null) {
                adaptor.addItems(removed);
                break;
            }

            IAEItemStack notInserted = inv.injectItems(aeRemoved, Actionable.SIMULATE, this.source);
            long canInsert = aeRemoved.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
            if (canInsert <= 0) {
                adaptor.addItems(removed);
                break;
            }

            if (canInsert < removed.getCount()) {
                ItemStack returnStack = removed.copy();
                returnStack.setCount((int) (removed.getCount() - canInsert));
                adaptor.addItems(returnStack);
                removed.setCount((int) canInsert);
                aeRemoved.setStackSize(canInsert);
            }

            inv.injectItems(aeRemoved, Actionable.MODULATE, this.source);
            worked = true;
            remaining -= canInsert;
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

    // === Fluid Stocking ===

    private boolean handleFluidStocking(TileEntity target, EnumFacing opposite, int configSlot,
                                         IAEItemStack filter, long targetAmount, long maxWork)
            throws GridAccessException {
        IFluidHandler fh = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        if (fh == null) return false;

        IMEMonitor<IAEFluidStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        IAEFluidStack fluidFilter = FakeFluids.unpackFluid(filter);
        if (fluidFilter == null || fluidFilter.getFluid() == null) return false;
        Fluid targetFluid = fluidFilter.getFluid();

        long actual = countFluids(fh, targetFluid);
        long delta = targetAmount - actual;
        boolean worked = false;

        if (delta > 0 && this.mode != StockingMode.RECOVER_ONLY) {
            long toSupply = Math.min(delta, maxWork * 1000);
            worked |= supplyFluid(fh, inv, targetFluid, toSupply);
        }

        if (delta < 0 && this.mode != StockingMode.SUPPLY_ONLY) {
            long toRecover = Math.min(-delta, maxWork * 1000);
            worked |= recoverFluid(fh, inv, targetFluid, toRecover);
        }

        return worked;
    }

    private long countFluids(IFluidHandler fh, Fluid targetFluid) {
        long count = 0;
        IFluidTankProperties[] tanks = fh.getTankProperties();
        if (tanks != null) {
            for (IFluidTankProperties tank : tanks) {
                FluidStack content = tank.getContents();
                if (content != null && content.getFluid() == targetFluid) {
                    count += content.amount;
                }
            }
        }
        return count;
    }

    private boolean supplyFluid(IFluidHandler fh, IMEMonitor<IAEFluidStack> inv, Fluid fluid, long amount) {
        if (amount <= 0) return false;
        FluidStack wanted = new FluidStack(fluid, (int) Math.min(amount, Integer.MAX_VALUE));
        wanted = canonicalizeFluidStack(wanted);

        IAEFluidStack aeWanted = AEFluidStack.fromFluidStack(wanted);
        if (aeWanted == null) return false;

        IAEFluidStack notExtracted = inv.extractItems(aeWanted, Actionable.SIMULATE, this.source);
        long canExtract = aeWanted.getStackSize() - (notExtracted != null ? notExtracted.getStackSize() : 0);
        if (canExtract <= 0) return false;

        FluidStack toFill = new FluidStack(fluid, (int) canExtract);
        toFill = canonicalizeFluidStack(toFill);
        int filled = fh.fill(toFill, false);
        if (filled <= 0) return false;

        FluidStack actualExtract = new FluidStack(fluid, filled);
        actualExtract = canonicalizeFluidStack(actualExtract);
        IAEFluidStack aeExtract = AEFluidStack.fromFluidStack(actualExtract);
        inv.extractItems(aeExtract, Actionable.MODULATE, this.source);
        fh.fill(actualExtract, true);
        return true;
    }

    private boolean recoverFluid(IFluidHandler fh, IMEMonitor<IAEFluidStack> inv, Fluid fluid, long amount) {
        if (amount <= 0) return false;
        FluidStack toDrain = new FluidStack(fluid, (int) Math.min(amount, Integer.MAX_VALUE));
        toDrain = canonicalizeFluidStack(toDrain);

        FluidStack drained = fh.drain(toDrain, false);
        if (drained == null || drained.amount <= 0) return false;
        drained = canonicalizeFluidStack(drained);

        IAEFluidStack aeDrained = AEFluidStack.fromFluidStack(drained);
        if (aeDrained == null) return false;

        IAEFluidStack notInserted = inv.injectItems(aeDrained, Actionable.SIMULATE, this.source);
        long canInsert = aeDrained.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
        if (canInsert <= 0) return false;

        FluidStack actualDrain = fh.drain(new FluidStack(fluid, (int) canInsert), true);
        if (actualDrain != null && actualDrain.amount > 0) {
            actualDrain = canonicalizeFluidStack(actualDrain);
            inv.injectItems(AEFluidStack.fromFluidStack(actualDrain), Actionable.MODULATE, this.source);
            return true;
        }
        return false;
    }

    private static FluidStack canonicalizeFluidStack(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return fluidStack;
        Fluid canonical = FluidRegistry.getFluid(fluidStack.getFluid().getName());
        if (canonical != null && canonical != fluidStack.getFluid()) {
            return new FluidStack(canonical, fluidStack.amount, fluidStack.tag);
        }
        return fluidStack;
    }

    // === Gas Stocking ===

    private boolean handleGasStocking(TileEntity target, EnumFacing opposite, int configSlot,
                                       IAEItemStack filter, long targetAmount, long maxWork)
            throws GridAccessException {
        if (!Loader.isModLoaded("mekanism") || !Loader.isModLoaded("mekeng")) return false;

        Object gasHandler = target.getCapability(getGasCapability(), opposite);
        if (gasHandler == null) return false;

        try {
            Class<?> gasHandlerClass = Class.forName("mekanism.api.gas.IGasHandler");
            Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
            java.lang.reflect.Field amountField = gasStackClass.getField("amount");
            java.lang.reflect.Method drawGas = gasHandlerClass.getMethod("drawGas", EnumFacing.class, int.class, boolean.class);
            java.lang.reflect.Method receiveGas = gasHandlerClass.getMethod("receiveGas", EnumFacing.class, gasStackClass, boolean.class);

            com.mekeng.github.common.me.data.IAEGasStack wanted = FakeGases.unpackGas(filter);
            if (wanted == null || wanted.getGas() == null) return false;

            long actual = countGas(gasHandler, drawGas, gasStackClass, amountField, opposite, wanted);
            long delta = targetAmount - actual;
            boolean worked = false;

            if (delta > 0 && this.mode != StockingMode.RECOVER_ONLY) {
                long toSupply = Math.min(delta, maxWork * 1000);
                worked |= supplyGas(gasHandler, drawGas, receiveGas, gasStackClass, amountField, opposite, wanted, toSupply);
            }

            if (delta < 0 && this.mode != StockingMode.SUPPLY_ONLY) {
                long toRecover = Math.min(-delta, maxWork * 1000);
                worked |= recoverGas(gasHandler, drawGas, gasStackClass, amountField, opposite, wanted, toRecover);
            }

            return worked;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Gas stocking failed", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private long countGas(Object gasHandler, java.lang.reflect.Method drawGas, Class<?> gasStackClass,
                          java.lang.reflect.Field amountField, EnumFacing opposite,
                          com.mekeng.github.common.me.data.IAEGasStack wanted) throws Exception {
        Object drained = drawGas.invoke(gasHandler, opposite, Integer.MAX_VALUE, false);
        if (drained == null) return 0;
        int amount = amountField.getInt(drained);
        Object gasType = gasStackClass.getMethod("getGas").invoke(drained);
        Object wantedGasType = wanted.getGas();
        if (gasType != null && gasType.equals(wantedGasType)) {
            return amount;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private boolean supplyGas(Object gasHandler, java.lang.reflect.Method drawGas, java.lang.reflect.Method receiveGas,
                               Class<?> gasStackClass, java.lang.reflect.Field amountField,
                               EnumFacing opposite, com.mekeng.github.common.me.data.IAEGasStack wanted, long amount)
            throws Exception {
        if (amount <= 0) return false;

        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv = null;
        try {
            Class<?> gasChannelClass = Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel");
            java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
            Object gasChannel = getChannel.invoke(AEApi.instance().storage(), gasChannelClass);
            java.lang.reflect.Method getInv = this.getProxy().getStorage().getClass().getMethod("getInventory", appeng.api.storage.IStorageChannel.class);
            inv = (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) getInv.invoke(this.getProxy().getStorage(), gasChannel);
        } catch (Exception e) {
            return false;
        }

        Object wantedGas = wanted.getGas();
        Object supplyStack = gasStackClass.getConstructor(wantedGas.getClass(), int.class)
                .newInstance(wantedGas, (int) Math.min(amount, Integer.MAX_VALUE));

        com.mekeng.github.common.me.data.impl.AEGasStack aeSupply =
                com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) supplyStack);
        if (aeSupply == null) return false;

        com.mekeng.github.common.me.data.IAEGasStack notExtracted = inv.extractItems(aeSupply, Actionable.SIMULATE, this.source);
        long canExtract = aeSupply.getStackSize() - (notExtracted != null ? notExtracted.getStackSize() : 0);
        if (canExtract <= 0) return false;

        Object toReceive = gasStackClass.getConstructor(wantedGas.getClass(), int.class)
                .newInstance(wantedGas, (int) canExtract);
        int received = (int) receiveGas.invoke(gasHandler, opposite, toReceive, false);
        if (received <= 0) return false;

        Object actualExtract = gasStackClass.getConstructor(wantedGas.getClass(), int.class)
                .newInstance(wantedGas, received);
        com.mekeng.github.common.me.data.impl.AEGasStack aeExtract =
                com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) actualExtract);
        inv.extractItems(aeExtract, Actionable.MODULATE, this.source);
        receiveGas.invoke(gasHandler, opposite, actualExtract, true);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean recoverGas(Object gasHandler, java.lang.reflect.Method drawGas, Class<?> gasStackClass,
                                java.lang.reflect.Field amountField, EnumFacing opposite,
                                com.mekeng.github.common.me.data.IAEGasStack wanted, long amount) throws Exception {
        if (amount <= 0) return false;

        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv = null;
        try {
            Class<?> gasChannelClass = Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel");
            java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
            Object gasChannel = getChannel.invoke(AEApi.instance().storage(), gasChannelClass);
            java.lang.reflect.Method getInv = this.getProxy().getStorage().getClass().getMethod("getInventory", appeng.api.storage.IStorageChannel.class);
            inv = (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) getInv.invoke(this.getProxy().getStorage(), gasChannel);
        } catch (Exception e) {
            return false;
        }

        Object wantedGas = wanted.getGas();
        Object drained = drawGas.invoke(gasHandler, opposite, (int) Math.min(amount, Integer.MAX_VALUE), false);
        if (drained == null) return false;
        int drainedAmount = amountField.getInt(drained);
        if (drainedAmount <= 0) return false;

        Object actualDraw = gasStackClass.getConstructor(wantedGas.getClass(), int.class)
                .newInstance(wantedGas, drainedAmount);
        com.mekeng.github.common.me.data.impl.AEGasStack aeDraw =
                com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) actualDraw);
        if (aeDraw == null) return false;

        com.mekeng.github.common.me.data.IAEGasStack notInserted = inv.injectItems(aeDraw, Actionable.SIMULATE, this.source);
        long canInsert = aeDraw.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
        if (canInsert <= 0) return false;

        Object realDraw = gasStackClass.getConstructor(wantedGas.getClass(), int.class)
                .newInstance(wantedGas, (int) canInsert);
        Object actual = drawGas.invoke(gasHandler, opposite, (int) canInsert, true);
        if (actual != null) {
            int actualAmount = amountField.getInt(actual);
            if (actualAmount > 0) {
                Object toInsert = gasStackClass.getConstructor(wantedGas.getClass(), int.class)
                        .newInstance(wantedGas, actualAmount);
                com.mekeng.github.common.me.data.impl.AEGasStack aeInsert =
                        com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) toInsert);
                inv.injectItems(aeInsert, Actionable.MODULATE, this.source);
                return true;
            }
        }
        return false;
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

    // === Essentia Stocking ===

    private boolean handleEssentiaStocking(TileEntity target, EnumFacing opposite, int configSlot,
                                            IAEItemStack filter, long targetAmount, long maxWork)
            throws GridAccessException {
        if (!Loader.isModLoaded("thaumcraft") || !Loader.isModLoaded("thaumicenergistics")) return false;
        try {
            Class<?> ieTransportClass = Class.forName("thaumcraft.api.aspects.IEssentiaTransport");
            if (!ieTransportClass.isInstance(target)) return false;
        } catch (ClassNotFoundException e) {
            return false;
        }

        try {
            Class<?> helperClass = Class.forName("com.github.aeddddd.ae2enhanced.util.EssentiaBusHelper");
            java.lang.reflect.Method method = helperClass.getMethod("stockEssentias",
                    appeng.api.networking.IGrid.class, TileEntity.class, EnumFacing.class,
                    IAEItemStack.class, long.class, long.class, int.class, IActionSource.class);
            return (Boolean) method.invoke(null, this.getProxy().getGrid(), target, opposite,
                    filter, targetAmount, maxWork, this.mode.ordinal(), this.source);
        } catch (NoSuchMethodException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] EssentiaBusHelper.stockEssentias not found, skipping essentia stocking");
            return false;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Essentia stocking failed", e);
            return false;
        }
    }

    // === NBT ===

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("stockingMode")) {
            int modeOrd = data.getInteger("stockingMode");
            if (modeOrd >= 0 && modeOrd < StockingMode.values().length) {
                this.mode = StockingMode.values()[modeOrd];
            }
        }
        for (int i = 0; i < CONFIG_SIZE; i++) {
            this.targetAmounts[i] = data.getLong("targetAmount_" + i);
            if (this.targetAmounts[i] <= 0) {
                this.targetAmounts[i] = 1;
            }
        }
        this.config.readFromNBT(data, "config");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("stockingMode", this.mode.ordinal());
        for (int i = 0; i < CONFIG_SIZE; i++) {
            data.setLong("targetAmount_" + i, this.targetAmounts[i]);
        }
        this.config.writeToNBT(data, "config");
    }

    // === GUI ===

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (!player.world.isRemote) {
            TileEntity te = this.getHost().getTile();
            int guiId = GuiHandler.GUI_STOCKING_BUS | (this.getSide().ordinal() << 8);
            player.openGui(AE2Enhanced.instance, guiId, te.getWorld(), te.getPos().getX(), te.getPos().getY(), te.getPos().getZ());
        }
        return true;
    }

    // === Model & Collision ===

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

    // === Getters / Setters ===

    public StockingMode getMode() {
        return this.mode;
    }

    public void setMode(StockingMode mode) {
        this.mode = mode;
        this.saveChanges();
    }

    public long getTargetAmount(int slot) {
        if (slot < 0 || slot >= CONFIG_SIZE) return 1;
        return this.targetAmounts[slot];
    }

    public void setTargetAmount(int slot, long amount) {
        if (slot < 0 || slot >= CONFIG_SIZE) return;
        this.targetAmounts[slot] = Math.max(0, amount);
        this.saveChanges();
    }

    public AppEngInternalAEInventory getConfig() {
        return this.config;
    }

    @Override
    public net.minecraftforge.items.IItemHandler getInventoryByName(String name) {
        if ("config".equals(name)) {
            return this.config;
        }
        return super.getInventoryByName(name);
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);
    }
}

package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟合成资源提取器：统一从 AE2 网络提取各类资源，并支持原子回滚。
 *
 * <p>当前支持：物品、流体、RF、Mana、Starlight、气体、源质。
 * 所有非物品/流体通道均通过反射 + {@link IStorageGrid#getInventory(IStorageChannel)} 访问，
 * 避免硬引用可选 mod 类，同时修正了旧实现中错误的 channel 类名和不存在的方法名。</p>
 */
public class VirtualCostExtractor {

    private static final double ENERGY_EPSILON = 0.0001;

    /**
     * 模拟提取全部资源，返回是否可行。
     */
    public static boolean simulateExtract(IStorageGrid storage, List<IAEStack> costs, IActionSource source) {
        for (IAEStack cost : costs) {
            if (isEmpty(cost)) continue;
            IAEStack remaining = extractOne(storage, cost, Actionable.SIMULATE, source);
            if (!isEmpty(remaining)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 实际提取全部资源，失败时自动回滚已提取部分。
     *
     * @return 是否全部提取成功
     */
    public static boolean extractAll(IStorageGrid storage, List<IAEStack> costs, IActionSource source) {
        List<IAEStack> extracted = new ArrayList<>();
        for (IAEStack cost : costs) {
            if (isEmpty(cost)) continue;
            IAEStack remaining = extractOne(storage, cost, Actionable.MODULATE, source);
            if (!isEmpty(remaining)) {
                rollback(storage, extracted, source);
                return false;
            }
            extracted.add(cost.copy());
        }
        return true;
    }

    /**
     * 模拟扣除 AE 能量，返回网络是否有足够能量。
     */
    public static boolean simulateExtractEnergy(IEnergySource energy, double amount) {
        if (amount <= 0) return true;
        return energy.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG) >= amount - ENERGY_EPSILON;
    }

    /**
     * 扣除 AE 能量。
     *
     * @return 是否扣除成功
     */
    public static boolean extractEnergy(IEnergySource energy, double amount, IActionSource source) {
        if (amount <= 0) return true;
        return energy.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG) >= amount - ENERGY_EPSILON;
    }

    private static boolean isEmpty(IAEStack stack) {
        return stack == null || stack.getStackSize() <= 0;
    }

    private static IAEStack extractOne(IStorageGrid storage, IAEStack cost,
                                       Actionable mode, IActionSource source) {
        if (cost instanceof IAEItemStack) {
            IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            return storage.getInventory(channel).extractItems((IAEItemStack) cost, mode, source);
        }
        if (cost instanceof IAEFluidStack) {
            IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            return storage.getInventory(channel).extractItems((IAEFluidStack) cost, mode, source);
        }

        String className = cost.getClass().getName();
        if (className.contains("AEEnergyStack")) {
            return extractViaChannel(storage, cost, mode, source,
                    "com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel", true);
        }
        if (className.contains("AEManaStack")) {
            return extractViaChannel(storage, cost, mode, source,
                    "com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel", true);
        }
        if (className.contains("AEStarlightStack")) {
            return extractViaChannel(storage, cost, mode, source,
                    "com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel", true);
        }
        if (className.contains("GasStack")) {
            return extractViaChannel(storage, cost, mode, source,
                    "com.mekeng.github.common.me.storage.IGasStorageChannel",
                    Loader.isModLoaded("mekanism") && Loader.isModLoaded("mekeng"));
        }
        if (className.contains("EssentiaStack")) {
            return extractViaChannel(storage, cost, mode, source,
                    "thaumicenergistics.api.storage.IEssentiaStorageChannel",
                    Loader.isModLoaded("thaumcraft") && Loader.isModLoaded("thaumicenergistics"));
        }

        AE2Enhanced.LOGGER.warn("[AE2E] Unknown virtual cost stack type: {}", className);
        return cost;
    }

    @SuppressWarnings("unchecked")
    private static IAEStack extractViaChannel(IStorageGrid storage, IAEStack cost, Actionable mode, IActionSource source,
                                              String channelClassName, boolean modLoaded) {
        if (!modLoaded) return cost;
        try {
            Class<?> channelClass = Class.forName(channelClassName);
            IStorageChannel<?> channel = (IStorageChannel<?>) AEApi.instance().storage().getStorageChannel((Class) channelClass);
            if (channel == null) return cost;

            Object monitor = IStorageGrid.class
                    .getMethod("getInventory", IStorageChannel.class)
                    .invoke(storage, channel);
            if (monitor == null) return cost;

            return (IAEStack) monitor.getClass()
                    .getMethod("extractItems", IAEStack.class, Actionable.class, IActionSource.class)
                    .invoke(monitor, cost, mode, source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to extract via channel {}: {}", channelClassName, e.getMessage());
            return cost;
        }
    }

    private static void rollback(IStorageGrid storage, List<IAEStack> extracted, IActionSource source) {
        for (IAEStack stack : extracted) {
            injectOne(storage, stack, source);
        }
    }

    private static void injectOne(IStorageGrid storage, IAEStack stack, IActionSource source) {
        if (stack instanceof IAEItemStack) {
            IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            storage.getInventory(channel).injectItems((IAEItemStack) stack, Actionable.MODULATE, source);
            return;
        }
        if (stack instanceof IAEFluidStack) {
            IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            storage.getInventory(channel).injectItems((IAEFluidStack) stack, Actionable.MODULATE, source);
            return;
        }

        String className = stack.getClass().getName();
        if (className.contains("AEEnergyStack")) {
            injectViaChannel(storage, stack, source, "com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel");
            return;
        }
        if (className.contains("AEManaStack")) {
            injectViaChannel(storage, stack, source, "com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel");
            return;
        }
        if (className.contains("AEStarlightStack")) {
            injectViaChannel(storage, stack, source, "com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel");
            return;
        }
        if (className.contains("GasStack")) {
            injectViaChannel(storage, stack, source, "com.mekeng.github.common.me.storage.IGasStorageChannel");
            return;
        }
        if (className.contains("EssentiaStack")) {
            injectViaChannel(storage, stack, source, "thaumicenergistics.api.storage.IEssentiaStorageChannel");
            return;
        }
        AE2Enhanced.LOGGER.warn("[AE2E] Cannot rollback unknown stack type: {}", className);
    }

    @SuppressWarnings("unchecked")
    private static void injectViaChannel(IStorageGrid storage, IAEStack stack, IActionSource source, String channelClassName) {
        try {
            Class<?> channelClass = Class.forName(channelClassName);
            IStorageChannel<?> channel = (IStorageChannel<?>) AEApi.instance().storage().getStorageChannel((Class) channelClass);
            if (channel == null) return;

            Object monitor = IStorageGrid.class
                    .getMethod("getInventory", IStorageChannel.class)
                    .invoke(storage, channel);
            if (monitor == null) return;

            monitor.getClass()
                    .getMethod("injectItems", IAEStack.class, Actionable.class, IActionSource.class)
                    .invoke(monitor, stack, Actionable.MODULATE, source);
        } catch (Exception ignored) {
        }
    }
}

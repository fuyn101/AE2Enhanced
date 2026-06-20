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
 * 随着更多 handler 接入批量虚拟合成，可继续扩展。</p>
 */
public class VirtualCostExtractor {

    /**
     * 模拟提取全部资源，返回是否可行。
     */
    public static boolean simulateExtract(IStorageGrid storage, List<IAEStack> costs, IActionSource source) {
        for (IAEStack cost : costs) {
            IAEStack remaining = extractOne(storage, cost, Actionable.SIMULATE, source);
            if (remaining != null && remaining.getStackSize() > 0) {
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
            IAEStack remaining = extractOne(storage, cost, Actionable.MODULATE, source);
            if (remaining != null && remaining.getStackSize() > 0) {
                rollback(storage, extracted, source);
                return false;
            }
            extracted.add(cost.copy());
        }
        return true;
    }

    /**
     * 扣除 AE 能量。
     *
     * @return 是否扣除成功
     */
    public static boolean extractEnergy(IEnergySource energy, double amount, IActionSource source) {
        return energy.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG) >= amount - 0.0001;
    }

    private static IAEStack extractOne(IStorageGrid storage, IAEStack cost,
                                       Actionable mode, IActionSource source) {
        String className = cost.getClass().getName();
        if (cost instanceof IAEItemStack) {
            IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            return storage.getInventory(channel).extractItems((IAEItemStack) cost, mode, source);
        }
        if (cost instanceof IAEFluidStack) {
            IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            return storage.getInventory(channel).extractItems((IAEFluidStack) cost, mode, source);
        }
        if (className.contains("GasStack")) {
            return extractViaChannel(storage, cost, mode, source,
                    "appeng.api.storage.channels.IGasStorageChannel", Loader.isModLoaded("mekanism") && Loader.isModLoaded("mekeng"));
        }
        if (className.contains("EssentiaStack")) {
            return extractViaChannel(storage, cost, mode, source,
                    "appeng.api.storage.channels.IEssentiaStorageChannel", Loader.isModLoaded("thaumcraft"));
        }
        // RF / Mana / Starlight 自定义标量通道
        return extractScalar(storage, cost, mode, source);
    }

    @SuppressWarnings("unchecked")
    private static IAEStack extractViaChannel(IStorageGrid storage, IAEStack cost, Actionable mode, IActionSource source,
                                              String channelClassName, boolean modLoaded) {
        if (!modLoaded) return cost;
        try {
            Class<?> channelClass = Class.forName(channelClassName);
            IStorageChannel<?> channel = (IStorageChannel<?>) AEApi.instance().storage().getStorageChannel((Class) channelClass);
            if (channel == null) return cost;
            Object monitor = channelClass.getMethod("getInventory").invoke(channel);
            return (IAEStack) monitor.getClass().getMethod("extractItems", cost.getClass(), Actionable.class, IActionSource.class)
                    .invoke(monitor, cost, mode, source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to extract via channel {}: {}", channelClassName, e.getMessage());
            return cost;
        }
    }

    private static IAEStack extractScalar(IStorageGrid storage, IAEStack cost, Actionable mode, IActionSource source) {
        String className = cost.getClass().getName();
        String channelClassName = null;
        if (className.contains("AEEnergyStack")) {
            channelClassName = "com.github.aeddddd.ae2enhanced.storage.IEnergyStorageChannel";
        } else if (className.contains("AEManaStack")) {
            channelClassName = "com.github.aeddddd.ae2enhanced.storage.IManaStorageChannel";
        } else if (className.contains("AEStarlightStack")) {
            channelClassName = "com.github.aeddddd.ae2enhanced.storage.IStarlightStorageChannel";
        }
        if (channelClassName == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] Unknown scalar stack type: {}", className);
            return cost;
        }
        return extractViaChannel(storage, cost, mode, source, channelClassName, true);
    }

    private static void rollback(IStorageGrid storage, List<IAEStack> extracted, IActionSource source) {
        for (IAEStack stack : extracted) {
            injectOne(storage, stack, source);
        }
    }

    private static void injectOne(IStorageGrid storage, IAEStack stack, IActionSource source) {
        String className = stack.getClass().getName();
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
        if (className.contains("GasStack")) {
            injectViaChannel(storage, stack, source, "appeng.api.storage.channels.IGasStorageChannel");
            return;
        }
        if (className.contains("EssentiaStack")) {
            injectViaChannel(storage, stack, source, "appeng.api.storage.channels.IEssentiaStorageChannel");
            return;
        }
        // RF / Mana / Starlight rollback
        String channelClassName = null;
        if (className.contains("AEEnergyStack")) {
            channelClassName = "com.github.aeddddd.ae2enhanced.storage.IEnergyStorageChannel";
        } else if (className.contains("AEManaStack")) {
            channelClassName = "com.github.aeddddd.ae2enhanced.storage.IManaStorageChannel";
        } else if (className.contains("AEStarlightStack")) {
            channelClassName = "com.github.aeddddd.ae2enhanced.storage.IStarlightStorageChannel";
        }
        if (channelClassName != null) {
            injectViaChannel(storage, stack, source, channelClassName);
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectViaChannel(IStorageGrid storage, IAEStack stack, IActionSource source, String channelClassName) {
        try {
            Class<?> channelClass = Class.forName(channelClassName);
            IStorageChannel<?> channel = (IStorageChannel<?>) AEApi.instance().storage().getStorageChannel((Class) channelClass);
            if (channel == null) return;
            Object monitor = channelClass.getMethod("getInventory").invoke(channel);
            monitor.getClass().getMethod("injectItems", stack.getClass(), Actionable.class, IActionSource.class)
                    .invoke(monitor, stack, Actionable.MODULATE, source);
        } catch (Exception ignored) {
        }
    }
}

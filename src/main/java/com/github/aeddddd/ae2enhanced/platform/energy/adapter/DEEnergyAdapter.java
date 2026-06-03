package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Draconic Evolution 专用能量适配器。
 *
 * <p>核心突破：对于所有实现 {@code IExtendedRFStorage} 的龙研设备（包括
 * {@code TileEnergyStorageCore}、{@code TileCraftingInjector} 等），通过反射
 * 直接操作其内部 {@code ManagedLong energy.value} 字段，实现 {@code long} 级别的
 * 瞬间注入，完全 bypass 标准 Forge {@link IEnergyStorage#receiveEnergy(int, boolean)}
 * 的 {@code int} 上限（2.1B）以及设备自身的 tick 级限流（如 CraftingInjector 的
 * {@code maxRFPerTick = cost / chargeSpeedModifier}）。</p>
 *
 * <p>对于不实现 {@code IExtendedRFStorage} 的龙研设备，回退到标准 FE 多调用策略。</p>
 */
public class DEEnergyAdapter implements IEnergyAdapter {

    private static final String[] BLOCK_PATTERNS = {
        "draconicevolution:"
    };

    // IExtendedRFStorage 接口反射缓存
    private static Class<?> extendedRFStorageClass;
    private static Method getExtendedStorageMethod;
    private static Method getExtendedCapacityMethod;
    private static Field managedLongValueField;
    private static boolean interfaceReflectionReady = false;

    // 各具体 Tile 类的 energy 字段缓存（key = tile class, value = declared Field("energy")）
    private static final Map<Class<?>, Field> ENERGY_FIELD_CACHE = new HashMap<>();

    public DEEnergyAdapter() {
        initInterfaceReflection();
    }

    private static synchronized void initInterfaceReflection() {
        if (interfaceReflectionReady) {
            return;
        }
        try {
            extendedRFStorageClass = Class.forName(
                    "com.brandon3055.draconicevolution.api.IExtendedRFStorage");
            getExtendedStorageMethod = extendedRFStorageClass.getMethod("getExtendedStorage");
            getExtendedCapacityMethod = extendedRFStorageClass.getMethod("getExtendedCapacity");

            // 预加载 TileEnergyStorageCore 的 energy 字段，用于推导 ManagedLong 类型
            Class<?> coreClass = Class.forName(
                    "com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyStorageCore");
            Field coreEnergyField = coreClass.getDeclaredField("energy");
            coreEnergyField.setAccessible(true);
            ENERGY_FIELD_CACHE.put(coreClass, coreEnergyField);

            // 在 ManagedLong 及其父类链中查找 long value 字段
            managedLongValueField = findLongValueField(coreEnergyField.getType());

            interfaceReflectionReady = true;
        } catch (Exception e) {
            // 反射失败，将完全回退到标准 Forge 策略
        }
    }

    private static Field findLongValueField(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField("value");
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException e) {
                // 继续查找父类
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * 获取（或缓存）指定 Tile 类的 energy 字段。
     */
    private static Field getEnergyField(TileEntity tile) {
        if (tile == null) return null;
        Class<?> clazz = tile.getClass();
        Field f = ENERGY_FIELD_CACHE.get(clazz);
        if (f != null) return f;

        try {
            f = clazz.getDeclaredField("energy");
            if (f.getType().getSimpleName().equals("ManagedLong")) {
                f.setAccessible(true);
                ENERGY_FIELD_CACHE.put(clazz, f);
                return f;
            }
        } catch (NoSuchFieldException e) {
            // 该类没有 energy 字段
        }
        ENERGY_FIELD_CACHE.put(clazz, null);
        return null;
    }

    @Override
    public boolean canHandle(String blockId) {
        for (String pattern : BLOCK_PATTERNS) {
            if (blockId.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long getReceiveableEnergy(TileEntity tile, IEnergyStorage cap) {
        if (interfaceReflectionReady && isExtendedRFStorage(tile)) {
            Field energyField = getEnergyField(tile);
            if (energyField != null && managedLongValueField != null) {
                try {
                    long stored = (Long) getExtendedStorageMethod.invoke(tile);
                    long capacity = (Long) getExtendedCapacityMethod.invoke(tile);
                    // TileCraftingInjector 的 getExtendedCapacity() 因 bug 返回 0，
                    // 此时视容量为 Long.MAX_VALUE，允许无限制注入
                    if (capacity <= 0L) {
                        capacity = Long.MAX_VALUE;
                    }
                    return Math.max(0L, capacity - stored);
                } catch (Exception e) {
                    // 反射失败，回退
                }
            }
        }
        return fallbackReceiveable(cap);
    }

    @Override
    public long injectEnergy(TileEntity tile, IEnergyStorage cap, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (interfaceReflectionReady && isExtendedRFStorage(tile)) {
            Field energyField = getEnergyField(tile);
            if (energyField != null && managedLongValueField != null) {
                try {
                    long stored = (Long) getExtendedStorageMethod.invoke(tile);
                    long capacity = (Long) getExtendedCapacityMethod.invoke(tile);
                    if (capacity <= 0L) {
                        capacity = Long.MAX_VALUE;
                    }
                    long canAdd = Math.max(0L, capacity - stored);
                    long toAdd = Math.min(amount, canAdd);
                    if (toAdd > 0 && !simulate) {
                        Object managedLong = energyField.get(tile);
                        managedLongValueField.setLong(managedLong, stored + toAdd);
                        tile.markDirty();
                    }
                    return toAdd;
                } catch (Exception e) {
                    // 反射失败，回退
                }
            }
        }
        return fallbackInject(cap, amount, simulate);
    }

    private boolean isExtendedRFStorage(TileEntity tile) {
        return tile != null && extendedRFStorageClass.isInstance(tile);
    }

    private static long fallbackReceiveable(IEnergyStorage cap) {
        if (cap == null || !cap.canReceive()) {
            return 0;
        }
        return cap.receiveEnergy(Integer.MAX_VALUE, true);
    }

    private static long fallbackInject(IEnergyStorage cap, long amount, boolean simulate) {
        if (cap == null || !cap.canReceive() || amount <= 0) {
            return 0;
        }
        if (simulate) {
            return cap.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), true);
        }
        long total = 0;
        while (amount > 0) {
            int toReceive = (int) Math.min(amount, Integer.MAX_VALUE);
            int injected = cap.receiveEnergy(toReceive, false);
            if (injected <= 0) {
                break;
            }
            total += injected;
            amount -= injected;
        }
        return total;
    }
}

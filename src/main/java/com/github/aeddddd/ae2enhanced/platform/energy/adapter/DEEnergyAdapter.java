package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Draconic Evolution 专用能量适配器。
 *
 * <p>核心突破：对于 {@code TileEnergyStorageCore}，通过反射直接操作其内部
 * {@code ManagedLong energy.value} 字段，实现 {@code long} 级别的瞬间注入，
 * 完全 bypass 标准 Forge {@link IEnergyStorage#receiveEnergy(int, boolean)} 的
 * {@code int} 上限（2.1B）限制。</p>
 *
 * <p>龙研能量核心各级容量：</p>
 * <ul>
 *   <li>Tier 1 (Draconium): ~45.5M RF</li>
 *   <li>Tier 2 (Wyvern): ~2.9B RF</li>
 *   <li>Tier 3 (Draconic): ~45.5B RF</li>
 *   <li>Tier 4 (Chaotic): ~911B RF</li>
 * </ul>
 *
 * <p>对于其他龙研设备（能量水晶、能量注入器等），回退到标准 FE 多调用策略。</p>
 */
public class DEEnergyAdapter implements IEnergyAdapter {

    private static final String[] BLOCK_PATTERNS = {
        "draconicevolution:"
    };

    // TileEnergyStorageCore 反射缓存
    private static Class<?> energyStorageCoreClass;
    private static Field energyField; // ManagedLong energy
    private static Field managedLongValueField; // long value
    private static Method getExtendedStorageMethod;
    private static Method getExtendedCapacityMethod;
    private static boolean coreReflectionReady = false;

    public DEEnergyAdapter() {
        initCoreReflection();
    }

    private static synchronized void initCoreReflection() {
        if (coreReflectionReady) {
            return;
        }
        try {
            energyStorageCoreClass = Class.forName(
                    "com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyStorageCore");
            energyField = energyStorageCoreClass.getField("energy");

            // 在 ManagedLong 及其父类链中查找 public long value 字段
            Class<?> managedLongClass = energyField.getType();
            managedLongValueField = findLongValueField(managedLongClass);

            getExtendedStorageMethod = energyStorageCoreClass.getMethod("getExtendedStorage");
            getExtendedCapacityMethod = energyStorageCoreClass.getMethod("getExtendedCapacity");

            coreReflectionReady = true;
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
        if (coreReflectionReady && isEnergyStorageCore(tile)) {
            try {
                long stored = (Long) getExtendedStorageMethod.invoke(tile);
                long capacity = (Long) getExtendedCapacityMethod.invoke(tile);
                return Math.max(0L, capacity - stored);
            } catch (Exception e) {
                // 反射失败，回退
            }
        }
        return fallbackReceiveable(cap);
    }

    @Override
    public long injectEnergy(TileEntity tile, IEnergyStorage cap, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (coreReflectionReady && isEnergyStorageCore(tile)) {
            try {
                long stored = (Long) getExtendedStorageMethod.invoke(tile);
                long capacity = (Long) getExtendedCapacityMethod.invoke(tile);
                long canAdd = Math.max(0L, capacity - stored);
                long toAdd = Math.min(amount, canAdd);
                if (toAdd > 0 && !simulate) {
                    Object managedLong = energyField.get(tile);
                    if (managedLongValueField != null) {
                        managedLongValueField.setLong(managedLong, stored + toAdd);
                    }
                    tile.markDirty();
                }
                return toAdd;
            } catch (Exception e) {
                // 反射失败，回退
            }
        }
        return fallbackInject(cap, amount, simulate);
    }

    private boolean isEnergyStorageCore(TileEntity tile) {
        return tile != null && energyStorageCoreClass.isInstance(tile);
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

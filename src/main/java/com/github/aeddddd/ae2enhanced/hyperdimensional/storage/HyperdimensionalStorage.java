package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import appeng.api.stacks.AEKey;

/**
 * 超维度仓储的内部存储容器。
 * <p>以 {@link AEKey} 为键、{@link BigInteger} 为数量，支持所有已注册的 AE key type。</p>
 */
public class HyperdimensionalStorage {

    /**
     * 每种 key 的容量上限。该值远大于 {@link Long#MAX_VALUE}，
     * 确保在 AE2 网络侧看到上限之前内部不会溢出。
     */
    public static final BigInteger CAPACITY_PER_KEY = BigInteger.TEN.pow(36);

    private final UUID nexusId;
    private final Map<AEKey, BigInteger> contents = new HashMap<>();
    private final List<StorageListener> listeners = new ArrayList<>();
    private final Consumer<HyperdimensionalStorage> changeCallback;

    private boolean dirty = false;

    public HyperdimensionalStorage(UUID nexusId) {
        this(nexusId, null);
    }

    public HyperdimensionalStorage(UUID nexusId, @Nullable Consumer<HyperdimensionalStorage> changeCallback) {
        this.nexusId = nexusId;
        this.changeCallback = changeCallback;
    }

    public UUID getNexusId() {
        return nexusId;
    }

    public Map<AEKey, BigInteger> getContents() {
        return Collections.unmodifiableMap(contents);
    }

    public BigInteger get(AEKey key) {
        return contents.getOrDefault(key, BigInteger.ZERO);
    }

    /**
     * 获取指定 key 的剩余可用容量。
     */
    public BigInteger getRemainingCapacity(AEKey key) {
        return CAPACITY_PER_KEY.subtract(get(key)).max(BigInteger.ZERO);
    }

    /**
     * 增加指定 key 的数量，受 {@link #CAPACITY_PER_KEY} 限制。
     *
     * @return 实际增加的数量
     */
    public BigInteger add(AEKey key, BigInteger amount) {
        if (amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger current = contents.getOrDefault(key, BigInteger.ZERO);
        BigInteger actual = amount.min(CAPACITY_PER_KEY.subtract(current));
        if (actual.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger next = current.add(actual);
        if (next.signum() == 0) {
            contents.remove(key);
        } else {
            contents.put(key, next);
        }
        markDirty();
        return actual;
    }

    /**
     * 减少指定 key 的数量，不会减到负数。
     *
     * @return 实际减少的数量
     */
    public BigInteger remove(AEKey key, BigInteger amount) {
        if (amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger current = contents.getOrDefault(key, BigInteger.ZERO);
        BigInteger removed = amount.min(current);
        if (removed.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger next = current.subtract(removed);
        if (next.signum() == 0) {
            contents.remove(key);
        } else {
            contents.put(key, next);
        }
        markDirty();
        return removed;
    }

    public void set(AEKey key, BigInteger amount) {
        if (amount.signum() <= 0 || amount.compareTo(CAPACITY_PER_KEY) > 0) {
            contents.remove(key);
        } else {
            contents.put(key, amount);
        }
        markDirty();
    }

    public void addListener(StorageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StorageListener listener) {
        listeners.remove(listener);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    private void markDirty() {
        dirty = true;
        for (StorageListener listener : listeners) {
            listener.onStorageChanged(this);
        }
        if (changeCallback != null) {
            changeCallback.accept(this);
        }
    }

    /**
     * 存储变化监听器。
     */
    public interface StorageListener {
        void onStorageChanged(HyperdimensionalStorage storage);
    }
}

package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * 超维度仓储中枢的泛型存储适配器基类.
 *
 * 统一物品/流体/气体/源质四种存储类型的公共逻辑：
 * - injectItems / extractItems 的 SIMULATE/MODULATE 事务
 * - getAvailableItems / getStorageList 的遍历与数量转换
 * - recalcTotal / getTotalCount / isSafeMode 等状态查询
 * - Listener 管理与 postChange 通知
 *
 * 子类只需实现：
 * - {@link #createDescriptor(T)}：从 AE 堆叠构造描述符
 * - {@link #createResult(T, BigInteger)}：从请求堆叠 + 提取量构造结果
 * - {@link #getAETemplate(D)}：从描述符获取 AE 模板(stackSize=1)
 * - {@link #getChannel()}：返回对应的 IStorageChannel
 *
 * @param <T> AE 堆叠类型,如 IAEItemStack / IAEFluidStack / IAEGasStack / IAEEssentiaStack
 * @param <D> 描述符类型,如 ItemDescriptor / FluidDescriptor / GasDescriptor / EssentiaDescriptor
 */
public abstract class AbstractStorageAdapter<T extends IAEStack<T>, D extends Descriptor>
        implements IMEMonitor<T>, IMEInventoryHandler<T> {

    protected final Map<D, BigInteger> storage = new ConcurrentHashMap<>();
    protected IStorageChannel<T> channel;
    protected final HyperdimensionalStorageFile file;
    protected final List<IMEMonitorHandlerReceiver<T>> listeners = new CopyOnWriteArrayList<>();
    protected final AtomicReference<BigInteger> totalCount = new AtomicReference<>(BigInteger.ZERO);
    protected Runnable onChangeCallback = null;
    protected BiConsumer<T, IActionSource> postChangeCallback = null;

    protected AbstractStorageAdapter(HyperdimensionalStorageFile file) {
        this.file = file;
    }

    // ---- 子类必须实现 ----

    /**
     * 从输入 AE 堆叠构造描述符,用于 Map 的 Key.
     */
    protected abstract D createDescriptor(T input);

    /**
     * 从请求堆叠和实际提取量构造结果堆叠.
     * 实现方式因类型而异：
     * - 物品/流体：通常通过 channel.createStack(...) 创建后设置 size
     * - 气体/源质：通常直接 request.copy() 后设置 size
     */
    protected abstract T createResult(T request, BigInteger amount);

    /**
     * 从描述符获取缓存的 AE 模板(stackSize=1).
     */
    protected abstract T getAETemplate(D descriptor);

    @Override
    public abstract IStorageChannel<T> getChannel();

    // ---- 核心存储操作(通用实现) ----

    @Override
    public T injectItems(T input, Actionable type, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;
        if (file != null && file.isSafeMode()) {
            return input; // 安全模式：拒绝写入
        }
        D key = createDescriptor(input);
        BigInteger amount = BigInteger.valueOf(input.getStackSize());

        if (type == Actionable.MODULATE) {
            storage.merge(key, amount, BigInteger::add);
            totalCount.updateAndGet(t -> t.add(amount));
            file.markDirty();
            notifyPostChange(input.copy(), src);
            return null; // 无限容量,全部接受
        }
        // SIMULATE: 无限容量,全部接受
        return null;
    }

    @Override
    public T extractItems(T request, Actionable type, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;
        D key = createDescriptor(request);
        BigInteger requested = BigInteger.valueOf(request.getStackSize());
        BigInteger toExtract;

        if (type == Actionable.MODULATE) {
            final BigInteger[] extracted = new BigInteger[1];
            storage.compute(key, (k, available) -> {
                BigInteger avail = available == null ? BigInteger.ZERO : available;
                BigInteger extract = avail.min(requested);
                if (extract.signum() <= 0) {
                    return available;
                }
                extracted[0] = extract;
                BigInteger remaining = avail.subtract(extract);
                return remaining.signum() <= 0 ? null : remaining;
            });
            toExtract = extracted[0];
            if (toExtract == null) {
                return null;
            }
            totalCount.updateAndGet(t -> t.subtract(toExtract));
            file.markDirty();
            T change = request.copy();
            change.setStackSize(-toExtract.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
            notifyPostChange(change, src);
        } else {
            BigInteger available = storage.getOrDefault(key, BigInteger.ZERO);
            toExtract = available.min(requested);
            if (toExtract.signum() <= 0) {
                return null;
            }
        }

        return createResult(request, toExtract);
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.info("[AE2E-DIAG] AbstractStorageAdapter.getAvailableItems: storageSize={}, class={}", storage.size(), this.getClass().getSimpleName());
        int added = 0;
        for (Map.Entry<D, BigInteger> entry : storage.entrySet()) {
            D desc = entry.getKey();
            T aeStack = getAETemplate(desc);
            if (aeStack == null) continue;

            BigInteger count = entry.getValue();
            T copy = aeStack.copy();
            if (count.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                copy.setStackSize(Long.MAX_VALUE);
            } else {
                copy.setStackSize(count.longValue());
            }
            out.add(copy);
            added++;
        }
        com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.info("[AE2E-DIAG] AbstractStorageAdapter.getAvailableItems done: added={}, class={}", added, this.getClass().getSimpleName());
        return out;
    }

    // ---- 状态查询(通用实现) ----

    protected void recalcTotal() {
        BigInteger sum = BigInteger.ZERO;
        for (BigInteger v : storage.values()) {
            sum = sum.add(v);
        }
        totalCount.set(sum);
    }

    public Map<D, BigInteger> getStorageMap() {
        return storage;
    }

    public BigInteger getTotalCount() {
        return totalCount.get();
    }

    public boolean isSafeMode() {
        return file != null && file.isSafeMode();
    }

    public HyperdimensionalStorageFile getFile() {
        return file;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(T input) {
        return false;
    }

    @Override
    public boolean canAccept(T input) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    @Override
    public IItemList<T> getStorageList() {
        IItemList<T> list = channel.createList();
        return getAvailableItems(list);
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<T> l, Object verificationToken) {
        listeners.add(l);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<T> l) {
        listeners.remove(l);
    }

    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    public void setPostChangeCallback(BiConsumer<T, IActionSource> callback) {
        this.postChangeCallback = callback;
    }

    // ---- 内部辅助 ----

    protected void notifyPostChange(T change, IActionSource src) {
        if (listeners.isEmpty() && onChangeCallback == null && postChangeCallback == null) return;
        if (!listeners.isEmpty()) {
            List<T> changes = java.util.Collections.singletonList(change);
            for (IMEMonitorHandlerReceiver<T> listener : listeners) {
                listener.postChange(this, changes, src);
            }
        }
        if (postChangeCallback != null) {
            postChangeCallback.accept(change, src);
        }
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }
}

package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 超维度仓储中枢的外部文件持久化层。
 * 每个结构对应一个独立文件，数据不写入 NBT/WorldSavedData。
 *
 * 文件格式（压缩 NBT）：
 * {
 *   version: 1 (int)
 *   nexusId: UUID (long[])
 *   items: NBTTagList { ... }
 *   fluids: NBTTagList { ... }
 *   gases: NBTTagList { ... }
 *   essentias: NBTTagList { ... }
 * }
 */
public class HyperdimensionalStorageFile {

    public static final int CURRENT_VERSION = 1;

    private static final ScheduledExecutorService FLUSH_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AE2E-Storage-Flush");
            t.setDaemon(true);
            return t;
        });

    private final File file;
    private final UUID nexusId;
    private final ScheduledFuture<?> flushTask;
    private volatile boolean dirty = false;
    private volatile boolean closed = false;
    private volatile boolean safeMode = false;
    private volatile Map<ItemDescriptor, BigInteger> storageRef = null;
    private volatile Map<FluidDescriptor, BigInteger> fluidStorageRef = null;
    private volatile Map<?, BigInteger> gasStorageRef = null;
    private volatile Map<?, BigInteger> essentiaStorageRef = null;
    private volatile Map<EnergyDescriptor, BigInteger> energyStorageRef = null;

    private final StorageSection<ItemDescriptor> itemSection =
            new StorageSection<>("items", ItemDescriptor::fromNBT);
    private final StorageSection<FluidDescriptor> fluidSection =
            new StorageSection<>("fluids", FluidDescriptor::fromNBT);
    private final StorageSection<EnergyDescriptor> energySection =
            new StorageSection<>("energy", tag -> EnergyDescriptor.fromNBT(tag));
    private StorageSection<Descriptor> gasSection = null;
    private StorageSection<Descriptor> essentiaSection = null;

    /** 初始化期间缓存读取的 NBT 根节点，避免多个 load 方法重复读取同一文件 */
    private NBTTagCompound loadCache = null;

    public HyperdimensionalStorageFile(World world, UUID nexusId) {
        this.nexusId = nexusId;
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            AE2Enhanced.LOGGER.warn("Failed to create storage directory: {}", storageDir.getAbsolutePath());
        }
        this.file = new File(storageDir, nexusId.toString() + ".dat");
        int flushInterval = AE2EnhancedConfig.storage.flushIntervalSeconds;
        this.flushTask = FLUSH_EXECUTOR.scheduleWithFixedDelay(this::flush, flushInterval, flushInterval, TimeUnit.SECONDS);
        initConditionalSections();
    }

    @SuppressWarnings("unchecked")
    private void initConditionalSections() {
        // GasDescriptor / EssentiaDescriptor 类中硬引用了可选 Mod 的类（MekanismEnergistics /
        // ThaumicEnergistics）。当对应 Mod 未安装时，Class.forName 在链接阶段会抛出
        // NoClassDefFoundError（Error 子类，而非 Exception），必须用 catch (Throwable) 捕获。
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.storage.GasDescriptor");
            java.lang.reflect.Method fromNbt = clazz.getMethod("fromNBT", NBTTagCompound.class);
            this.gasSection = new StorageSection<>("gases", tag -> {
                try {
                    return (Descriptor) fromNbt.invoke(null, tag);
                } catch (Exception e) {
                    return null;
                }
            });
        } catch (Throwable e) {
            this.gasSection = null;
        }
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.storage.EssentiaDescriptor");
            java.lang.reflect.Method fromNbt = clazz.getMethod("fromNBT", NBTTagCompound.class);
            this.essentiaSection = new StorageSection<>("essentias", tag -> {
                try {
                    return (Descriptor) fromNbt.invoke(null, tag);
                } catch (Exception e) {
                    return null;
                }
            });
        } catch (Throwable e) {
            this.essentiaSection = null;
        }
    }

    private NBTTagCompound readRoot() {
        if (loadCache != null) return loadCache;
        if (!file.exists()) return null;
        try {
            loadCache = CompressedStreamTools.read(file);
            return loadCache;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error(
                "[AE2E] Failed to read storage file: {}. Entering safe mode (read-only).", file.getAbsolutePath(), e);
            safeMode = true;
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadSection(StorageSection<?> section, Map<?, BigInteger> target, String typeName) {
        if (section == null || target == null) return;
        NBTTagCompound root = readRoot();
        if (root == null) return;
        try {
            int version = root.getInteger("version");
            if (version > CURRENT_VERSION) {
                AE2Enhanced.LOGGER.error(
                    "[AE2E] Storage file version {} > current {}. Refusing to load to prevent data corruption.",
                    version, CURRENT_VERSION);
                return;
            }
            ((StorageSection<Descriptor>) section).load(root, (Map<Descriptor, BigInteger>) target);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error(
                "[AE2E] Failed to load {} storage from file: {}. Entering safe mode (read-only).", typeName, file.getAbsolutePath(), e);
            safeMode = true;
        }
    }

    public void load(Map<ItemDescriptor, BigInteger> target) {
        loadSection(itemSection, target, "item");
    }

    public void loadFluids(Map<FluidDescriptor, BigInteger> target) {
        loadSection(fluidSection, target, "fluid");
    }

    public void loadGases(Map<?, BigInteger> target) {
        loadSection(gasSection, target, "gas");
    }

    public void loadEssentias(Map<?, BigInteger> target) {
        loadSection(essentiaSection, target, "essentia");
    }

    public void loadEnergy(Map<EnergyDescriptor, BigInteger> target) {
        loadSection(energySection, target, "energy");
    }

    @SuppressWarnings("unchecked")
    public boolean save() {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("version", CURRENT_VERSION);
        root.setUniqueId("nexusId", nexusId);

        itemSection.save(root, storageRef);
        fluidSection.save(root, fluidStorageRef);
        if (gasSection != null && gasStorageRef != null) {
            ((StorageSection<Descriptor>) gasSection).save(root, (Map<Descriptor, BigInteger>) gasStorageRef);
        }
        if (essentiaSection != null && essentiaStorageRef != null) {
            ((StorageSection<Descriptor>) essentiaSection).save(root, (Map<Descriptor, BigInteger>) essentiaStorageRef);
        }
        if (energyStorageRef != null) {
            energySection.save(root, energyStorageRef);
        }

        File tmpFile = new File(file.getAbsolutePath() + ".tmp");
        try {
            CompressedStreamTools.write(root, tmpFile);
            Files.move(tmpFile.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            AE2Enhanced.LOGGER.error(
                "[AE2E] Failed to save storage file: {}. Entering safe mode (read-only).", file.getAbsolutePath(), e);
            safeMode = true;
            return false;
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void setStorageRef(Map<ItemDescriptor, BigInteger> ref) {
        this.storageRef = ref;
    }

    public void setFluidStorageRef(Map<FluidDescriptor, BigInteger> ref) {
        this.fluidStorageRef = ref;
    }

    public void setGasStorageRef(Map<?, BigInteger> ref) {
        this.gasStorageRef = ref;
    }

    public void setEssentiaStorageRef(Map<?, BigInteger> ref) {
        this.essentiaStorageRef = ref;
    }

    public void setEnergyStorageRef(Map<EnergyDescriptor, BigInteger> ref) {
        this.energyStorageRef = ref;
    }

    private void flush() {
        if (!dirty || closed) return;
        if (save()) {
            dirty = false;
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (flushTask != null) {
            flushTask.cancel(false);
        }
        save();
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isSafeMode() {
        return safeMode;
    }

    public void setSafeMode(boolean safeMode) {
        this.safeMode = safeMode;
    }

    public UUID getNexusId() {
        return nexusId;
    }
}

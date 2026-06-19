package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import ae2.api.parts.IPart;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.util.AEPartLocation;
import ae2.helpers.InterfaceLogic;
import ae2.parts.misc.InterfacePart;
import ae2.tile.misc.TileInterface;
import ae2.tile.networking.TileCableBus;
import ae2.util.ConfigInventory;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 智能样板文件的垃圾回收器。
 *
 * <p>复合策略：</p>
 * <ul>
 *   <li>基础：文件最后访问时间过期（{@link SmartPatternStorageFile#load} 时更新 mtime）</li>
 *   <li>白名单：扫描所有加载的 ME 接口（AE2S 原版、本模组中枢接口），
 *       其中存放的 {@link ItemSmartPattern} 对应的 UUID 不受过期删除影响</li>
 * </ul>
 *
 * <p>扫描周期和过期天数由 {@link AE2EnhancedConfig.SmartPattern} 配置。</p>
 */
public class SmartPatternGarbageCollector {

    private static final String AE2FC_DUAL_TILE = "com.glodblock.github.common.tile.TileDualInterface";
    private static final String AE2FC_TRIO_TILE = "com.glodblock.github.common.tile.TileTrioInterface";
    private static final String AE2FC_DUAL_PART = "com.glodblock.github.common.part.PartDualInterface";
    private static final String AE2FC_TRIO_PART = "com.glodblock.github.common.part.PartTrioInterface";

    // ae2fc 反射缓存（可能不存在，延迟初始化）
    private static Class<?> ae2fcDualTileClass;
    private static Class<?> ae2fcTrioTileClass;
    private static Class<?> ae2fcDualPartClass;
    private static Class<?> ae2fcTrioPartClass;
    private static Method ae2fcGetLogicMethod;
    private static Method ae2fcGetConfigMethod;
    private static boolean ae2fcReflected = false;

    private int tickCounter = 0;
    private int lastIntervalMinutes = -1;

    public static void init() {
        if (AE2EnhancedConfig.smartPattern.gcIntervalMinutes > 0) {
            MinecraftForge.EVENT_BUS.register(new SmartPatternGarbageCollector());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int interval = AE2EnhancedConfig.smartPattern.gcIntervalMinutes;
        if (interval <= 0) {
            return;
        }
        // 配置热修改时重置计数器
        if (interval != lastIntervalMinutes) {
            lastIntervalMinutes = interval;
            tickCounter = 0;
        }
        tickCounter++;
        int intervalTicks = interval * 60 * 20; // minutes -> ticks
        if (tickCounter >= intervalTicks) {
            tickCounter = 0;
            runGC();
        }
    }

    private void runGC() {
        runManualGC();
    }

    /**
     * 手动触发一次垃圾回收，返回删除的文件数量。
     */
    public static int runManualGC() {
        try {
            Set<UUID> whitelist = collectReferencedIds();
            int deleted = deleteOrphanedFiles(whitelist);
            if (deleted > 0) {
                AE2Enhanced.LOGGER.info("[AE2E] SmartPattern GC completed: {} orphaned file(s) deleted.", deleted);
            }
            return deleted;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] SmartPattern GC failed.", e);
            return -1;
        }
    }

    /**
     * 遍历所有已加载维度的 TileEntity，收集 ME 接口中存放的智能样板 UUID。
     */
    @Nonnull
    private static Set<UUID> collectReferencedIds() {
        Set<UUID> ids = new HashSet<>();
        for (WorldServer world : FMLCommonHandler.instance().getMinecraftServerInstance().worlds) {
            for (TileEntity te : world.loadedTileEntityList) {
                if (te == null || te.isInvalid()) {
                    continue;
                }
                // 1. AE2S 原版 TileInterface
                if (te instanceof TileInterface) {
                    scanConfigInventory(ids, ((TileInterface) te).getInterfaceLogic());
                    continue;
                }
                // 2. AE2S 原版 TileCableBus（扫描其中的 Part）
                if (te instanceof TileCableBus) {
                    scanCableBus(ids, (TileCableBus) te);
                    continue;
                }
                // 3. 本模组 TileSmartPatternInterface
                if (te instanceof TileSmartPatternInterface) {
                    scanHandler(ids, ((TileSmartPatternInterface) te).getInventory());
                    continue;
                }
                // 4. ae2fc TileDualInterface / TileTrioInterface（反射）
                if (isAe2fcTileInterface(te)) {
                    scanAe2fcInterface(ids, te);
                }
            }
        }
        return ids;
    }

    private static void scanCableBus(@Nonnull Set<UUID> ids, @Nonnull TileCableBus cableBus) {
        for (EnumFacing side : EnumFacing.values()) {
            IPart part = cableBus.getPart(side);
            if (part == null) {
                continue;
            }
            // AE2S 原版 InterfacePart
            if (part instanceof InterfacePart) {
                scanConfigInventory(ids, ((InterfacePart) part).getInterfaceLogic());
                continue;
            }
            // ae2fc PartDualInterface / PartTrioInterface（反射）
            if (isAe2fcPartInterface(part)) {
                scanAe2fcPart(ids, part);
            }
        }
    }

    private static void scanConfigInventory(@Nonnull Set<UUID> ids, @Nonnull InterfaceLogic logic) {
        ConfigInventory config = logic.getConfig();
        scanConfigInventory(ids, config);
    }

    private static void scanConfigInventory(@Nonnull Set<UUID> ids, @Nullable ConfigInventory config) {
        if (config == null) return;
        for (int i = 0; i < config.size(); i++) {
            GenericStack stack = config.getStack(i);
            if (stack == null || !(stack.what() instanceof AEItemKey)) {
                continue;
            }
            ItemStack itemStack = ((AEItemKey) stack.what()).toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE));
            addPatternId(ids, itemStack);
        }
    }

    private static void scanHandler(@Nonnull Set<UUID> ids, @Nonnull IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            addPatternId(ids, stack);
        }
    }

    private static void addPatternId(@Nonnull Set<UUID> ids, @Nonnull ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSmartPattern)) {
            return;
        }
        UUID uuid = ItemSmartPattern.getPatternDataId(stack);
        if (uuid != null) {
            ids.add(uuid);
        }
    }

    // ---- ae2fc 反射辅助 ----

    private static void initAe2fcReflection() {
        if (ae2fcReflected) {
            return;
        }
        try {
            ae2fcDualTileClass = Class.forName(AE2FC_DUAL_TILE);
            ae2fcTrioTileClass = Class.forName(AE2FC_TRIO_TILE);
            ae2fcDualPartClass = Class.forName(AE2FC_DUAL_PART);
            ae2fcTrioPartClass = Class.forName(AE2FC_TRIO_PART);
            ae2fcGetLogicMethod = ae2fcDualTileClass.getMethod("getInterfaceLogic");
            ae2fcGetConfigMethod = ae2fcGetLogicMethod.getReturnType().getMethod("getConfig");
        } catch (Exception ignored) {
            // ae2fc 未安装或类名/方法名不匹配
        }
        ae2fcReflected = true;
    }

    private static boolean isAe2fcTileInterface(@Nonnull TileEntity te) {
        initAe2fcReflection();
        Class<?> c = te.getClass();
        return (ae2fcDualTileClass != null && ae2fcDualTileClass.isAssignableFrom(c))
                || (ae2fcTrioTileClass != null && ae2fcTrioTileClass.isAssignableFrom(c));
    }

    private static boolean isAe2fcPartInterface(@Nonnull IPart part) {
        initAe2fcReflection();
        Class<?> c = part.getClass();
        return (ae2fcDualPartClass != null && ae2fcDualPartClass.isAssignableFrom(c))
                || (ae2fcTrioPartClass != null && ae2fcTrioPartClass.isAssignableFrom(c));
    }

    private static void scanAe2fcInterface(@Nonnull Set<UUID> ids, @Nonnull TileEntity te) {
        if (ae2fcGetLogicMethod == null || ae2fcGetConfigMethod == null) {
            return;
        }
        try {
            Object logic = ae2fcGetLogicMethod.invoke(te);
            if (logic == null) return;
            Object config = ae2fcGetConfigMethod.invoke(logic);
            if (config instanceof ConfigInventory) {
                scanConfigInventory(ids, (ConfigInventory) config);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to scan ae2fc interface at {}.", te.getPos(), e);
        }
    }

    private static void scanAe2fcPart(@Nonnull Set<UUID> ids, @Nonnull IPart part) {
        if (ae2fcGetLogicMethod == null || ae2fcGetConfigMethod == null) {
            return;
        }
        try {
            Object logic = ae2fcGetLogicMethod.invoke(part);
            if (logic == null) return;
            Object config = ae2fcGetConfigMethod.invoke(logic);
            if (config instanceof ConfigInventory) {
                scanConfigInventory(ids, (ConfigInventory) config);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to scan ae2fc part {}.", part.getClass().getName(), e);
        }
    }

    // ---- 文件删除 ----

    private static int deleteOrphanedFiles(@Nonnull Set<UUID> whitelist) {
        File storageDir = SmartPatternStorageFile.getStorageDir();
        if (storageDir == null || !storageDir.exists()) {
            return 0;
        }
        File[] files = storageDir.listFiles((dir, name) -> name.startsWith(SmartPatternStorageFile.FILE_PREFIX) && name.endsWith(".dat"));
        if (files == null || files.length == 0) {
            return 0;
        }
        // 相对过期：以最新 dat 文件的 mtime 为基准
        long latestMtime = 0;
        for (File file : files) {
            long mtime = file.lastModified();
            if (mtime > latestMtime) {
                latestMtime = mtime;
            }
        }
        if (latestMtime <= 0) {
            return 0;
        }
        long maxAgeMs = TimeUnit.DAYS.toMillis(AE2EnhancedConfig.smartPattern.gcMaxAgeDays);
        int deleted = 0;
        for (File file : files) {
            UUID uuid = parseUuidFromFileName(file.getName());
            if (uuid == null) continue;
            // 白名单保护
            if (whitelist.contains(uuid)) continue;
            // 相对过期检查：文件是否比最新的 dat 文件老 maxAgeDays 以上
            if (latestMtime - file.lastModified() < maxAgeMs) continue;
            if (file.delete()) {
                deleted++;
                AE2Enhanced.LOGGER.debug("[AE2E] Deleted orphaned SmartPattern file: {}", file.getName());
            }
        }
        return deleted;
    }

    @Nullable
    private static UUID parseUuidFromFileName(@Nonnull String name) {
        String prefix = SmartPatternStorageFile.FILE_PREFIX;
        if (!name.startsWith(prefix) || !name.endsWith(".dat")) {
            return null;
        }
        try {
            return UUID.fromString(name.substring(prefix.length(), name.length() - 4));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

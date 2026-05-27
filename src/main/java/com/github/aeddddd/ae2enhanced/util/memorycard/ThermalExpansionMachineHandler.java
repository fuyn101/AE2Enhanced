package com.github.aeddddd.ae2enhanced.util.memorycard;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thermal Expansion 机器的配置复制粘贴 Handler。
 * 通过 NBT 读写关键配置字段（Facing, SideCache, Level, Augments 等）。
 * Level 升级会消耗对应的 Upgrade Kit / Conversion Kit。
 *
 * 架构约定：
 * 1. paste() 采用"先统一验证（含 ME 网络回退），再原子执行"的流程。
 * 2. 任何消耗操作前必须确认所有物品（kit + augment）都已可用。
 * 3. augment 的 slot 数量必须在 level 确定后再计算，因此 level kit 与 augment 一起验证。
 */
public class ThermalExpansionMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Set<String> CONFIG_KEYS = new HashSet<>(Arrays.asList(
            "Facing", "SideCache", "Level", "RSControl",
            "EnableAutoInput", "EnableAutoOutput", "Mode"
    ));

    // Thermal Foundation 升级套件缓存（从静态字段直接引用，使用时必须 copy()）
    private static ItemStack[] UPGRADE_INCREMENTAL = null;
    private static ItemStack[] UPGRADE_FULL = null;

    static {
        AVAILABLE = Loader.isModLoaded("thermalexpansion");
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) return false;
        String className = target.getClass().getName();
        return className.startsWith("cofh.thermalexpansion.block.machine.")
                || className.startsWith("cofh.thermalexpansion.block.dynamo.")
                || className.startsWith("cofh.thermalexpansion.block.device.")
                || className.startsWith("cofh.thermalexpansion.block.apparatus.");
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            NBTTagCompound fullNbt = tile.writeToNBT(new NBTTagCompound());

            for (String key : CONFIG_KEYS) {
                if (fullNbt.hasKey(key)) {
                    output.setTag(key, fullNbt.getTag(key));
                }
            }

            if (fullNbt.hasKey("Augments")) {
                output.setTag("Augments", fullNbt.getTag("Augments"));
            }

            output.setString("dataType", target.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy TE machine config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            if (data.hasKey("dataType")) {
                String sourceType = data.getString("dataType");
                String targetType = target.getClass().getName();
                if (!isCompatible(sourceType, targetType)) {
                    return PasteResult.INVALID_MACHINE;
                }
            }

            int originalLevel = getCurrentLevel(tile);
            int targetLevel = data.hasKey("Level") ? data.getByte("Level") : originalLevel;

            // ===== 阶段 1：统一计算需要的所有物品 =====
            List<ItemStack> allNeeded = new ArrayList<>();

            // 1a. Level upgrade kit（如有）
            List<ItemStack> levelKits = getLevelKits(originalLevel, targetLevel);
            for (ItemStack kit : levelKits) {
                if (kit.isEmpty()) {
                    return PasteResult.MISSING_UPGRADES; // 某个 kit 类型不存在
                }
                allNeeded.add(kit);
            }

            // 1b. Augments（按目标 level 的 slot 数量截断）
            List<ItemStack> neededAugments = new ArrayList<>();
            if (data.hasKey("Augments")) {
                neededAugments = parseAugmentList(data.getTagList("Augments", 10));
                int augmentSlots = getNumAugmentSlots(tile, targetLevel);
                if (augmentSlots > 0 && neededAugments.size() > augmentSlots) {
                    neededAugments = neededAugments.subList(0, augmentSlots);
                }
                for (ItemStack aug : neededAugments) {
                    if (!aug.isEmpty()) {
                        allNeeded.add(aug.copy());
                    }
                }
            }

            // ===== 阶段 2：统一验证（含 ME 网络回退） =====
            if (!allNeeded.isEmpty() && !MemoryCardUpgradeHelper.ensureAvailable(player, allNeeded)) {
                return PasteResult.MISSING_UPGRADES;
            }

            // ===== 阶段 3：执行 Level upgrade =====
            if (!levelKits.isEmpty()) {
                for (ItemStack kit : levelKits) {
                    MemoryCardUpgradeHelper.consumeFromInventory(player, kit);
                }
                boolean ok = setLevel(tile, targetLevel);
                if (!ok) {
                    // 返还 kits（尽力而为）
                    for (ItemStack kit : levelKits) {
                        if (!player.addItemStackToInventory(kit.copy())) {
                            player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, kit.copy()));
                        }
                    }
                    return PasteResult.FAILED;
                }
                closeOpenGUIs(tile);
            }

            // ===== 阶段 4：执行 Augment =====
            if (!neededAugments.isEmpty()) {
                PasteResult augResult = applyAugments(tile, neededAugments, player);
                if (augResult != PasteResult.SUCCESS) {
                    // 回滚 level（kit 不返还，因为已经消耗且难以精确回滚到原 slot）
                    if (!levelKits.isEmpty()) {
                        setLevel(tile, originalLevel);
                        closeOpenGUIs(tile);
                    }
                    return augResult;
                }
            }

            // ===== 阶段 5：readFromNBT 恢复其余配置 =====
            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());
            for (String key : CONFIG_KEYS) {
                if (!key.equals("Level") && data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }
            if (data.hasKey("Augments")) {
                currentNbt.setTag("Augments", data.getTag("Augments"));
            }
            tile.readFromNBT(currentNbt);

            // 6. 更新 augment 状态
            invokeIfExists(tile, "updateAugmentStatus");

            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(), tile.getWorld().getBlockState(tile.getPos()), tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste TE machine config", e);
            return PasteResult.FAILED;
        }
    }

    /**
     * 根据当前 level 和目标 level，返回需要的 Conversion/Upgrade Kit 列表。
     * 优先使用 Full Conversion Kit；否则返回所有 Incremental Upgrade Kit。
     * 不执行任何消耗，仅做计算。
     * @return kit 列表，无需升级时返回空列表；某个 kit 类型不存在时列表中包含 EMPTY
     */
    private List<ItemStack> getLevelKits(int currentLevel, int targetLevel) {
        List<ItemStack> result = new ArrayList<>();
        if (targetLevel <= currentLevel) {
            return result;
        }
        initUpgradeKits();

        // 策略1：Full Conversion Kit（索引 = targetLevel - 1）
        int fullKitIndex = targetLevel - 1;
        if (UPGRADE_FULL != null && fullKitIndex >= 0 && fullKitIndex < UPGRADE_FULL.length) {
            ItemStack fullKit = UPGRADE_FULL[fullKitIndex];
            if (fullKit != null && !fullKit.isEmpty()) {
                result.add(fullKit.copy());
                return result;
            }
        }

        // 策略2：Incremental Upgrade Kit（需要多个）
        if (UPGRADE_INCREMENTAL != null) {
            for (int i = currentLevel; i < targetLevel; i++) {
                if (i < 0 || i >= UPGRADE_INCREMENTAL.length) continue;
                ItemStack incKit = UPGRADE_INCREMENTAL[i];
                if (incKit != null && !incKit.isEmpty()) {
                    result.add(incKit.copy());
                } else {
                    result.add(ItemStack.EMPTY); // 标记为缺失
                }
            }
        }
        return result;
    }

    private void initUpgradeKits() {
        if (UPGRADE_INCREMENTAL != null) return;
        try {
            Class<?> itemUpgradeClass = Class.forName("cofh.thermalfoundation.item.ItemUpgrade");
            Field incField = itemUpgradeClass.getDeclaredField("upgradeIncremental");
            incField.setAccessible(true);
            Field fullField = itemUpgradeClass.getDeclaredField("upgradeFull");
            fullField.setAccessible(true);
            UPGRADE_INCREMENTAL = (ItemStack[]) incField.get(null);
            UPGRADE_FULL = (ItemStack[]) fullField.get(null);
            AE2Enhanced.LOGGER.debug("[AE2E] Loaded TE upgrade kits: incremental={}, full={}",
                    UPGRADE_INCREMENTAL != null ? UPGRADE_INCREMENTAL.length : "null",
                    UPGRADE_FULL != null ? UPGRADE_FULL.length : "null");
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Could not load Thermal Foundation upgrade kits", e);
        }
    }

    private int getCurrentLevel(TileEntity tile) {
        try {
            Field levelField = findFieldInHierarchy(tile.getClass(), "level");
            if (levelField != null) {
                return levelField.getInt(tile);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not get TE level", e);
        }
        return 0;
    }

    private boolean setLevel(TileEntity tile, int level) {
        try {
            Method setLevel = findMethodInHierarchy(tile.getClass(), "setLevel", int.class);
            if (setLevel != null) {
                Object result = setLevel.invoke(tile, level);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
                return true;
            }
            // fallback: 直接写字段
            Field levelField = findFieldInHierarchy(tile.getClass(), "level");
            if (levelField != null) {
                levelField.setInt(tile, level);
                return true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Could not set TE level", e);
        }
        return false;
    }

    private List<ItemStack> parseAugmentList(NBTTagList sourceAugments) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < sourceAugments.tagCount(); i++) {
            NBTTagCompound tag = sourceAugments.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    private PasteResult applyAugments(TileEntity tile, List<ItemStack> neededAugments, EntityPlayer player) {
        try {
            if (neededAugments.isEmpty()) {
                return PasteResult.SUCCESS;
            }

            // 1. 弹出目标现有 augment
            ItemStack[] existingAugments = getAugmentsArray(tile);
            if (existingAugments != null) {
                for (ItemStack aug : existingAugments) {
                    if (aug != null && !aug.isEmpty()) {
                        if (!player.addItemStackToInventory(aug.copy())) {
                            player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, aug.copy()));
                        }
                    }
                }
            }

            // 2. 消耗 augment（ensureAvailable 已保证足够）
            for (ItemStack needed : neededAugments) {
                MemoryCardUpgradeHelper.consumeFromInventory(player, needed);
            }

            // 3. 设置 augment 数组
            if (existingAugments != null && existingAugments.length > 0) {
                Arrays.fill(existingAugments, ItemStack.EMPTY);
                for (int i = 0; i < neededAugments.size() && i < existingAugments.length; i++) {
                    existingAugments[i] = neededAugments.get(i).copy();
                }
                invokeIfExists(tile, "preAugmentInstall");
                invokeIfExists(tile, "postAugmentInstall");
            } else {
                NBTTagList augmentList = new NBTTagList();
                for (int i = 0; i < neededAugments.size(); i++) {
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setInteger("Slot", i);
                    neededAugments.get(i).writeToNBT(tag);
                    augmentList.appendTag(tag);
                }
                NBTTagCompound augmentNbt = new NBTTagCompound();
                augmentNbt.setTag("Augments", augmentList);
                Method readAugments = findMethodInHierarchy(tile.getClass(), "readAugmentsFromNBT", NBTTagCompound.class);
                if (readAugments != null) {
                    readAugments.invoke(tile, augmentNbt);
                }
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to apply TE augments", e);
            return PasteResult.FAILED;
        }
    }

    private ItemStack[] getAugmentsArray(TileEntity tile) {
        Class<?> current = tile.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals("augments") && field.getType().isArray()
                        && field.getType().getComponentType() == ItemStack.class) {
                    try {
                        return (ItemStack[]) field.get(tile);
                    } catch (IllegalAccessException e) {
                        return null;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void invokeIfExists(TileEntity tile, String methodName) {
        try {
            Method method = findMethodInHierarchy(tile.getClass(), methodName);
            if (method != null) {
                method.invoke(tile);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not invoke {} on {}", methodName, tile.getClass().getName());
        }
    }

    /**
     * 获取指定 level 下的 augment slot 数量。
     */
    private int getNumAugmentSlots(TileEntity tile, int level) {
        try {
            Method getNumAugmentSlots = findMethodInHierarchy(tile.getClass(), "getNumAugmentSlots", int.class);
            if (getNumAugmentSlots != null) {
                return (int) getNumAugmentSlots.invoke(tile, level);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not get TE augment slot count", e);
        }
        return 0;
    }

    private Method findMethodInHierarchy(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Method m = current.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Field findFieldInHierarchy(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 强制关闭所有与该 TileEntity 关联的 GUI。
     * TE 机器升级 level 后 augment slot 数量会变化，已打开的 Container 不会自动重建，
     * 若继续同步 SPacketWindowItems 会导致客户端 IndexOutOfBoundsException。
     */
    private void closeOpenGUIs(TileEntity tile) {
        if (tile.getWorld() == null) return;
        for (EntityPlayer p : tile.getWorld().playerEntities) {
            if (p.openContainer == null || p.openContainer == p.inventoryContainer) continue;
            for (Object slotObj : p.openContainer.inventorySlots) {
                if (slotObj instanceof net.minecraft.inventory.Slot) {
                    net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) slotObj;
                    if (slot.inventory == tile) {
                        p.closeScreen();
                        break;
                    }
                    try {
                        java.lang.reflect.Method m = slot.getClass().getMethod("getItemHandler");
                        Object handler = m.invoke(slot);
                        if (handler == tile) {
                            p.closeScreen();
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private boolean isCompatible(String sourceType, String targetType) {
        if (sourceType.equals(targetType)) return true;
        String sourceCategory = extractCategory(sourceType);
        String targetCategory = extractCategory(targetType);
        return sourceCategory != null && sourceCategory.equals(targetCategory);
    }

    private String extractCategory(String className) {
        if (className.contains(".block.machine.")) return "machine";
        if (className.contains(".block.dynamo.")) return "dynamo";
        if (className.contains(".block.device.")) return "device";
        if (className.contains(".block.apparatus.")) return "apparatus";
        return null;
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}

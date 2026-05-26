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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Thermal Expansion 机器的配置复制粘贴 Handler。
 * 通过 NBT 读写关键配置字段（Facing, SideCache, Level, Augments 等）。
 * Level 升级会消耗对应的 Upgrade Kit / Conversion Kit。
 */
public class ThermalExpansionMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Set<String> CONFIG_KEYS = new HashSet<>(Arrays.asList(
            "Facing", "SideCache", "Level", "RSControl",
            "EnableAutoInput", "EnableAutoOutput", "Mode"
    ));

    // Thermal Foundation 升级套件缓存
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

            // 1. 先处理 Level 升级（消耗转换套件）
            if (data.hasKey("Level")) {
                PasteResult levelResult = applyLevelUpgrade(tile, data.getByte("Level"), player);
                if (levelResult != PasteResult.SUCCESS) {
                    return levelResult;
                }
            }

            // 2. 获取当前完整 NBT（level 已更新）
            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());

            // 3. 覆盖配置键（不含 Level，已手动处理）
            for (String key : CONFIG_KEYS) {
                if (!key.equals("Level") && data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }

            // 4. 处理 augment（在 level 更新后，槽位数量正确）
            if (data.hasKey("Augments")) {
                PasteResult augmentResult = applyAugments(tile, data.getTagList("Augments", 10), player);
                if (augmentResult != PasteResult.SUCCESS) {
                    return augmentResult;
                }
                currentNbt.setTag("Augments", data.getTag("Augments"));
            }

            // 5. 调用 readFromNBT 恢复配置
            tile.readFromNBT(currentNbt);

            // 6. 更新 augment 状态
            try {
                Method updateAugmentStatus = tile.getClass().getMethod("updateAugmentStatus");
                updateAugmentStatus.invoke(tile);
            } catch (NoSuchMethodException e) {
                // 某些设备无此方法
            }

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
     * 处理 Level 升级：消耗 Upgrade Kit / Conversion Kit，然后设置 level。
     */
    private PasteResult applyLevelUpgrade(TileEntity tile, byte sourceLevel, EntityPlayer player) {
        try {
            int targetLevel = getCurrentLevel(tile);
            if (sourceLevel <= targetLevel) {
                return PasteResult.SUCCESS; // 无需升级或降级
            }

            initUpgradeKits();

            int levelsNeeded = sourceLevel - targetLevel;

            // 策略1：尝试使用 Full Conversion Kit 直接跳到目标 level
            if (UPGRADE_FULL != null && sourceLevel < UPGRADE_FULL.length) {
                ItemStack fullKit = UPGRADE_FULL[sourceLevel];
                if (fullKit != null && !fullKit.isEmpty()
                        && MemoryCardUpgradeHelper.countInInventory(player, fullKit) >= 1) {
                    MemoryCardUpgradeHelper.consumeFromInventory(player, fullKit);
                    setLevel(tile, sourceLevel);
                    return PasteResult.SUCCESS;
                }
            }

            // 策略2：使用 Incremental Upgrade Kit 逐级升级
            if (UPGRADE_INCREMENTAL != null) {
                for (int i = targetLevel; i < sourceLevel; i++) {
                    if (i < 0 || i >= UPGRADE_INCREMENTAL.length) continue;
                    ItemStack incKit = UPGRADE_INCREMENTAL[i];
                    if (incKit == null || incKit.isEmpty()) {
                        return PasteResult.MISSING_UPGRADES;
                    }
                    if (MemoryCardUpgradeHelper.countInInventory(player, incKit) < 1) {
                        return PasteResult.MISSING_UPGRADES;
                    }
                }
                // 消耗
                for (int i = targetLevel; i < sourceLevel; i++) {
                    if (i < 0 || i >= UPGRADE_INCREMENTAL.length) continue;
                    ItemStack incKit = UPGRADE_INCREMENTAL[i];
                    MemoryCardUpgradeHelper.consumeFromInventory(player, incKit);
                }
                setLevel(tile, sourceLevel);
                return PasteResult.SUCCESS;
            }

            return PasteResult.MISSING_UPGRADES;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to apply TE level upgrade", e);
            return PasteResult.FAILED;
        }
    }

    private void initUpgradeKits() {
        if (UPGRADE_INCREMENTAL != null) return;
        try {
            Class<?> itemUpgradeClass = Class.forName("cofh.thermalfoundation.item.ItemUpgrade");
            Field incField = itemUpgradeClass.getField("upgradeIncremental");
            Field fullField = itemUpgradeClass.getField("upgradeFull");
            UPGRADE_INCREMENTAL = (ItemStack[]) incField.get(null);
            UPGRADE_FULL = (ItemStack[]) fullField.get(null);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not load Thermal Foundation upgrade kits", e);
        }
    }

    private int getCurrentLevel(TileEntity tile) {
        try {
            Field levelField = findFieldInHierarchy(tile.getClass(), "level");
            if (levelField != null) {
                levelField.setAccessible(true);
                return levelField.getInt(tile);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not get TE level", e);
        }
        return 0;
    }

    private void setLevel(TileEntity tile, int level) {
        try {
            Method setLevel = findMethodInHierarchy(tile.getClass(), "setLevel", int.class);
            if (setLevel != null) {
                setLevel.setAccessible(true);
                setLevel.invoke(tile, level);
                return;
            }
            // fallback: 直接写字段
            Field levelField = findFieldInHierarchy(tile.getClass(), "level");
            if (levelField != null) {
                levelField.setAccessible(true);
                levelField.setInt(tile, level);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Could not set TE level", e);
        }
    }

    private PasteResult applyAugments(TileEntity tile, NBTTagList sourceAugments, EntityPlayer player) {
        try {
            java.util.List<ItemStack> neededAugments = new java.util.ArrayList<>();
            for (int i = 0; i < sourceAugments.tagCount(); i++) {
                NBTTagCompound tag = sourceAugments.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(tag);
                if (!stack.isEmpty()) {
                    neededAugments.add(stack);
                }
            }

            if (neededAugments.isEmpty()) {
                return PasteResult.SUCCESS;
            }

            int targetSlots = getNumAugmentSlots(tile);
            if (targetSlots > 0 && neededAugments.size() > targetSlots) {
                neededAugments = neededAugments.subList(0, targetSlots);
            }

            // 弹出目标现有 augment
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

            // 验证并消耗 augment
            for (ItemStack needed : neededAugments) {
                if (MemoryCardUpgradeHelper.countInInventory(player, needed) < needed.getCount()) {
                    return PasteResult.MISSING_UPGRADES;
                }
            }
            for (ItemStack needed : neededAugments) {
                MemoryCardUpgradeHelper.consumeFromInventory(player, needed);
            }

            // 设置 augment 数组
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
                    field.setAccessible(true);
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

    private int getNumAugmentSlots(TileEntity tile) {
        try {
            Method getNumAugmentSlots = findMethodInHierarchy(tile.getClass(), "getNumAugmentSlots", int.class);
            if (getNumAugmentSlots != null) {
                int level = getCurrentLevel(tile);
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
                return current.getDeclaredMethod(name, paramTypes);
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
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
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

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
 * 通过 NBT 读写关键配置字段（Facing, SideCache, Augments 等）。
 * 注意：不复制 Level，避免免费升级；Augment 通过反射直接操作字段。
 */
public class ThermalExpansionMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Set<String> CONFIG_KEYS = new HashSet<>(Arrays.asList(
            "Facing", "SideCache", "RSControl",
            "EnableAutoInput", "EnableAutoOutput", "Mode"
    ));

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

            // 复制配置键（不含 Level）
            for (String key : CONFIG_KEYS) {
                if (fullNbt.hasKey(key)) {
                    output.setTag(key, fullNbt.getTag(key));
                }
            }

            // 复制 Augments
            if (fullNbt.hasKey("Augments")) {
                output.setTag("Augments", fullNbt.getTag("Augments"));
            }

            // 复制 dataType 用于验证
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
            // 验证机器类型
            if (data.hasKey("dataType")) {
                String sourceType = data.getString("dataType");
                String targetType = target.getClass().getName();
                if (!isCompatible(sourceType, targetType)) {
                    return PasteResult.INVALID_MACHINE;
                }
            }

            // 获取当前完整 NBT
            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());

            // 覆盖配置键（不含 Level）
            for (String key : CONFIG_KEYS) {
                if (data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }

            // 处理 augment
            if (data.hasKey("Augments")) {
                PasteResult augmentResult = applyAugments(tile, data.getTagList("Augments", 10), player);
                if (augmentResult != PasteResult.SUCCESS) {
                    return augmentResult;
                }
                // 保留 Augments 在 currentNbt 中，让 readFromNBT 处理基类状态
                currentNbt.setTag("Augments", data.getTag("Augments"));
            }

            // 调用 readFromNBT 恢复配置（不含 Level）
            tile.readFromNBT(currentNbt);

            // 更新 augment 状态（如果存在该方法）
            try {
                Method updateAugmentStatus = tile.getClass().getMethod("updateAugmentStatus");
                updateAugmentStatus.invoke(tile);
            } catch (NoSuchMethodException e) {
                // 某些 TE 设备可能没有此方法
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
     * 手动处理 augment 的粘贴：
     * 1. 读取源 augment 列表
     * 2. 弹出目标现有 augment 并返还给玩家
     * 3. 验证并消耗玩家背包中的 augment
     * 4. 通过反射设置 augments 字段，并触发安装流程
     */
    private PasteResult applyAugments(TileEntity tile, NBTTagList sourceAugments, EntityPlayer player) {
        try {
            // 解析源 augment 列表
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

            // 获取 augment 槽位数
            int targetSlots = getNumAugmentSlots(tile);
            if (targetSlots > 0 && neededAugments.size() > targetSlots) {
                neededAugments = neededAugments.subList(0, targetSlots);
            }

            // 弹出目标现有 augment 并返还给玩家
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

            // 验证玩家是否有足够的 augment
            for (ItemStack needed : neededAugments) {
                if (MemoryCardUpgradeHelper.countInInventory(player, needed) < needed.getCount()) {
                    return PasteResult.MISSING_UPGRADES;
                }
            }

            // 消耗 augment
            for (ItemStack needed : neededAugments) {
                MemoryCardUpgradeHelper.consumeFromInventory(player, needed);
            }

            // 通过反射设置 augments 字段
            if (existingAugments != null && existingAugments.length > 0) {
                // 清空现有 augment 数组
                Arrays.fill(existingAugments, ItemStack.EMPTY);

                // 放入新的 augment
                for (int i = 0; i < neededAugments.size() && i < existingAugments.length; i++) {
                    existingAugments[i] = neededAugments.get(i).copy();
                }

                // 触发 augment 安装流程
                invokeIfExists(tile, "preAugmentInstall");
                // installAugmentToSlot 通常在循环中调用，但直接设置数组后
                // 调用 postAugmentInstall 应该足够
                invokeIfExists(tile, "postAugmentInstall");
            } else {
                // 回退：尝试 readAugmentsFromNBT
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
                int level = 0;
                try {
                    Field levelField = findFieldInHierarchy(tile.getClass(), "level");
                    if (levelField != null) {
                        levelField.setAccessible(true);
                        level = levelField.getInt(tile);
                    }
                } catch (Exception ignored) {}
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

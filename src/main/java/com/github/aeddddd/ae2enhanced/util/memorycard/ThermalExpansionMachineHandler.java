package com.github.aeddddd.ae2enhanced.util.memorycard;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Thermal Expansion 机器的配置复制粘贴 Handler。
 * 通过 NBT 读写关键配置字段（Facing, SideCache, Level, Augments 等）。
 */
public class ThermalExpansionMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Set<String> CONFIG_KEYS = new HashSet<>(Arrays.asList(
            "Facing", "SideCache", "Level", "Augments", "RSControl",
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

            // 复制配置键
            for (String key : CONFIG_KEYS) {
                if (fullNbt.hasKey(key)) {
                    output.setTag(key, fullNbt.getTag(key));
                }
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

            // 覆盖配置键
            for (String key : CONFIG_KEYS) {
                if (data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }

            // 处理 augment 的消耗与验证
            if (data.hasKey("Augments")) {
                PasteResult augmentResult = applyAugments(tile, data.getTagList("Augments", 10), player);
                if (augmentResult != PasteResult.SUCCESS) {
                    return augmentResult;
                }
                // 清空 currentNbt 中的 Augments，因为我们手动处理了
                currentNbt.removeTag("Augments");
            }

            // 调用 readFromNBT 恢复配置
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
     * 2. 验证每个 augment 是否适合目标机器
     * 3. 从玩家背包/网络获取缺少的 augment
     * 4. 调用 readAugmentsFromNBT 设置 augment
     */
    private PasteResult applyAugments(TileEntity tile, NBTTagList sourceAugments, EntityPlayer player) {
        try {
            // 获取目标机器的 augment 槽位数量
            int targetSlots = getNumAugmentSlots(tile);
            if (targetSlots <= 0) {
                return PasteResult.SUCCESS; // 目标机器不支持 augment
            }

            // 计算需要的 augment
            java.util.List<ItemStack> neededAugments = new java.util.ArrayList<>();
            for (int i = 0; i < sourceAugments.tagCount(); i++) {
                NBTTagCompound tag = sourceAugments.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(tag);
                if (!stack.isEmpty()) {
                    neededAugments.add(stack);
                }
            }

            if (neededAugments.size() > targetSlots) {
                // 源机器 augment 数量超过目标机器槽位
                // 截取前 targetSlots 个
                neededAugments = neededAugments.subList(0, targetSlots);
            }

            // 验证玩家是否有足够的 augment
            for (ItemStack needed : neededAugments) {
                if (MemoryCardUpgradeHelper.countInInventory(player, needed) < needed.getCount()) {
                    // TODO: 尝试从网络拉取
                    return PasteResult.MISSING_UPGRADES;
                }
            }

            // 消耗 augment
            for (ItemStack needed : neededAugments) {
                MemoryCardUpgradeHelper.consumeFromInventory(player, needed);
            }

            // 构建 augment NBT 列表
            NBTTagList augmentList = new NBTTagList();
            for (int i = 0; i < neededAugments.size(); i++) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Slot", i);
                neededAugments.get(i).writeToNBT(tag);
                augmentList.appendTag(tag);
            }

            NBTTagCompound augmentNbt = new NBTTagCompound();
            augmentNbt.setTag("Augments", augmentList);

            // 调用 readAugmentsFromNBT
            Method readAugments = findMethodInHierarchy(tile.getClass(), "readAugmentsFromNBT", NBTTagCompound.class);
            if (readAugments != null) {
                readAugments.invoke(tile, augmentNbt);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to apply TE augments", e);
            return PasteResult.FAILED;
        }
    }

    private int getNumAugmentSlots(TileEntity tile) {
        try {
            // 尝试调用 getNumAugmentSlots(int level)
            Method getNumAugmentSlots = findMethodInHierarchy(tile.getClass(), "getNumAugmentSlots", int.class);
            if (getNumAugmentSlots != null) {
                // 获取当前 level
                int level = 0;
                try {
                    java.lang.reflect.Field levelField = findFieldInHierarchy(tile.getClass(), "level");
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

    private java.lang.reflect.Field findFieldInHierarchy(Class<?> clazz, String name) {
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

    /**
     * 验证源机器和目标机器是否兼容。
     * 允许同一大类（如所有 machine）之间的粘贴，但不同类型（如 machine 和 dynamo）之间不允许。
     */
    private boolean isCompatible(String sourceType, String targetType) {
        if (sourceType.equals(targetType)) return true;

        // 提取大类（machine/dynamo/device/apparatus）
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

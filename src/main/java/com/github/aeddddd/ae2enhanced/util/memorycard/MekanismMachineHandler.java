package com.github.aeddddd.ae2enhanced.util.memorycard;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mekanism 机器的配置复制粘贴 Handler。
 * 使用反射访问 Mekanism API，避免硬依赖。
 */
public class MekanismMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Class<?> CONFIG_CARD_CAPABILITY_CLASS;
    private static final Class<?> SPECIAL_CONFIG_DATA_CLASS;
    private static Object CONFIG_CARD_CAPABILITY;
    private static Object SPECIAL_CONFIG_DATA_CAPABILITY;

    private static final Class<?> REDSTONE_CONTROL_CLASS;
    private static final Method REDSTONE_GET_CONTROL_TYPE;
    private static final Method REDSTONE_SET_CONTROL_TYPE;
    private static final Object[] REDSTONE_CONTROL_VALUES;

    private static final Class<?> SIDE_CONFIGURATION_CLASS;
    private static final Method SIDE_CONFIG_GET_CONFIG;
    private static final Method SIDE_CONFIG_GET_EJECTOR;
    private static final Method SIDE_CONFIG_GET_ORIENTATION;

    private static final Class<?> TILE_COMPONENT_CONFIG_CLASS;
    private static final Method TILE_COMPONENT_CONFIG_WRITE;
    private static final Method TILE_COMPONENT_CONFIG_READ;

    private static final Class<?> TILE_COMPONENT_EJECTOR_CLASS;
    private static final Method TILE_COMPONENT_EJECTOR_WRITE;
    private static final Method TILE_COMPONENT_EJECTOR_READ;

    private static final Class<?> UPGRADE_TILE_CLASS;
    private static final Method UPGRADE_TILE_GET_COMPONENT;

    private static final Class<?> TILE_COMPONENT_UPGRADE_CLASS;
    private static final Method TILE_COMPONENT_UPGRADE_READ;
    private static final Method TILE_COMPONENT_UPGRADE_WRITE;
    private static final Method TILE_COMPONENT_UPGRADE_GET_UPGRADES;
    private static final Method TILE_COMPONENT_UPGRADE_ADD_UPGRADE;
    private static final Method TILE_COMPONENT_UPGRADE_REMOVE_UPGRADE;
    private static final Method TILE_COMPONENT_UPGRADE_GET_INSTALLED_TYPES;

    private static final Class<?> UPGRADE_CLASS;
    private static final Method UPGRADE_GET_STACK;
    private static final Method UPGRADE_SAVE_MAP;
    private static final Method UPGRADE_BUILD_MAP;

    private static final Class<?> CAPABILITY_UTILS_CLASS;
    private static final Method CAPABILITY_UTILS_HAS_CAPABILITY;
    private static final Method CAPABILITY_UTILS_GET_CAPABILITY;

    private static final Class<?> TILE_ENTITY_CONTAINER_BLOCK_CLASS;
    private static final Field CONTAINER_BLOCK_FULL_NAME;

    // Tier upgrade reflection
    private static final Class<?> TIER_UPGRADEABLE_CLASS;
    private static final Method TIER_UPGRADEABLE_UPGRADE;
    private static final Class<?> BASE_TIER_CLASS;
    private static final Object[] BASE_TIER_VALUES;
    private static final ItemStack[] TIER_INSTALLER_CACHE = new ItemStack[4];

    static {
        boolean available = false;
        Class<?> configCardCapabilityClass = null;
        Class<?> specialConfigDataClass = null;
        Object configCardCapability = null;
        Object specialConfigDataCapability = null;

        Class<?> redstoneControlClass = null;
        Method redstoneGetControlType = null;
        Method redstoneSetControlType = null;
        Object[] redstoneControlValues = null;

        Class<?> sideConfigurationClass = null;
        Method sideConfigGetConfig = null;
        Method sideConfigGetEjector = null;
        Method sideConfigGetOrientation = null;

        Class<?> tileComponentConfigClass = null;
        Method tileComponentConfigWrite = null;
        Method tileComponentConfigRead = null;

        Class<?> tileComponentEjectorClass = null;
        Method tileComponentEjectorWrite = null;
        Method tileComponentEjectorRead = null;

        Class<?> upgradeTileClass = null;
        Method upgradeTileGetComponent = null;

        Class<?> tileComponentUpgradeClass = null;
        Method tileComponentUpgradeRead = null;
        Method tileComponentUpgradeWrite = null;
        Method tileComponentUpgradeGetUpgrades = null;
        Method tileComponentUpgradeAddUpgrade = null;
        Method tileComponentUpgradeRemoveUpgrade = null;
        Method tileComponentUpgradeGetInstalledTypes = null;

        Class<?> upgradeClass = null;
        Method upgradeGetStack = null;
        Method upgradeSaveMap = null;
        Method upgradeBuildMap = null;

        Class<?> capabilityUtilsClass = null;
        Method capabilityUtilsHasCapability = null;
        Method capabilityUtilsGetCapability = null;

        Class<?> tileEntityContainerBlockClass = null;
        Field containerBlockFullName = null;

        Class<?> tierUpgradeableClass = null;
        Method tierUpgradeableUpgrade = null;
        Class<?> baseTierClass = null;
        Object[] baseTierValues = null;

        try {
            if (Loader.isModLoaded("mekanism")) {
                configCardCapabilityClass = Class.forName("mekanism.api.IConfigCardAccess");
                specialConfigDataClass = Class.forName("mekanism.api.IConfigCardAccess$ISpecialConfigData");

                Class<?> capabilitiesClass = Class.forName("mekanism.common.capabilities.Capabilities");
                configCardCapability = capabilitiesClass.getField("CONFIG_CARD_CAPABILITY").get(null);
                specialConfigDataCapability = capabilitiesClass.getField("SPECIAL_CONFIG_DATA_CAPABILITY").get(null);

                redstoneControlClass = Class.forName("mekanism.common.base.IRedstoneControl");
                redstoneGetControlType = redstoneControlClass.getMethod("getControlType");
                redstoneSetControlType = redstoneControlClass.getMethod("setControlType", redstoneGetControlType.getReturnType());
                redstoneControlValues = redstoneGetControlType.getReturnType().getEnumConstants();

                sideConfigurationClass = Class.forName("mekanism.common.base.ISideConfiguration");
                sideConfigGetConfig = sideConfigurationClass.getMethod("getConfig");
                sideConfigGetEjector = sideConfigurationClass.getMethod("getEjector");
                sideConfigGetOrientation = sideConfigurationClass.getMethod("getOrientation");

                tileComponentConfigClass = sideConfigGetConfig.getReturnType();
                tileComponentConfigWrite = tileComponentConfigClass.getMethod("write", NBTTagCompound.class);
                tileComponentConfigRead = tileComponentConfigClass.getMethod("read", NBTTagCompound.class);

                tileComponentEjectorClass = sideConfigGetEjector.getReturnType();
                tileComponentEjectorWrite = tileComponentEjectorClass.getMethod("write", NBTTagCompound.class);
                tileComponentEjectorRead = tileComponentEjectorClass.getMethod("read", NBTTagCompound.class);

                upgradeTileClass = Class.forName("mekanism.common.base.IUpgradeTile");
                upgradeTileGetComponent = upgradeTileClass.getMethod("getComponent");

                tileComponentUpgradeClass = upgradeTileGetComponent.getReturnType();
                tileComponentUpgradeRead = tileComponentUpgradeClass.getMethod("read", NBTTagCompound.class);
                tileComponentUpgradeWrite = tileComponentUpgradeClass.getMethod("write", NBTTagCompound.class);
                tileComponentUpgradeGetUpgrades = tileComponentUpgradeClass.getMethod("getUpgrades", Class.forName("mekanism.common.Upgrade"));
                tileComponentUpgradeAddUpgrade = tileComponentUpgradeClass.getMethod("addUpgrade", Class.forName("mekanism.common.Upgrade"));
                tileComponentUpgradeRemoveUpgrade = tileComponentUpgradeClass.getMethod("removeUpgrade", Class.forName("mekanism.common.Upgrade"));
                tileComponentUpgradeGetInstalledTypes = tileComponentUpgradeClass.getMethod("getInstalledTypes");

                upgradeClass = Class.forName("mekanism.common.Upgrade");
                upgradeGetStack = upgradeClass.getMethod("getStack");
                upgradeSaveMap = upgradeClass.getMethod("saveMap", Map.class, NBTTagCompound.class);
                upgradeBuildMap = upgradeClass.getMethod("buildMap", NBTTagCompound.class);

                capabilityUtilsClass = Class.forName("mekanism.common.util.CapabilityUtils");
                capabilityUtilsHasCapability = capabilityUtilsClass.getMethod("hasCapability", ICapabilityProvider.class, net.minecraftforge.common.capabilities.Capability.class, EnumFacing.class);
                capabilityUtilsGetCapability = capabilityUtilsClass.getMethod("getCapability", ICapabilityProvider.class, net.minecraftforge.common.capabilities.Capability.class, EnumFacing.class);

                tileEntityContainerBlockClass = Class.forName("mekanism.common.tile.prefab.TileEntityContainerBlock");
                containerBlockFullName = tileEntityContainerBlockClass.getField("fullName");

                tierUpgradeableClass = Class.forName("mekanism.common.base.ITierUpgradeable");
                tierUpgradeableUpgrade = tierUpgradeableClass.getMethod("upgrade", Class.forName("mekanism.common.tier.BaseTier"));
                baseTierClass = Class.forName("mekanism.common.tier.BaseTier");
                baseTierValues = baseTierClass.getEnumConstants();

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Mekanism reflection for UMC", e);
        }

        AVAILABLE = available;
        CONFIG_CARD_CAPABILITY_CLASS = configCardCapabilityClass;
        SPECIAL_CONFIG_DATA_CLASS = specialConfigDataClass;
        CONFIG_CARD_CAPABILITY = configCardCapability;
        SPECIAL_CONFIG_DATA_CAPABILITY = specialConfigDataCapability;

        REDSTONE_CONTROL_CLASS = redstoneControlClass;
        REDSTONE_GET_CONTROL_TYPE = redstoneGetControlType;
        REDSTONE_SET_CONTROL_TYPE = redstoneSetControlType;
        REDSTONE_CONTROL_VALUES = redstoneControlValues;

        SIDE_CONFIGURATION_CLASS = sideConfigurationClass;
        SIDE_CONFIG_GET_CONFIG = sideConfigGetConfig;
        SIDE_CONFIG_GET_EJECTOR = sideConfigGetEjector;
        SIDE_CONFIG_GET_ORIENTATION = sideConfigGetOrientation;

        TILE_COMPONENT_CONFIG_CLASS = tileComponentConfigClass;
        TILE_COMPONENT_CONFIG_WRITE = tileComponentConfigWrite;
        TILE_COMPONENT_CONFIG_READ = tileComponentConfigRead;

        TILE_COMPONENT_EJECTOR_CLASS = tileComponentEjectorClass;
        TILE_COMPONENT_EJECTOR_WRITE = tileComponentEjectorWrite;
        TILE_COMPONENT_EJECTOR_READ = tileComponentEjectorRead;

        UPGRADE_TILE_CLASS = upgradeTileClass;
        UPGRADE_TILE_GET_COMPONENT = upgradeTileGetComponent;

        TILE_COMPONENT_UPGRADE_CLASS = tileComponentUpgradeClass;
        TILE_COMPONENT_UPGRADE_READ = tileComponentUpgradeRead;
        TILE_COMPONENT_UPGRADE_WRITE = tileComponentUpgradeWrite;
        TILE_COMPONENT_UPGRADE_GET_UPGRADES = tileComponentUpgradeGetUpgrades;
        TILE_COMPONENT_UPGRADE_ADD_UPGRADE = tileComponentUpgradeAddUpgrade;
        TILE_COMPONENT_UPGRADE_REMOVE_UPGRADE = tileComponentUpgradeRemoveUpgrade;
        TILE_COMPONENT_UPGRADE_GET_INSTALLED_TYPES = tileComponentUpgradeGetInstalledTypes;

        UPGRADE_CLASS = upgradeClass;
        UPGRADE_GET_STACK = upgradeGetStack;
        UPGRADE_SAVE_MAP = upgradeSaveMap;
        UPGRADE_BUILD_MAP = upgradeBuildMap;

        CAPABILITY_UTILS_CLASS = capabilityUtilsClass;
        CAPABILITY_UTILS_HAS_CAPABILITY = capabilityUtilsHasCapability;
        CAPABILITY_UTILS_GET_CAPABILITY = capabilityUtilsGetCapability;

        TILE_ENTITY_CONTAINER_BLOCK_CLASS = tileEntityContainerBlockClass;
        CONTAINER_BLOCK_FULL_NAME = containerBlockFullName;

        TIER_UPGRADEABLE_CLASS = tierUpgradeableClass;
        TIER_UPGRADEABLE_UPGRADE = tierUpgradeableUpgrade;
        BASE_TIER_CLASS = baseTierClass;
        BASE_TIER_VALUES = baseTierValues;
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) return false;
        try {
            // 延迟获取 capability（Forge @CapabilityInject 可能在 init 之后才注入）
            if (CONFIG_CARD_CAPABILITY == null) {
                Class<?> capabilitiesClass = Class.forName("mekanism.common.capabilities.Capabilities");
                CONFIG_CARD_CAPABILITY = capabilitiesClass.getField("CONFIG_CARD_CAPABILITY").get(null);
            }
            if (CONFIG_CARD_CAPABILITY == null) {
                // 回退：按类名前缀匹配
                return target.getClass().getName().startsWith("mekanism.common.tile.");
            }
            // 尝试 null facing 和所有 6 个面
            for (EnumFacing face : EnumFacing.values()) {
                if ((Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, target, CONFIG_CARD_CAPABILITY, face)) {
                    return true;
                }
            }
            return (Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, target, CONFIG_CARD_CAPABILITY, null);
        } catch (Exception e) {
            return target.getClass().getName().startsWith("mekanism.common.tile.");
        }
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            // 1. 红石控制
            if (REDSTONE_CONTROL_CLASS.isInstance(tile)) {
                Object controlType = REDSTONE_GET_CONTROL_TYPE.invoke(tile);
                output.setInteger("controlType", ((Enum<?>) controlType).ordinal()); // enum ordinal
            }

            // 2. 侧面配置
            if (SIDE_CONFIGURATION_CLASS.isInstance(tile)) {
                Object config = SIDE_CONFIG_GET_CONFIG.invoke(tile);
                Object ejector = SIDE_CONFIG_GET_EJECTOR.invoke(tile);
                TILE_COMPONENT_CONFIG_WRITE.invoke(config, output);
                TILE_COMPONENT_EJECTOR_WRITE.invoke(ejector, output);
            }

            // 3. 特殊配置数据
            if ((Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null)) {
                Object special = CAPABILITY_UTILS_GET_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null);
                Method getConfigurationData = SPECIAL_CONFIG_DATA_CLASS.getMethod("getConfigurationData", NBTTagCompound.class);
                NBTTagCompound specialData = (NBTTagCompound) getConfigurationData.invoke(special, new NBTTagCompound());
                if (specialData != null) {
                    output.merge(specialData);
                }

                Method getDataType = SPECIAL_CONFIG_DATA_CLASS.getMethod("getDataType");
                String dataType = (String) getDataType.invoke(special);
                output.setString("dataType", dataType);
            } else {
                // 没有特殊配置数据，使用 block registry name + fullName
                if (TILE_ENTITY_CONTAINER_BLOCK_CLASS.isInstance(tile)) {
                    String fullName = (String) CONTAINER_BLOCK_FULL_NAME.get(tile);
                    String blockName = tile.getWorld().getBlockState(tile.getPos()).getBlock().getRegistryName().toString();
                    output.setString("dataType", blockName + "." + fullName + ".name");
                }
            }

            // 4. 升级
            if (UPGRADE_TILE_CLASS.isInstance(tile)) {
                Object component = UPGRADE_TILE_GET_COMPONENT.invoke(tile);
                NBTTagCompound upgradeNbt = new NBTTagCompound();
                TILE_COMPONENT_UPGRADE_WRITE.invoke(component, upgradeNbt);
                if (!upgradeNbt.isEmpty()) {
                    output.setTag("mekanism:upgrades", upgradeNbt);

                    // 同时存储 ae2e:upgrades 格式，供 MISSING_UPGRADES 消息显示
                    NBTTagList ae2eUpgrades = new NBTTagList();
                    @SuppressWarnings("unchecked")
                    Map<Object, Integer> upgrades = (Map<Object, Integer>) UPGRADE_BUILD_MAP.invoke(null, upgradeNbt);
                    for (Map.Entry<Object, Integer> entry : upgrades.entrySet()) {
                        ItemStack upgStack = ((ItemStack) UPGRADE_GET_STACK.invoke(entry.getKey())).copy();
                        upgStack.setCount(entry.getValue());
                        ae2eUpgrades.appendTag(upgStack.writeToNBT(new NBTTagCompound()));
                    }
                    if (!ae2eUpgrades.isEmpty()) {
                        output.setTag("ae2e:upgrades", ae2eUpgrades);
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy Mekanism machine config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            // 验证 dataType
            String sourceDataType = data.getString("dataType");
            String targetDataType = getDataType(tile);
            if (!isCompatible(sourceDataType, targetDataType)) {
                return PasteResult.INVALID_MACHINE;
            }

            // 0. 处理工厂 tier 升级（需要在其他配置之前）
            PasteResult tierResult = applyTierUpgrade(tile, data, player);
            if (tierResult == PasteResult.INVALID_MACHINE || tierResult == PasteResult.FAILED) {
                return tierResult;
            }
            if (tierResult == PasteResult.MISSING_UPGRADES) {
                return tierResult;
            }
            if (tierResult == PasteResult.SUCCESS) {
                // upgrade 可能替换了 tile，重新获取
                TileEntity newTile = player.world.getTileEntity(tile.getPos());
                if (newTile != null) {
                    tile = newTile;
                }
            }

            // 1. 红石控制
            if (data.hasKey("controlType") && REDSTONE_CONTROL_CLASS.isInstance(tile)) {
                int ordinal = data.getInteger("controlType");
                if (ordinal >= 0 && ordinal < REDSTONE_CONTROL_VALUES.length) {
                    REDSTONE_SET_CONTROL_TYPE.invoke(tile, REDSTONE_CONTROL_VALUES[ordinal]);
                }
            }

            // 2. 侧面配置
            if (SIDE_CONFIGURATION_CLASS.isInstance(tile)) {
                Object config = SIDE_CONFIG_GET_CONFIG.invoke(tile);
                Object ejector = SIDE_CONFIG_GET_EJECTOR.invoke(tile);
                NBTTagCompound configData = data.copy();
                // 移除非配置键，避免误写入
                configData.removeTag("dataType");
                configData.removeTag("mekanism:upgrades");
                TILE_COMPONENT_CONFIG_READ.invoke(config, configData);
                TILE_COMPONENT_EJECTOR_READ.invoke(ejector, configData);
            }

            // 3. 特殊配置数据
            if ((Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null)) {
                Object special = CAPABILITY_UTILS_GET_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null);
                Method setConfigurationData = SPECIAL_CONFIG_DATA_CLASS.getMethod("setConfigurationData", NBTTagCompound.class);
                NBTTagCompound specialData = data.copy();
                specialData.removeTag("dataType");
                specialData.removeTag("mekanism:upgrades");
                specialData.removeTag("controlType");
                setConfigurationData.invoke(special, specialData);
            }

            // 4. 升级处理（支持网络回退）
            if (data.hasKey("mekanism:upgrades") && UPGRADE_TILE_CLASS.isInstance(tile)) {
                Object component = UPGRADE_TILE_GET_COMPONENT.invoke(tile);
                NBTTagCompound upgradeNbt = data.getCompoundTag("mekanism:upgrades");

                // 读取源升级 map
                @SuppressWarnings("unchecked")
                Map<Object, Integer> sourceUpgrades = (Map<Object, Integer>) UPGRADE_BUILD_MAP.invoke(null, upgradeNbt);

                // 读取目标当前升级 map
                NBTTagCompound currentNbt = new NBTTagCompound();
                TILE_COMPONENT_UPGRADE_WRITE.invoke(component, currentNbt);
                @SuppressWarnings("unchecked")
                Map<Object, Integer> targetUpgrades = (Map<Object, Integer>) UPGRADE_BUILD_MAP.invoke(null, currentNbt);

                // 4a. 先检查所有需要增加的升级是否可获得（含网络回退）
                List<ItemStack> missingUpgrades = new ArrayList<>();
                for (Map.Entry<Object, Integer> entry : sourceUpgrades.entrySet()) {
                    Object upgradeType = entry.getKey();
                    int sourceCount = entry.getValue();
                    int targetCount = targetUpgrades.getOrDefault(upgradeType, 0);
                    if (targetCount < sourceCount) {
                        int needed = sourceCount - targetCount;
                        ItemStack stack = ((ItemStack) UPGRADE_GET_STACK.invoke(upgradeType)).copy();
                        stack.setCount(needed);
                        if (MemoryCardUpgradeHelper.countInInventory(player, stack) < needed) {
                            missingUpgrades.add(stack.copy());
                        }
                    }
                }
                if (!missingUpgrades.isEmpty()) {
                    boolean pulled = MemoryCardUpgradeHelper.tryPullFromNetwork(player, missingUpgrades);
                    if (!pulled) {
                        return PasteResult.MISSING_UPGRADES;
                    }
                    // 回退后再次验证
                    for (ItemStack need : missingUpgrades) {
                        if (MemoryCardUpgradeHelper.countInInventory(player, need) < need.getCount()) {
                            return PasteResult.MISSING_UPGRADES;
                        }
                    }
                }

                // 4b. 应用升级差异
                for (Map.Entry<Object, Integer> entry : sourceUpgrades.entrySet()) {
                    Object upgradeType = entry.getKey();
                    int sourceCount = entry.getValue();
                    int targetCount = targetUpgrades.getOrDefault(upgradeType, 0);

                    if (targetCount < sourceCount) {
                        int needed = sourceCount - targetCount;
                        ItemStack stack = ((ItemStack) UPGRADE_GET_STACK.invoke(upgradeType)).copy();
                        stack.setCount(needed);
                        MemoryCardUpgradeHelper.consumeFromInventory(player, stack);
                        for (int i = 0; i < needed; i++) {
                            TILE_COMPONENT_UPGRADE_ADD_UPGRADE.invoke(component, upgradeType);
                        }
                    } else if (targetCount > sourceCount) {
                        int remove = targetCount - sourceCount;
                        for (int i = 0; i < remove; i++) {
                            TILE_COMPONENT_UPGRADE_REMOVE_UPGRADE.invoke(component, upgradeType);
                        }
                        ItemStack stack = ((ItemStack) UPGRADE_GET_STACK.invoke(upgradeType)).copy();
                        stack.setCount(remove);
                        if (!player.addItemStackToInventory(stack)) {
                            player.world.spawnEntity(new net.minecraft.entity.item.EntityItem(player.world, player.posX, player.posY, player.posZ, stack));
                        }
                    }
                }

                // 处理目标有但源没有的升级类型
                for (Map.Entry<Object, Integer> entry : targetUpgrades.entrySet()) {
                    Object upgradeType = entry.getKey();
                    if (!sourceUpgrades.containsKey(upgradeType)) {
                        int remove = entry.getValue();
                        for (int i = 0; i < remove; i++) {
                            TILE_COMPONENT_UPGRADE_REMOVE_UPGRADE.invoke(component, upgradeType);
                        }
                        ItemStack stack = ((ItemStack) UPGRADE_GET_STACK.invoke(upgradeType)).copy();
                        stack.setCount(remove);
                        if (!player.addItemStackToInventory(stack)) {
                            player.world.spawnEntity(new net.minecraft.entity.item.EntityItem(player.world, player.posX, player.posY, player.posZ, stack));
                        }
                    }
                }
            }

            // 5. 显式处理 sorting 字段（双重保险）
            if (data.hasKey("sorting")) {
                applySorting(tile, data.getBoolean("sorting"));
            }

            // 6. 更新 tile
            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(), tile.getWorld().getBlockState(tile.getPos()), tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste Mekanism machine config", e);
            return PasteResult.FAILED;
        }
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }

    /**
     * 尝试应用工厂 tier 升级。如果不是工厂或不需要升级，返回 SUCCESS。
     */
    private PasteResult applyTierUpgrade(TileEntity tile, NBTTagCompound data, EntityPlayer player) {
        if (TIER_UPGRADEABLE_CLASS == null || BASE_TIER_VALUES == null) {
            return PasteResult.SUCCESS; // 反射不可用
        }
        try {
            String sourceDataType = data.getString("dataType");
            String targetDataType = getDataType(tile);

            // 只处理工厂类型
            if (!sourceDataType.contains("Factory") || !targetDataType.contains("Factory")) {
                return PasteResult.SUCCESS;
            }

            // 提取 tier
            Object sourceTier = extractBaseTier(sourceDataType);
            Object targetTier = extractBaseTier(targetDataType);
            if (sourceTier == null || targetTier == null) {
                return PasteResult.SUCCESS;
            }

            int sourceOrdinal = ((Enum<?>) sourceTier).ordinal();
            int targetOrdinal = ((Enum<?>) targetTier).ordinal();

            if (sourceOrdinal <= targetOrdinal) {
                return PasteResult.SUCCESS; // 无需升级或降级
            }

            // 需要升级：消耗 Tier Installer（meta = 目标 tier ordinal）
            if (!TIER_UPGRADEABLE_CLASS.isInstance(tile)) {
                return PasteResult.INVALID_MACHINE;
            }

            // 逐级升级（从当前 tier+1 到源 tier）
            for (int tierOrd = targetOrdinal + 1; tierOrd <= sourceOrdinal; tierOrd++) {
                Object tier = BASE_TIER_VALUES[tierOrd];
                ItemStack installer = getTierInstaller(tierOrd);
                if (installer.isEmpty()) {
                    return PasteResult.FAILED;
                }
                if (MemoryCardUpgradeHelper.countInInventory(player, installer) < 1) {
                    // 尝试从网络拉取
                    List<ItemStack> missing = new ArrayList<>();
                    missing.add(installer.copy());
                    boolean pulled = MemoryCardUpgradeHelper.tryPullFromNetwork(player, missing);
                    if (!pulled || MemoryCardUpgradeHelper.countInInventory(player, installer) < 1) {
                        return PasteResult.MISSING_UPGRADES;
                    }
                }
                MemoryCardUpgradeHelper.consumeFromInventory(player, installer);
                TIER_UPGRADEABLE_UPGRADE.invoke(tile, tier);

                // 升级后重新获取 tile（block 被替换）
                TileEntity newTile = player.world.getTileEntity(tile.getPos());
                if (newTile == null || !TIER_UPGRADEABLE_CLASS.isInstance(newTile)) {
                    return PasteResult.FAILED;
                }
                tile = newTile;
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to apply Mekanism tier upgrade", e);
            return PasteResult.FAILED;
        }
    }

    private Object extractBaseTier(String dataType) {
        // dataType 示例: "Basic Smelting Factory", "Elite Enriching Factory"
        String[] parts = dataType.split(" ");
        if (parts.length == 0) return null;
        String tierName = parts[0];
        for (Object tier : BASE_TIER_VALUES) {
            if (((Enum<?>) tier).name().equalsIgnoreCase(tierName)) {
                return tier;
            }
        }
        return null;
    }

    private ItemStack getTierInstaller(int meta) {
        if (meta >= 0 && meta < TIER_INSTALLER_CACHE.length && TIER_INSTALLER_CACHE[meta] != null) {
            return TIER_INSTALLER_CACHE[meta].copy();
        }
        try {
            net.minecraft.util.ResourceLocation rl = new net.minecraft.util.ResourceLocation("mekanism", "tierinstaller");
            net.minecraft.item.Item item = net.minecraft.item.Item.REGISTRY.getObject(rl);
            if (item != null) {
                ItemStack stack = new ItemStack(item, 1, meta);
                if (meta >= 0 && meta < TIER_INSTALLER_CACHE.length) {
                    TIER_INSTALLER_CACHE[meta] = stack.copy();
                }
                return stack;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not get Mekanism tier installer", e);
        }
        return ItemStack.EMPTY;
    }

    private void applySorting(TileEntity tile, boolean sorting) {
        try {
            Class<?> current = tile.getClass();
            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField("sorting");
                    if (field.getType() == boolean.class) {
                        field.setAccessible(true);
                        field.setBoolean(tile, sorting);
                        return;
                    }
                } catch (NoSuchFieldException ignored) {
                }
                current = current.getSuperclass();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not set sorting field on {}", tile.getClass().getName());
        }
    }

    private String getDataType(TileEntity tile) throws Exception {
        if ((Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null)) {
            Object special = CAPABILITY_UTILS_GET_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null);
            Method getDataType = SPECIAL_CONFIG_DATA_CLASS.getMethod("getDataType");
            return (String) getDataType.invoke(special);
        }
        if (TILE_ENTITY_CONTAINER_BLOCK_CLASS.isInstance(tile)) {
            String fullName = (String) CONTAINER_BLOCK_FULL_NAME.get(tile);
            String blockName = tile.getWorld().getBlockState(tile.getPos()).getBlock().getRegistryName().toString();
            return blockName + "." + fullName + ".name";
        }
        return tile.getClass().getName();
    }

    /**
     * 验证源机器和目标机器是否兼容。
     * 允许工厂跨 tier 粘贴（如 Basic Factory → Elite Factory）。
     */
    private boolean isCompatible(String sourceType, String targetType) {
        if (sourceType.equals(targetType)) return true;

        // 工厂兼容：检查是否都是工厂且 recipe type 相同
        // 典型的 dataType: "Basic Smelting Factory" / "Elite Smelting Factory"
        // 或者: "SmeltingFactory" 在 fullName 中
        if (sourceType.contains("Factory") && targetType.contains("Factory")) {
            // 提取 recipe type（假设 dataType 格式为 "Tier RecipeType Factory" 或包含 recipe type）
            // 简化处理：如果两者都包含相同的 recipe type 关键词，则允许
            String[] sourceParts = sourceType.split(" ");
            String[] targetParts = targetType.split(" ");
            if (sourceParts.length >= 2 && targetParts.length >= 2) {
                // 比较中间部分（recipe type）
                String sourceRecipe = sourceParts[sourceParts.length - 2];
                String targetRecipe = targetParts[targetParts.length - 2];
                if (sourceRecipe.equals(targetRecipe)) {
                    return true;
                }
            }
            //  fallback：如果都包含 Factory 且有共同的单词（除了 tier）
            // 提取非 tier 单词
            String sourceNormalized = sourceType.replaceAll("(?i)basic|advanced|elite|ultimate", "").trim();
            String targetNormalized = targetType.replaceAll("(?i)basic|advanced|elite|ultimate", "").trim();
            if (sourceNormalized.equalsIgnoreCase(targetNormalized)) {
                return true;
            }
        }

        return false;
    }
}

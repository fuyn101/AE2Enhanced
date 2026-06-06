package com.github.aeddddd.ae2enhanced.util.memorycard.handler.nuclearcraft;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.IUpgradeProvider;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * NuclearCraft 机器的配置复制粘贴 Handler。
 * <p>支持 TileEnergyProcessor 及其子类（包括可升级处理器）。</p>
 * <p>复制/粘贴的内容包括：物品侧面配置、流体侧面配置、红石控制、比较器模式、
 * 物品输出设置、流体设置（分离/废弃/输出模式）、升级槽（速度/能量）。</p>
 *
 * <p>使用反射访问 NuclearCraft API，避免硬依赖。</p>
 */
public class NuclearCraftMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;

    // ---------- 类 ----------
    private static Class<?> TILE_ENERGY_PROCESSOR_CLASS;
    private static Class<?> TILE_CONTAINER_INFO_CLASS;
    private static Class<?> UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS;

    // ---------- 字段 ----------
    /** TileEnergyProcessor.info */
    private static Field INFO_FIELD;
    /** TileContainerInfo.name */
    private static Field NAME_FIELD;
    /** UpgradableProcessorContainerInfo.speedUpgradeSlot */
    private static Field SPEED_UPGRADE_SLOT_FIELD;
    /** UpgradableProcessorContainerInfo.energyUpgradeSlot */
    private static Field ENERGY_UPGRADE_SLOT_FIELD;

    // ---------- NCTile 方法 ----------
    private static Method GET_REDSTONE_CONTROL_METHOD;
    private static Method SET_REDSTONE_CONTROL_METHOD;
    private static Method GET_ALTERNATE_COMPARATOR_METHOD;
    private static Method SET_ALTERNATE_COMPARATOR_METHOD;

    // ---------- ITileInventory 方法 ----------
    private static Method HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD;
    private static Method WRITE_INVENTORY_CONNECTIONS_METHOD;
    private static Method READ_INVENTORY_CONNECTIONS_METHOD;
    private static Method WRITE_SLOT_SETTINGS_METHOD;
    private static Method READ_SLOT_SETTINGS_METHOD;
    private static Method GET_INVENTORY_STACKS_METHOD;

    // ---------- ITileFluid 方法 ----------
    private static Method HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD;
    private static Method WRITE_FLUID_CONNECTIONS_METHOD;
    private static Method READ_FLUID_CONNECTIONS_METHOD;
    private static Method WRITE_TANK_SETTINGS_METHOD;
    private static Method READ_TANK_SETTINGS_METHOD;

    // ---------- TileEnergyProcessor 方法 ----------
    private static Method REFRESH_ENERGY_CAPACITY_METHOD;

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("nuclearcraft")) {
                TILE_ENERGY_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileEnergyProcessor");
                TILE_CONTAINER_INFO_CLASS = Class.forName("nc.tile.TileContainerInfo");
                try {
                    UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS = Class.forName("nc.tile.processor.info.UpgradableProcessorContainerInfo");
                } catch (ClassNotFoundException e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] UpgradableProcessorContainerInfo not found, NC upgrade support disabled");
                }

                INFO_FIELD = TILE_ENERGY_PROCESSOR_CLASS.getDeclaredField("info");
                INFO_FIELD.setAccessible(true);
                NAME_FIELD = TILE_CONTAINER_INFO_CLASS.getField("name");

                if (UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS != null) {
                    SPEED_UPGRADE_SLOT_FIELD = UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.getField("speedUpgradeSlot");
                    ENERGY_UPGRADE_SLOT_FIELD = UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.getField("energyUpgradeSlot");
                }

                Class<?> ncTileClass = Class.forName("nc.tile.NCTile");
                GET_REDSTONE_CONTROL_METHOD = ncTileClass.getMethod("getRedstoneControl");
                SET_REDSTONE_CONTROL_METHOD = ncTileClass.getMethod("setRedstoneControl", boolean.class);
                GET_ALTERNATE_COMPARATOR_METHOD = ncTileClass.getMethod("getAlternateComparator");
                SET_ALTERNATE_COMPARATOR_METHOD = ncTileClass.getMethod("setAlternateComparator", boolean.class);

                Class<?> iTileInventoryClass = Class.forName("nc.tile.inventory.ITileInventory");
                HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("hasConfigurableInventoryConnections");
                WRITE_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("writeInventoryConnections", NBTTagCompound.class);
                READ_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("readInventoryConnections", NBTTagCompound.class);
                WRITE_SLOT_SETTINGS_METHOD = iTileInventoryClass.getMethod("writeSlotSettings", NBTTagCompound.class);
                READ_SLOT_SETTINGS_METHOD = iTileInventoryClass.getMethod("readSlotSettings", NBTTagCompound.class);
                GET_INVENTORY_STACKS_METHOD = iTileInventoryClass.getMethod("getInventoryStacks");

                Class<?> iTileFluidClass = Class.forName("nc.tile.fluid.ITileFluid");
                HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("hasConfigurableFluidConnections");
                WRITE_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("writeFluidConnections", NBTTagCompound.class);
                READ_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("readFluidConnections", NBTTagCompound.class);
                WRITE_TANK_SETTINGS_METHOD = iTileFluidClass.getMethod("writeTankSettings", NBTTagCompound.class);
                READ_TANK_SETTINGS_METHOD = iTileFluidClass.getMethod("readTankSettings", NBTTagCompound.class);

                REFRESH_ENERGY_CAPACITY_METHOD = TILE_ENERGY_PROCESSOR_CLASS.getMethod("refreshEnergyCapacity");

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize NuclearCraft reflection for UMC", e);
        }
        AVAILABLE = available;
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) {
            return false;
        }
        return TILE_ENERGY_PROCESSOR_CLASS != null && TILE_ENERGY_PROCESSOR_CLASS.isInstance(target);
    }

    /** 通过反射读取 TileEnergyProcessor.info.name */
    private String getInfoName(TileEntity tile) throws Exception {
        Object info = INFO_FIELD.get(tile);
        if (info == null) {
            return tile.getClass().getName();
        }
        return (String) NAME_FIELD.get(info);
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            output.setString("infoName", getInfoName(tile));

            // 物品侧面配置
            if ((Boolean) HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD.invoke(tile)) {
                NBTTagCompound nbt = (NBTTagCompound) WRITE_INVENTORY_CONNECTIONS_METHOD.invoke(tile, new NBTTagCompound());
                if (nbt != null && !nbt.isEmpty()) {
                    output.setTag("inventoryConnections", nbt);
                }
            }

            // 流体侧面配置
            if ((Boolean) HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD.invoke(tile)) {
                NBTTagCompound nbt = (NBTTagCompound) WRITE_FLUID_CONNECTIONS_METHOD.invoke(tile, new NBTTagCompound());
                if (nbt != null && !nbt.isEmpty()) {
                    output.setTag("fluidConnections", nbt);
                }
            }

            // 物品输出设置
            NBTTagCompound slotNbt = (NBTTagCompound) WRITE_SLOT_SETTINGS_METHOD.invoke(tile, new NBTTagCompound());
            if (slotNbt != null && !slotNbt.isEmpty()) {
                output.setTag("slotSettings", slotNbt);
            }

            // 流体设置（分离输入罐、废弃无效流体、输出模式）
            NBTTagCompound tankNbt = (NBTTagCompound) WRITE_TANK_SETTINGS_METHOD.invoke(tile, new NBTTagCompound());
            if (tankNbt != null && !tankNbt.isEmpty()) {
                output.setTag("tankSettings", tankNbt);
            }

            // 红石控制
            output.setBoolean("redstoneControl", (Boolean) GET_REDSTONE_CONTROL_METHOD.invoke(tile));
            // 比较器模式
            output.setBoolean("alternateComparator", (Boolean) GET_ALTERNATE_COMPARATOR_METHOD.invoke(tile));

            // 升级
            copyUpgrades(tile, output);

        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy NuclearCraft machine config", e);
        }

        return output;
    }

    /** 复制速度/能量升级槽 */
    @SuppressWarnings("unchecked")
    private void copyUpgrades(TileEntity tile, NBTTagCompound output) throws Exception {
        if (UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS == null) {
            return;
        }
        Object info = INFO_FIELD.get(tile);
        if (info == null || !UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.isInstance(info)) {
            return;
        }

        int speedSlot = SPEED_UPGRADE_SLOT_FIELD.getInt(info);
        int energySlot = ENERGY_UPGRADE_SLOT_FIELD.getInt(info);
        NonNullList<ItemStack> stacks = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(tile);

        NBTTagList list = new NBTTagList();
        ItemStack speed = stacks.get(speedSlot);
        if (!speed.isEmpty()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", 0);
            speed.writeToNBT(tag);
            list.appendTag(tag);
        }
        ItemStack energy = stacks.get(energySlot);
        if (!energy.isEmpty()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", 1);
            energy.writeToNBT(tag);
            list.appendTag(tag);
        }

        if (!list.isEmpty()) {
            output.setTag("ae2e:upgrades", list);
        }
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            // 严格匹配 infoName（不同机器的槽位/罐位数量可能不同）
            String sourceInfoName = data.getString("infoName");
            String targetInfoName = getInfoName(tile);
            if (!sourceInfoName.equals(targetInfoName)) {
                return PasteResult.INVALID_MACHINE;
            }

            // ===== 阶段 1：升级处理 =====
            List<ItemStack> neededUpgrades = parseUpgrades(tile, data);
            if (!neededUpgrades.isEmpty()) {
                IUpgradeProvider provider = createUpgradeProvider(tile);
                if (provider != null) {
                    PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(provider, neededUpgrades, player);
                    if (result != PasteResult.SUCCESS) {
                        return result;
                    }
                }
            }

            // ===== 阶段 2：应用配置 =====
            if (data.hasKey("inventoryConnections")
                    && (Boolean) HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD.invoke(tile)) {
                READ_INVENTORY_CONNECTIONS_METHOD.invoke(tile, data.getCompoundTag("inventoryConnections"));
            }

            if (data.hasKey("fluidConnections")
                    && (Boolean) HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD.invoke(tile)) {
                READ_FLUID_CONNECTIONS_METHOD.invoke(tile, data.getCompoundTag("fluidConnections"));
            }

            if (data.hasKey("slotSettings")) {
                READ_SLOT_SETTINGS_METHOD.invoke(tile, data.getCompoundTag("slotSettings"));
            }

            if (data.hasKey("tankSettings")) {
                READ_TANK_SETTINGS_METHOD.invoke(tile, data.getCompoundTag("tankSettings"));
            }

            if (data.hasKey("redstoneControl")) {
                SET_REDSTONE_CONTROL_METHOD.invoke(tile, data.getBoolean("redstoneControl"));
            }

            if (data.hasKey("alternateComparator")) {
                SET_ALTERNATE_COMPARATOR_METHOD.invoke(tile, data.getBoolean("alternateComparator"));
            }

            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(
                        tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()),
                        3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste NuclearCraft machine config", e);
            return PasteResult.FAILED;
        }
    }

    /** 从 NBT 解析需要的升级物品 */
    @SuppressWarnings("unchecked")
    private List<ItemStack> parseUpgrades(TileEntity tile, NBTTagCompound data) throws Exception {
        List<ItemStack> list = new ArrayList<>();
        if (!data.hasKey("ae2e:upgrades")) {
            return list;
        }
        if (UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS == null) {
            return list;
        }
        Object info = INFO_FIELD.get(tile);
        if (info == null || !UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.isInstance(info)) {
            return list;
        }

        NBTTagList upgrades = data.getTagList("ae2e:upgrades", 10);
        for (int i = 0; i < upgrades.tagCount(); i++) {
            ItemStack stack = new ItemStack(upgrades.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    /** 为目标机器创建 IUpgradeProvider（仅针对升级槽位） */
    @SuppressWarnings("unchecked")
    private IUpgradeProvider createUpgradeProvider(TileEntity tile) throws Exception {
        if (UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS == null) {
            return null;
        }
        Object info = INFO_FIELD.get(tile);
        if (info == null || !UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.isInstance(info)) {
            return null;
        }

        int speedSlot = SPEED_UPGRADE_SLOT_FIELD.getInt(info);
        int energySlot = ENERGY_UPGRADE_SLOT_FIELD.getInt(info);
        NonNullList<ItemStack> stacks = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(tile);
        return new NuclearCraftUpgradeProvider(tile, stacks, speedSlot, energySlot);
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }

    /**
     * NuclearCraft 升级槽的 IUpgradeProvider 实现。
     * <p>直接操作 NonNullList 中的指定索引，并在修改后调用 refreshEnergyCapacity()。</p>
     */
    private static class NuclearCraftUpgradeProvider implements IUpgradeProvider {

        private final TileEntity tile;
        private final NonNullList<ItemStack> stacks;
        private final int speedSlot;
        private final int energySlot;

        NuclearCraftUpgradeProvider(TileEntity tile, NonNullList<ItemStack> stacks, int speedSlot, int energySlot) {
            this.tile = tile;
            this.stacks = stacks;
            this.speedSlot = speedSlot;
            this.energySlot = energySlot;
        }

        @Override
        public int getSlotCount() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stacks.get(slot == 0 ? speedSlot : energySlot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            stacks.set(slot == 0 ? speedSlot : energySlot, stack);
            refreshCapacity();
        }

        @Override
        public void clearSlots() {
            stacks.set(speedSlot, ItemStack.EMPTY);
            stacks.set(energySlot, ItemStack.EMPTY);
            refreshCapacity();
        }

        private void refreshCapacity() {
            try {
                if (REFRESH_ENERGY_CAPACITY_METHOD != null) {
                    REFRESH_ENERGY_CAPACITY_METHOD.invoke(tile);
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Could not refresh NuclearCraft energy capacity", e);
            }
        }
    }
}

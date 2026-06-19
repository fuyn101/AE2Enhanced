package com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2;

import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.InternalInventoryUpgradeAdapter;

import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.ISegmentedInventory;
import ae2.tile.AEBaseTile;
import ae2.util.SettingsFrom;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.List;

/**
 * 处理 AE2S TileEntity 的配置复制粘贴.
 * 配置与升级分离：paste 先应用升级,再用剩余 NBT 应用配置.
 */
public class AE2TileHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        return target instanceof AEBaseTile;
    }

    @Override
    public NBTTagCompound copy(Object target) {
        AEBaseTile tile = (AEBaseTile) target;
        NBTTagCompound output = tile.exportSettings(SettingsFrom.MEMORY_CARD);
        if (output == null) {
            output = new NBTTagCompound();
        }

        try {
            if (tile instanceof ISegmentedInventory) {
                InternalInventory upgrades = ((ISegmentedInventory) tile).getSubInventory(ISegmentedInventory.UPGRADES);
                if (upgrades != null && upgrades.size() > 0) {
                    NBTTagList upgradeList = MemoryCardUpgradeHelper.serializeUpgrades(
                            new InternalInventoryUpgradeAdapter(upgrades));
                    if (!upgradeList.isEmpty()) {
                        output.setTag("ae2e:upgrades", upgradeList);
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy upgrades for {}", tile.getClass().getName(), e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        AEBaseTile tile = (AEBaseTile) target;

        // 1. 先处理升级(配置粘贴不应覆盖升级槽)
        if (data.hasKey("ae2e:upgrades") && tile instanceof ISegmentedInventory) {
            NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
            InternalInventory upgrades = ((ISegmentedInventory) tile).getSubInventory(ISegmentedInventory.UPGRADES);
            if (upgrades != null) {
                List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(upgradeList);
                PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(
                        new InternalInventoryUpgradeAdapter(upgrades), needed, player);
                if (result != PasteResult.SUCCESS) return result;
            }
        }

        // 2. 用不含升级的 NBT 应用配置
        NBTTagCompound settings = data.copy();
        settings.removeTag("ae2e:upgrades");
        tile.importSettings(SettingsFrom.MEMORY_CARD, settings, player);

        return PasteResult.SUCCESS;
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof AEBaseTile) {
            return ((AEBaseTile) target).getClass().getSimpleName();
        }
        return target.getClass().getSimpleName();
    }
}

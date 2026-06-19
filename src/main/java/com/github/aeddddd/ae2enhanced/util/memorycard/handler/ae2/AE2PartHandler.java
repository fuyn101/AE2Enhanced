package com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2;

import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.InternalInventoryUpgradeAdapter;

import ae2.api.inventories.InternalInventory;
import ae2.api.inventories.ISegmentedInventory;
import ae2.parts.AEBasePart;
import ae2.util.SettingsFrom;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.List;

/**
 * 处理 AE2S Part 的配置复制粘贴.
 * 使用 AE2S 原生的 {@link AEBasePart#exportSettings}/{@link AEBasePart#importSettings},
 * 并额外序列化/还原升级槽.
 */
public class AE2PartHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        return target instanceof AEBasePart;
    }

    @Override
    public NBTTagCompound copy(Object target) {
        AEBasePart part = (AEBasePart) target;
        NBTTagCompound output = part.exportSettings(SettingsFrom.MEMORY_CARD);
        if (output == null) {
            output = new NBTTagCompound();
        }

        try {
            InternalInventory upgrades = part.getSubInventory(ISegmentedInventory.UPGRADES);
            if (upgrades != null && upgrades.size() > 0) {
                NBTTagList upgradeList = MemoryCardUpgradeHelper.serializeUpgrades(
                        new InternalInventoryUpgradeAdapter(upgrades));
                if (!upgradeList.isEmpty()) {
                    output.setTag("ae2e:upgrades", upgradeList);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy upgrades for {}", part.getClass().getName(), e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        AEBasePart part = (AEBasePart) target;

        // 1. 先处理升级
        if (data.hasKey("ae2e:upgrades")) {
            NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
            InternalInventory upgrades = part.getSubInventory(ISegmentedInventory.UPGRADES);
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
        part.importSettings(SettingsFrom.MEMORY_CARD, settings, player);

        return PasteResult.SUCCESS;
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof AEBasePart) {
            return ((AEBasePart) target).getPartItem().asItemStack().getDisplayName();
        }
        return target.getClass().getSimpleName();
    }
}

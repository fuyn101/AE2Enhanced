package com.github.aeddddd.ae2enhanced.util.memorycard;

import appeng.api.parts.PartItemStack;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Method;

/**
 * 处理 AE2 Part 的配置复制粘贴。
 */
public class AE2PartHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        return target instanceof AEBasePart;
    }

    @Override
    public NBTTagCompound copy(Object target) {
        AEBasePart part = (AEBasePart) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            // 优先从实际类获取方法（支持子类重写），fallback 到 AEBasePart
            Method method;
            try {
                method = part.getClass().getDeclaredMethod("downloadSettings", SettingsFrom.class, NBTTagCompound.class);
            } catch (NoSuchMethodException e) {
                method = AEBasePart.class.getDeclaredMethod("downloadSettings", SettingsFrom.class, NBTTagCompound.class);
            }
            method.setAccessible(true);
            method.invoke(part, SettingsFrom.MEMORY_CARD, output);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy AE2 part settings for {}", part.getClass().getName(), e);
            // 即使反射失败，也返回已收集的数据（可能为空）
        }

        // 额外复制升级槽
        try {
            IItemHandler upgrades = part.getInventoryByName("upgrades");
            if (upgrades != null && upgrades.getSlots() > 0) {
                NBTTagList upgradeList = MemoryCardUpgradeHelper.serializeUpgrades(upgrades);
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

        // 1. 应用基础配置
        NBTTagCompound settings = data.copy();
        settings.removeTag("ae2e:upgrades");
        part.uploadSettings(SettingsFrom.MEMORY_CARD, settings, player);

        // 2. 处理升级槽
        if (data.hasKey("ae2e:upgrades")) {
            NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
            IItemHandler upgrades = part.getInventoryByName("upgrades");
            if (upgrades != null) {
                return MemoryCardUpgradeHelper.applyUpgrades(upgrades, upgradeList, player);
            }
        }

        return PasteResult.SUCCESS;
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof AEBasePart) {
            return ((AEBasePart) target).getItemStack(PartItemStack.NETWORK).getDisplayName();
        }
        return target.getClass().getSimpleName();
    }
}

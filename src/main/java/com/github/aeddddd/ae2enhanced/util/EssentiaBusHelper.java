package com.github.aeddddd.ae2enhanced.util;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.tile.inventory.AppEngInternalAEInventory;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

/**
 * 所有涉及 thaumcraft / thaumicenergistics 的 Essentia 逻辑集中在此处。
 * 此类只在运行时通过 Class.forName 加载，避免在 Part 类加载时触发
 * thaumicenergistics 类缺失导致的 NoClassDefFoundError。
 */
public class EssentiaBusHelper {

    // region FakeEssentias 功能（供 Mixin / Helper 自身使用）

    public static IAEItemStack packEssentia(IAEEssentiaStack essentiaStack) {
        if (essentiaStack == null || essentiaStack.getAspect() == null) return null;
        String aspectTag = essentiaStack.getAspect().getTag();
        long amount = essentiaStack.getStackSize();
        ItemStack fakeItem = ItemEssentiaDrop.createStack(aspectTag, 1);
        IAEItemStack result = AEApi.instance().storage().getStorageChannel(
                appeng.api.storage.channels.IItemStorageChannel.class).createStack(fakeItem);
        if (result != null) {
            result.setStackSize(amount);
        }
        return result;
    }

    public static IAEEssentiaStack unpackEssentia(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack mcStack = itemStack.createItemStack();
        String aspectTag = ItemEssentiaDrop.getAspectTag(mcStack);
        if (aspectTag == null) return null;
        EssentiaStack essStack = new EssentiaStack(aspectTag, 1);
        IAEEssentiaStack result = AEEssentiaStack.fromEssentiaStack(essStack);
        if (result != null) {
            result.setStackSize(itemStack.getStackSize());
        }
        return result;
    }

    // endregion

    // region Import Bus

    @SuppressWarnings("unchecked")
    public static boolean importEssentias(appeng.api.networking.IGrid grid, TileEntity target, EnumFacing opposite,
                                           AppEngInternalAEInventory config, IActionSource source) throws Exception {
        thaumcraft.api.aspects.IEssentiaTransport transport = (thaumcraft.api.aspects.IEssentiaTransport) target;

        Class<?> essentiaChannelClass = Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
        java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
        Object essentiaChannel = getChannel.invoke(AEApi.instance().storage(), essentiaChannelClass);

        appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
        @SuppressWarnings("unchecked")
        IMEMonitor<IAEEssentiaStack> inv = (IMEMonitor<IAEEssentiaStack>) storageGrid.getInventory((appeng.api.storage.IStorageChannel<?>) essentiaChannel);

        boolean worked = false;

        for (int x = 0; x < config.getSlots(); x++) {
            IAEItemStack filter = config.getAEStackInSlot(x);
            if (filter == null || !ItemEssentiaDrop.isEssentiaDrop(filter.createItemStack())) continue;

            IAEEssentiaStack wanted = unpackEssentia(filter);
            if (wanted == null || wanted.getAspect() == null) continue;

            int available = transport.getEssentiaAmount(opposite);
            if (available <= 0) continue;

            int toTake = Math.min(available, 64);
            EssentiaStack essStack = new EssentiaStack(wanted.getAspect().getTag(), toTake);
            IAEEssentiaStack aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
            if (aeEss == null) continue;

            IAEEssentiaStack notInserted = inv.injectItems(aeEss, Actionable.SIMULATE, source);
            long canInsert = aeEss.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
            if (canInsert <= 0) continue;

            int actual = transport.takeEssentia(wanted.getAspect(), (int) canInsert, opposite);
            if (actual > 0) {
                EssentiaStack actualStack = new EssentiaStack(wanted.getAspect().getTag(), actual);
                IAEEssentiaStack toInsert = AEEssentiaStack.fromEssentiaStack(actualStack);
                inv.injectItems(toInsert, Actionable.MODULATE, source);
                worked = true;
                break;
            }
        }

        return worked;
    }

    // endregion

    // region Export Bus

    @SuppressWarnings("unchecked")
    public static boolean exportEssentias(appeng.api.networking.IGrid grid, TileEntity target, EnumFacing opposite,
                                           AppEngInternalAEInventory config, IActionSource source) throws Exception {
        thaumcraft.api.aspects.IEssentiaTransport transport = (thaumcraft.api.aspects.IEssentiaTransport) target;

        Class<?> essentiaChannelClass = Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
        java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
        Object essentiaChannel = getChannel.invoke(AEApi.instance().storage(), essentiaChannelClass);

        appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
        @SuppressWarnings("unchecked")
        IMEMonitor<IAEEssentiaStack> inv = (IMEMonitor<IAEEssentiaStack>) storageGrid.getInventory((appeng.api.storage.IStorageChannel<?>) essentiaChannel);

        boolean worked = false;

        for (int x = 0; x < config.getSlots(); x++) {
            IAEItemStack filter = config.getAEStackInSlot(x);
            if (filter == null || !ItemEssentiaDrop.isEssentiaDrop(filter.createItemStack())) continue;

            IAEEssentiaStack wanted = unpackEssentia(filter);
            if (wanted == null || wanted.getAspect() == null) continue;

            int toSend = (int) Math.min(wanted.getStackSize(), 64);
            EssentiaStack essStack = new EssentiaStack(wanted.getAspect().getTag(), toSend);
            IAEEssentiaStack aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
            if (aeEss == null) continue;

            IAEEssentiaStack out = inv.extractItems(aeEss, Actionable.SIMULATE, source);
            if (out == null || out.getStackSize() <= 0) continue;

            int actual = transport.addEssentia(wanted.getAspect(), (int) out.getStackSize(), opposite);
            if (actual > 0) {
                essStack = new EssentiaStack(wanted.getAspect().getTag(), actual);
                aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
                inv.extractItems(aeEss, Actionable.MODULATE, source);
                worked = true;
                break;
            }
        }

        return worked;
    }

    // endregion
}

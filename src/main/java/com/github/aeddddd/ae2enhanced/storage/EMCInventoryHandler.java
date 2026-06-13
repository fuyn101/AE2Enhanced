package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * EMC 接口向 AE 网络暴露的存储处理器.
 *
 * <p>单向源: 只根据玩家 EMC 余额把已学知识物品“生成”到网络,不接受物品注入.</p>
 */
public class EMCInventoryHandler implements IMEInventoryHandler<IAEItemStack>, IMEMonitor<IAEItemStack> {

    private final TileEMCInterface tile;

    // 缓存
    private List<IAEItemStack> availableCache = Collections.emptyList();
    private long availableCacheTick = -100;
    private long emcBalanceCache = 0;
    private long emcBalanceCacheTick = -100;

    public EMCInventoryHandler(TileEMCInterface tile) {
        this.tile = tile;
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        // EMC 接口为单向源,拒绝接收物品
        return input;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable type, IActionSource src) {
        if (request == null || request.getStackSize() <= 0 || !tile.isBound()) return null;
        ItemStack definition = request.getDefinition();
        if (definition.isEmpty()) return null;

        // 白名单校验
        if (!tile.isWhitelisted(definition)) return null;

        long itemEmc = ProjectEHelper.getEmcValue(definition);
        if (itemEmc <= 0) return null;

        Object provider = tile.getKnowledgeProvider();
        if (provider == null) return null;

        // 提取时强制刷新余额缓存
        long balance = getEmcBalance(provider, true);
        long maxAffordable = balance / itemEmc;
        if (maxAffordable <= 0) return null;

        long extractCount = Math.min(request.getStackSize(), maxAffordable);
        if (extractCount <= 0) return null;

        if (type == Actionable.SIMULATE) {
            IAEItemStack result = request.copy();
            result.setStackSize(extractCount);
            return result;
        }

        // MODULATE: 扣减 EMC
        long cost = extractCount * itemEmc;
        long newBalance = balance - cost;
        ProjectEHelper.setEmc(provider, newBalance);
        refreshEmcCache(newBalance);

        // 同步在线玩家
        syncOwnerIfOnline();

        ItemStack real = definition.copy();
        real.setCount((int) Math.min(extractCount, definition.getMaxStackSize()));
        IAEItemStack result = AEItemStack.fromItemStack(real);
        if (result != null) {
            result.setStackSize(extractCount);
        }
        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        if (!tile.isBound()) return out;
        Object provider = tile.getKnowledgeProvider();
        if (provider == null) return out;

        long balance = getEmcBalance(provider, false);
        List<IAEItemStack> cached = getAvailableCache(provider, balance);
        for (IAEItemStack stack : cached) {
            out.add(stack.copy());
        }
        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        return false;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        return getAvailableItems(getChannel().createList());
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEItemStack> l, Object verificationToken) {
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEItemStack> l) {
    }

    // ---- 缓存控制 ----

    public void invalidateCache() {
        availableCacheTick = -100;
        emcBalanceCacheTick = -100;
    }

    private long getEmcBalance(Object provider, boolean forceRefresh) {
        long now = tile.getWorld().getTotalWorldTime();
        if (forceRefresh || now - emcBalanceCacheTick >= 20) {
            emcBalanceCache = ProjectEHelper.getEmc(provider);
            emcBalanceCacheTick = now;
        }
        return emcBalanceCache;
    }

    private void refreshEmcCache(long balance) {
        emcBalanceCache = balance;
        emcBalanceCacheTick = tile.getWorld().getTotalWorldTime();
    }

    @Nonnull
    private List<IAEItemStack> getAvailableCache(Object provider, long balance) {
        long now = tile.getWorld().getTotalWorldTime();
        if (now - availableCacheTick < 5 && !availableCache.isEmpty()) {
            return availableCache;
        }

        List<IAEItemStack> list = new ArrayList<>();
        for (ItemStack knowledge : ProjectEHelper.getKnowledge(provider)) {
            if (knowledge.isEmpty()) continue;
            if (!tile.isWhitelisted(knowledge)) continue;
            long itemEmc = ProjectEHelper.getEmcValue(knowledge);
            if (itemEmc <= 0) continue;
            long maxCount = balance / itemEmc;
            if (maxCount <= 0) continue;

            IAEItemStack ae = AEItemStack.fromItemStack(knowledge);
            if (ae == null) continue;
            ae.setStackSize(Math.min(maxCount, Integer.MAX_VALUE));
            list.add(ae);
        }
        availableCache = list;
        availableCacheTick = now;
        return list;
    }

    private void syncOwnerIfOnline() {
        UUID owner = tile.getOwnerUUID();
        if (owner == null) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(owner);
        if (player != null) {
            ProjectEHelper.sync(tile.getKnowledgeProvider(), player);
        }
    }
}

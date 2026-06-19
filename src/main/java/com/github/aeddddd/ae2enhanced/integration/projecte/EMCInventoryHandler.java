package com.github.aeddddd.ae2enhanced.integration.projecte;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

/**
 * 将绑定玩家的 ProjectE EMC 余额作为 AE2 网络物品源.
 *
 * <p>只输出白名单中且玩家已学习的物品；接收提取请求时扣除对应 EMC.</p>
 */
public class EMCInventoryHandler implements MEStorage, IStorageProvider {

    private final TileEMCInterface tile;

    public EMCInventoryHandler(TileEMCInterface tile) {
        this.tile = tile;
    }

    public void invalidateAvailableCache() {
        // AE2S 通过 getAvailableStacks 实时查询,无需缓存
    }

    public void invalidateEmcCache() {
        // 同上
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount; // 不接受注入
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!(what instanceof AEItemKey) || !tile.isBound() || amount <= 0) return 0;
        AEItemKey itemKey = (AEItemKey) what;
        UUID owner = tile.getOwnerUUID();
        if (owner == null) return 0;

        Object provider = ProjectEHelper.getKnowledgeProvider(owner);
        if (provider == null) return 0;

        ItemStack representative = itemKey.toStack();
        if (representative.isEmpty()) return 0;
        if (!ProjectEHelper.hasKnowledge(provider, representative)) return 0;

        long itemEmc = ProjectEHelper.getEmcValue(representative);
        if (itemEmc <= 0) return 0;

        long playerEmc = ProjectEHelper.getEmc(provider);
        long maxByEmc = playerEmc / itemEmc;
        long toExtract = Math.min(amount, maxByEmc);
        if (toExtract <= 0) return 0;

        if (mode == Actionable.MODULATE) {
            ProjectEHelper.setEmc(provider, playerEmc - toExtract * itemEmc);
        }
        return toExtract;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!tile.isBound()) return;
        UUID owner = tile.getOwnerUUID();
        if (owner == null) return;

        Object provider = ProjectEHelper.getKnowledgeProvider(owner);
        if (provider == null) return;

        long playerEmc = ProjectEHelper.getEmc(provider);
        if (playerEmc <= 0) return;

        Set<AEItemKey> whitelist = tile.getWhitelistKeys();
        for (AEItemKey key : whitelist) {
            ItemStack stack = key.toStack();
            if (stack.isEmpty()) continue;
            if (!ProjectEHelper.hasKnowledge(provider, stack)) continue;
            long itemEmc = ProjectEHelper.getEmcValue(stack);
            if (itemEmc <= 0) continue;
            long amount = playerEmc / itemEmc;
            if (amount > 0) {
                out.add(key, amount);
            }
        }
    }

    @Nonnull
    @Override
    public TextComponentString getDescription() {
        return new TextComponentString("EMC Interface");
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(this, 0);
    }
}

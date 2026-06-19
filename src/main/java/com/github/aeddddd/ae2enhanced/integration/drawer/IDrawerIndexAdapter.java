package com.github.aeddddd.ae2enhanced.integration.drawer;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.storage.data.AEItemKey;
import ae2.api.storage.data.KeyCounter;

/**
 * 抽屉模组 Hash 索引适配器统一接口.
 *
 * <p>封装 StorageDrawers 和 FunctionalStorageLegacy 的底层实现差异,
 * 对外提供 O(同物品槽位数) 的索引访问.所有反射和 NPE 风险由实现类内部承担.</p>
 */
public interface IDrawerIndexAdapter {

    AEItemKey injectItems(AEItemKey input, Actionable type, IActionSource src);

    AEItemKey extractItems(AEItemKey request, Actionable mode, IActionSource src);

    KeyCounter<AEItemKey> getAvailableItems(KeyCounter<AEItemKey> out);

    AccessRestriction getAccess();

    boolean isPrioritized(AEItemKey input);

    boolean canAccept(AEItemKey input);

    int getPriority();

    int getSlot();

    boolean validForPass(int pass);
}

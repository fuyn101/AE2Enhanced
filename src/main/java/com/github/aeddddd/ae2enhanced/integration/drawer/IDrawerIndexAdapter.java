package com.github.aeddddd.ae2enhanced.integration.drawer;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;

/**
 * 抽屉模组 Hash 索引适配器统一接口.
 *
 * <p>封装 StorageDrawers 和 FunctionalStorageLegacy 的底层实现差异,
 * 对外提供 O(同物品槽位数) 的索引访问.所有反射和 NPE 风险由实现类内部承担.</p>
 *
 * <p>本接口已迁移至 AE2S API:返回值使用 {@code long amount} 而非可变 {@link AEItemKey}.</p>
 */
public interface IDrawerIndexAdapter {

    /**
     * 尝试存入物品,返回未能存入的数量.
     *
     * @param input  物品类型
     * @param amount 尝试存入数量
     * @param type   模拟或实际执行
     * @param src    动作来源
     * @return 剩余未存入数量
     */
    long injectItems(AEItemKey input, long amount, Actionable type, IActionSource src);

    /**
     * 尝试取出物品,返回实际取出的数量.
     *
     * @param request 请求物品类型
     * @param amount  请求数量
     * @param mode    模拟或实际执行
     * @param src     动作来源
     * @return 实际取出数量
     */
    long extractItems(AEItemKey request, long amount, Actionable mode, IActionSource src);

    /**
     * 获取当前所有可用物品,写入 {@code out}.
     *
     * @param out 计数器
     */
    void getAvailableStacks(KeyCounter out);
}

package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 虚拟合成处理器接口，用于工作台类机器。
 *
 * 实现此接口的 handler 可以在不将物品实际推送到工作台的情况下，
 * 直接从 AE2 网络扣除材料并返回产物（类似分子装配室的即时合成）。
 *
 * 在 DualityCentralInterface.pushPattern() 中的使用逻辑：
 * <pre>
 * if (handler instanceof IVirtualCraftingHandler) {
 *     IVirtualCraftingHandler vh = (IVirtualCraftingHandler) handler;
 *     if (vh.canCraftVirtually(...)) {
 *         List&lt;ItemStack&gt; products = vh.virtualCraft(...);
 *         injectToNetwork(products);
 *         return true;
 *     }
 * }
 * </pre>
 */
public interface IVirtualCraftingHandler extends IRemoteHandler {

    /**
     * 验证该配方是否可以通过此工作台虚拟合成。
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 配方输入物品
     * @param outputs     配方预期产物
     * @return true 表示可以执行虚拟合成
     */
    boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs);

    /**
     * 执行虚拟合成。
     *
     * 实现应：<br>
     * 1. 验证网络中是否有足够材料（或已在外部扣除）<br>
     * 2. 从网络扣除材料<br>
     * 3. 返回产物列表（不将物品实际放入工作台）
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 配方输入物品
     * @param outputs     配方预期产物
     * @param source      AE 动作源
     * @return 合成产物列表；若失败返回空列表
     */
    List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source);
}

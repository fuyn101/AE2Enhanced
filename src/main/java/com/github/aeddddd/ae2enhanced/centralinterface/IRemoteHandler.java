package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * 远程目标处理器的抽象接口。
 *
 * 每个 TargetBinding 对应的目标方块，在合成推送和产物收集阶段
 * 会由匹配的 IRemoteHandler 处理实际物品交互。
 */
public interface IRemoteHandler {

    /**
     * 判断此处理器是否支持给定的目标 TileEntity。
     *
     * @param target 目标方块实体
     * @return true 表示此处理器可以处理该目标
     */
    boolean matches(TileEntity target);

    /**
     * 将合成配方所需的全部材料推送到目标方块。
     *
     * @param target      目标方块实体
     * @param ingredients 配方输入物品（InventoryCrafting）
     * @param source      AE 动作源（用于能量扣除等）
     * @return true 表示所有材料均已成功推送
     */
    boolean pushMaterials(TileEntity target, InventoryCrafting ingredients, IActionSource source);

    /**
     * 从目标方块收集预期的合成产物。
     *
     * @param target   目标方块实体
     * @param expected 期望收集的产物（含数量）
     * @param source   AE 动作源
     * @return 实际收集到的物品堆；若目标尚无产物则返回 EMPTY
     */
    ItemStack collectProducts(TileEntity target, IAEItemStack expected, IActionSource source);
}

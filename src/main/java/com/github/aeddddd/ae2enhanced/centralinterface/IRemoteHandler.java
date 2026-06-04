package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 远程目标处理器的抽象接口。
 *
 * 每个 {@link TargetBinding} 对应的目标方块，在合成推送和产物收集阶段
 * 会由匹配的 IRemoteHandler 处理实际物品交互。
 *
 * <p>P2 完整生命周期：</p>
 * <pre>
 * canHandle(blockId) ──→ isValidTarget(world, pos) ──→ canStart(world, pos, ingredients)
 *        ↓
 * pushMaterials(world, pos, ingredients, source) ──→ startProcess(world, pos, source)
 *        ↓
 * (等待机器处理)
 *        ↓
 * isIdle(world, pos) == true ──→ collectProducts(world, pos, expectedOutputs, source)
 * </pre>
 */
public interface IRemoteHandler {

    /**
     * 按方块 ID 字符串匹配此处理器是否支持该类型。
     *
     * <p>用于 {@link HandlerRegistry} 反射隔离查找，不涉及第三方类加载。</p>
     *
     * @param blockId 方块注册 ID（如 "minecraft:furnace"、"botania:pool"）
     * @return true 表示此处理器可以处理该方块类型
     */
    boolean canHandle(String blockId);

    /**
     * 验证目标位置是否存在且为正确类型。
     *
     * @param world 世界实例
     * @param pos   方块位置
     * @return true 表示目标有效且可被处理
     */
    boolean isValidTarget(World world, BlockPos pos);

    /**
     * 判断当前目标是否可以开始执行该配方。
     *
     * <p>例如：检查能量是否充足、输入槽是否空闲、祭坛是否就绪等。
     * 对于简单机器（如熔炉），可始终返回 {@code true}。</p>
     *
     * @param world       世界实例
     * @param pos         方块位置
     * @param ingredients 配方输入物品（来自 {@link InventoryCrafting}）
     * @return true 表示可以开始推送材料
     */
    boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients);

    /**
     * 将合成配方所需的全部材料推送到目标方块输入端。
     *
     * @param world       世界实例
     * @param pos         方块位置
     * @param ingredients 配方输入物品
     * @param source      AE 动作源（用于能量扣除等审计）
     * @return true 表示所有材料均已成功推送
     */
    boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source);

    /**
     * 启动机器处理流程（如激活祭坛、开始注魔等）。
     *
     * <p>对于简单机器（如熔炉）此方法应为空操作并返回 {@code true}，
     * 因为物品插入后机器会自动开始处理。</p>
     *
     * @param world  世界实例
     * @param pos    方块位置
     * @param source AE 动作源
     * @return true 表示成功触发启动
     */
    boolean startProcess(World world, BlockPos pos, IActionSource source);

    /**
     * 从目标方块收集已完成的产物（以及残余物品如空桶）。
     *
     * @param world           世界实例
     * @param pos             方块位置
     * @param expectedOutputs 配方预期产物列表，handler 应优先收集匹配物品
     * @param inputs          该目标本次合成推送的输入材料快照（用于区分产物与残留输入）
     * @param source          AE 动作源
     * @return 实际收集到的物品列表；若无产物则返回空列表（非 null）
     */
    List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source);

    /**
     * 判断目标是否处于空闲 / 处理完成状态。
     *
     * <p>当此方法返回 {@code true} 时，{@link DualityCentralInterface}
     * 会调用 {@link #collectProducts} 尝试收集产物。</p>
     *
     * @param world  世界实例
     * @param pos    方块位置
     * @param inputs 该目标本次合成推送的输入材料快照（用于区分产物与残留输入）
     * @return true 表示目标已完成处理，可以收集产物
     */
    boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs);

    /**
     * 当 {@code pushMaterials} 成功但 {@code startProcess} 失败时，
     * 调用此方法将已推送的材料从目标取回。
     *
     * <p>默认实现返回空列表，表示不回退。需要回退能力的 handler 应覆盖此方法。</p>
     *
     * @param world  世界实例
     * @param pos    方块位置
     * @param source AE 动作源
     * @return 回退收集到的物品列表；无物品则返回空列表
     */
    default List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        return java.util.Collections.emptyList();
    }
}

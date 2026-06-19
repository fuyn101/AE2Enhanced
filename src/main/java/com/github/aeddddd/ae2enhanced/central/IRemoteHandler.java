package com.github.aeddddd.ae2enhanced.central;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.GenericStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * 远程目标处理器的抽象接口(适配 AE2S 的 GenericStack/IPatternDetails 模型).
 *
 * <p>每个 {@link TargetBinding} 对应的目标方块,在合成推送和产物收集阶段
 * 会由匹配的 IRemoteHandler 处理实际物品交互.</p>
 */
public interface IRemoteHandler {

    /**
     * 按方块 ID 字符串匹配此处理器是否支持该类型.
     */
    boolean canHandle(@Nonnull String blockId);

    /**
     * 验证目标位置是否存在且为正确类型.
     */
    boolean isValidTarget(@Nonnull World world, @Nonnull BlockPos pos);

    /**
     * 判断当前目标是否可以开始执行该配方.
     */
    boolean canStart(@Nonnull World world, @Nonnull BlockPos pos,
                     @Nonnull IPatternDetails pattern, @Nonnull List<GenericStack> inputs);

    /**
     * 将合成配方所需的全部材料推送到目标方块输入端.
     */
    boolean pushMaterials(@Nonnull World world, @Nonnull BlockPos pos,
                          @Nonnull IPatternDetails pattern, @Nonnull List<GenericStack> inputs,
                          @Nonnull IActionSource source);

    /**
     * 启动机器处理流程(如激活祭坛、开始注魔等).
     */
    boolean startProcess(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IActionSource source);

    /**
     * 从目标方块收集已完成的产物(以及残余物品如空桶).
     *
     * @param inputs 该目标本次合成推送的输入材料快照(用于区分产物与残留输入)
     */
    @Nonnull
    List<GenericStack> collectProducts(@Nonnull World world, @Nonnull BlockPos pos,
                                       @Nonnull IPatternDetails pattern, @Nonnull List<GenericStack> inputs,
                                       @Nonnull IActionSource source);

    /**
     * 判断目标当前是否可以收集产物.
     */
    boolean isIdle(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull List<GenericStack> inputs);

    /**
     * 判断目标是否已完全处理完本次推送的所有输入材料.
     */
    default boolean hasFinished(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull List<GenericStack> inputs) {
        return isIdle(world, pos, inputs);
    }

    /**
     * 当 pushMaterials 成功但 startProcess 失败时,调用此方法将已推送的材料从目标取回.
     */
    @Nonnull
    default List<GenericStack> revertMaterials(@Nonnull World world, @Nonnull BlockPos pos,
                                               @Nonnull IActionSource source) {
        return Collections.emptyList();
    }

    /**
     * 在发配前回收目标输出槽中的全部残留内容.
     */
    @Nonnull
    default List<GenericStack> clearOutputs(@Nonnull World world, @Nonnull BlockPos pos,
                                            @Nonnull IActionSource source) {
        return Collections.emptyList();
    }
}

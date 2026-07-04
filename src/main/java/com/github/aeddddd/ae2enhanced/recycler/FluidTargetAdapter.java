package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.data.IAEFluidStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 回收目标流体适配器接口.
 */
public interface FluidTargetAdapter {

    /**
     * 扫描目标并返回可提取的流体列表.
     *
     * @param simulate 是否仅模拟
     * @return 可提取的流体列表
     */
    @Nonnull
    List<FluidStack> scan(boolean simulate);

    /**
     * 提取指定数量的某种流体.
     *
     * @param requested 请求的流体类型与数量
     * @param simulate  是否仅模拟
     * @return 实际提取的流体堆叠
     */
    @Nullable
    FluidStack extract(@Nonnull IAEFluidStack requested, boolean simulate);

    /**
     * 释放资源引用.
     */
    default void invalidate() {
    }
}

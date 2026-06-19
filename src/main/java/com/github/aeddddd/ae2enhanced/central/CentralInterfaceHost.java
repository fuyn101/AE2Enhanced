package com.github.aeddddd.ae2enhanced.central;

import ae2.api.inventories.InternalInventory;
import ae2.helpers.InterfaceLogicHost;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 中枢 ME 接口的宿主接口.
 *
 * <p>在 {@link InterfaceLogicHost} 的基础上增加远程目标绑定管理,
 * 以及一个独立的样板物品栏(中枢接口需要展示/编辑其远程目标).</p>
 */
public interface CentralInterfaceHost extends InterfaceLogicHost {

    @Override
    TileEntity getTileEntity();

    /**
     * 获取供远程目标绑定的样板/配置物品栏.
     *
     * <p>具体槽位语义由宿主决定;对纯接口逻辑而言,只需要能读写 NBT 即可.</p>
     */
    @Nonnull
    InternalInventory getPatternInventory();

    /**
     * 获取当前所有远程目标绑定.
     */
    @Nonnull
    List<TargetBinding> getBindings();

    /**
     * 添加一个目标绑定.若绑定已存在,建议由宿主决定是否覆盖.
     */
    void addBinding(@Nonnull TargetBinding binding);

    /**
     * 移除一个目标绑定.
     */
    void removeBinding(@Nonnull TargetBinding binding);

    /**
     * 清空所有目标绑定.
     */
    void clearBindings();
}

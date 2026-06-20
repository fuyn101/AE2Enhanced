package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;

/**
 * 支持批量虚拟合成的远程目标处理器接口.
 *
 * <p>实现此接口的 handler 可以在不占用物理设备的情况下,按指定份数直接从 AE2 网络
 * 扣除资源并返回产物.用于 Central Interface 安装虚拟并行卡后的高并行合成.</p>
 */
public interface IVirtualBatchCraftingHandler extends IVirtualCraftingHandler {

    /**
     * 计算 {@code count} 次虚拟合成所需的全部资源.
     *
     * <p>返回值应包含完整 {@code count} 份所需的所有 AE 存储栈(物品、流体、RF、
     * Mana、Starlight、源质、气体等).调用方会自行决定如何从网络提取.</p>
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品(来自 {@link InventoryCrafting})
     * @param outputs     单份配方的预期产物
     * @param count       虚拟合成份数
     * @return 资源清单；若无法计算则返回空列表
     */
    List<IAEStack> getVirtualCost(World world, BlockPos pos,
                                  InventoryCrafting ingredients,
                                  IAEItemStack[] outputs,
                                  int count);

    /**
     * 执行 {@code count} 次虚拟合成.
     *
     * <p>调用方保证资源已扣除,handler 内部不再重复扣资源,仅负责验证配方并返回产物.</p>
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品
     * @param outputs     单份配方的预期产物
     * @param count       虚拟合成份数
     * @param source      AE 动作源
     * @return 合成产物列表；失败返回空列表
     */
    List<ItemStack> virtualCraftBatch(World world, BlockPos pos,
                                      InventoryCrafting ingredients,
                                      IAEItemStack[] outputs,
                                      int count,
                                      IActionSource source);

    /**
     * 获取该 handler 在虚拟合成时使用的粒子效果.
     *
     * @param world 目标世界
     * @param pos   目标位置
     * @return 粒子类型列表
     */
    default List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return Collections.singletonList(EnumParticleTypes.PORTAL);
    }
}

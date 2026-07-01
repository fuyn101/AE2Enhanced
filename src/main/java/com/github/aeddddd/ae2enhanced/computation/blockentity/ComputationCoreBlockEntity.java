package com.github.aeddddd.ae2enhanced.computation.blockentity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPU;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

/**
 * 超因果计算核心控制器方块实体。
 * <p>成形后创建一个虚拟 AE2 Crafting CPU 并通过 Mixin 注册到 CraftingService。</p>
 */
public class ComputationCoreBlockEntity extends MultiblockControllerBlockEntity {

    private static final String INTERFACE_POS_TAG = "interfacePos";
    private static final String PARALLEL_LIMIT_TAG = "parallelLimit";

    @Nullable
    private VirtualCraftingCPU virtualCpu;
    @Nullable
    private BlockPos interfacePos;
    private int parallelLimit = 0;
    private int validationCooldown = 0;

    public ComputationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPUTATION_CONTROLLER.get(), pos, state);
    }

    public int getParallelLimit() {
        return parallelLimit;
    }

    /**
     * 结构装配成功时调用。
     *
     * @param parallel       并行上限
     * @param interfacePos   通用 ME 接口位置，虚拟 CPU 将挂靠在该接口节点上
     */
    public void assemble(int parallel, BlockPos interfacePos) {
        if (level == null || level.isClientSide() || isFormed()) {
            return;
        }
        this.interfacePos = interfacePos.immutable();
        this.parallelLimit = parallel;
        super.assemble();
    }

    @Override
    public void assemble() {
        if (isFormed()) {
            return;
        }
        super.assemble();
    }

    @Override
    public void onAssemble() {
        bindVirtualCpu();
    }

    @Override
    public void disassemble() {
        if (!isFormed()) {
            return;
        }
        super.disassemble();
    }

    @Override
    public void onDisassemble() {
        unbindVirtualCpu();
        interfacePos = null;
        parallelLimit = 0;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // 重新加载后若已成形但虚拟 CPU 未绑定，尝试重新绑定。
        if (isFormed() && virtualCpu == null && interfacePos != null) {
            bindVirtualCpu();
        }

        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            if (isFormed() && !SupercausalStructure.validate(level, worldPosition).passed) {
                SupercausalStructure.disassemble(level, worldPosition);
            }
        }
    }

    private void bindVirtualCpu() {
        if (level == null || interfacePos == null || virtualCpu != null) {
            return;
        }
        if (level.getBlockEntity(interfacePos)
                instanceof com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity me) {
            this.virtualCpu = new VirtualCraftingCPU(this, me.getMainNode(), level, interfacePos);
            VirtualCraftingCPURegistry.register(this.virtualCpu.getCluster());
        }
    }

    private void unbindVirtualCpu() {
        if (virtualCpu != null) {
            VirtualCraftingCPURegistry.unregister(virtualCpu.getCluster());
            virtualCpu.destroy();
            virtualCpu = null;
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        long encoded = data.getLong(INTERFACE_POS_TAG);
        interfacePos = encoded != 0 ? BlockPos.of(encoded) : null;
        parallelLimit = data.getInt(PARALLEL_LIMIT_TAG);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        if (interfacePos != null) {
            data.putLong(INTERFACE_POS_TAG, interfacePos.asLong());
        }
        data.putInt(PARALLEL_LIMIT_TAG, parallelLimit);
    }
}

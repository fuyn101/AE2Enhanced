package com.github.aeddddd.ae2enhanced.blockentity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.block.HyperdimensionalMeInterfaceBlock;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;

/**
 * 超维度仓储中枢 ME 接口方块实体。
 * <p>多方块成形后作为 ME 网络接入点；未成形的接口不会连接网格。</p>
 * <p>与 master 的委托设计保持一致：接口本身持有网格节点，但所有存储逻辑委托给控制器。</p>
 */
public class HyperdimensionalMeInterfaceBlockEntity extends AE2ENetworkedBlockEntity {

    @Nullable
    private BlockPos controllerPos = null;

    public HyperdimensionalMeInterfaceBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.HYPERDIMENSIONAL_ME_INTERFACE.get(), pos, blockState);
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void setControllerPos(@Nullable BlockPos controllerPos) {
        if (java.util.Objects.equals(this.controllerPos, controllerPos)) {
            return;
        }
        this.controllerPos = controllerPos;
        setChanged();

        if (level == null || level.isClientSide()) {
            return;
        }

        // 同步更新方块状态（成形/未成形）
        BlockState state = getBlockState();
        boolean formed = controllerPos != null;
        if (state.getValue(HyperdimensionalMeInterfaceBlock.FORMED) != formed) {
            level.setBlock(worldPosition, state.setValue(HyperdimensionalMeInterfaceBlock.FORMED, formed),
                    Block.UPDATE_ALL);
        }
    }

    @Override
    public void onReady() {
        // 仅在成形后才创建 AE2 网格节点，避免未成形接口也能连上线缆
        if (controllerPos != null) {
            super.onReady();
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        long encoded = data.getLong("controllerPos");
        if (encoded != 0) {
            controllerPos = BlockPos.of(encoded);
        } else {
            controllerPos = null;
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        if (controllerPos != null) {
            data.putLong("controllerPos", controllerPos.asLong());
        }
    }
}

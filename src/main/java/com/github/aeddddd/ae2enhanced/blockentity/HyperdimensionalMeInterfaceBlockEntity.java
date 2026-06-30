package com.github.aeddddd.ae2enhanced.blockentity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.block.HyperdimensionalMeInterfaceBlock;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;

/**
 * 统一多方块接口方块实体。
 * <p>记录所属控制器位置；仅在成形后创建 AE2 网格节点，作为多方块对外的网络接入点。</p>
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

        // 同步更新方块状态
        BlockState state = getBlockState();
        boolean formed = controllerPos != null;
        if (state.getValue(HyperdimensionalMeInterfaceBlock.FORMED) != formed) {
            level.setBlock(worldPosition, state.setValue(HyperdimensionalMeInterfaceBlock.FORMED, formed),
                    Block.UPDATE_ALL);
        }

        // 仅在成形时创建网格节点；拆解时销毁
        if (formed) {
            if (getMainNode().getNode() == null) {
                getMainNode().create(level, worldPosition);
                onGridConnectableSidesChanged();
            }
        } else {
            if (getMainNode().getNode() != null) {
                getMainNode().destroy();
            }
        }
    }

    @Override
    public void onReady() {
        // 未成形时不创建网格节点，避免未成形的接口也能连上线缆
        if (controllerPos != null) {
            super.onReady();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (getMainNode().getNode() != null) {
            getMainNode().destroy();
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

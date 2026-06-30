package com.github.aeddddd.ae2enhanced.blockentity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.storage.MEStorage;

import com.github.aeddddd.ae2enhanced.multiblock.IStorageHost;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;

/**
 * 超维度仓储中枢控制器方块实体。
 * <p>继承通用控制器基类，未来实现 IStorageHost 以挂载 BigInteger 外部存储。</p>
 */
public class HyperdimensionalControllerBlockEntity extends MultiblockControllerBlockEntity
        implements IStorageHost {

    @Nullable
    private MEStorage storage;
    private int validationCooldown = 0;

    public HyperdimensionalControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.HYPERDIMENSIONAL_CONTROLLER.get(), pos, blockState);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            if (isFormed() && !HyperdimensionalStructure.validate(level, worldPosition)) {
                HyperdimensionalStructure.disassemble(level, worldPosition);
            }
        }
    }

    // ---- IStorageHost（占位，Phase 1 实现完整存储） ----

    @Nullable
    @Override
    public MEStorage getStorage() {
        return storage;
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
    }
}

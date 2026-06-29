package com.github.aeddddd.ae2enhanced.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;

/**
 * 超维度仓储中枢控制器方块实体。
 * <p>Phase 1A 仅维护成形状态与周期性结构校验。</p>
 */
public class HyperdimensionalControllerBlockEntity extends AE2EBaseBlockEntity {

    private boolean formed = false;
    private int validationCooldown = 0;

    public HyperdimensionalControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.HYPERDIMENSIONAL_CONTROLLER.get(), pos, blockState);
    }

    public boolean isFormed() {
        return formed;
    }

    public void assemble() {
        if (!formed) {
            formed = true;
            setChanged();
            markForUpdate();
        }
    }

    public void disassemble() {
        if (formed) {
            formed = false;
            setChanged();
            markForUpdate();
        }
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            if (formed && !HyperdimensionalStructure.validate(level, worldPosition)) {
                HyperdimensionalStructure.disassemble(level, worldPosition);
            }
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        formed = data.getBoolean("formed");
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.putBoolean("formed", formed);
    }
}

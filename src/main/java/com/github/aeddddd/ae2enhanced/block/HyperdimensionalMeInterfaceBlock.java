package com.github.aeddddd.ae2enhanced.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalMeInterfaceBlockEntity;

/**
 * 统一多方块接口方块。
 * <p>多方块成形后成为 ME 网络接入点；未成形的接口不会连接网格。</p>
 */
public class HyperdimensionalMeInterfaceBlock extends AE2EBaseEntityBlock {

    public HyperdimensionalMeInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 10 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HyperdimensionalMeInterfaceBlockEntity(pos, state);
    }
}

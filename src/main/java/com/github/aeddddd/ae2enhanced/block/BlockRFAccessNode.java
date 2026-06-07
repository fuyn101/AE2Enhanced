package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileRFAccessNode;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * RF 访问节点方块.
 * 作为 ME 网络与外部 RF 能量网络之间的通用桥梁,独立于先进中枢平台系统.
 */
public class BlockRFAccessNode extends Block {

    public BlockRFAccessNode() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "rf_access_node");
        setTranslationKey(AE2Enhanced.MOD_ID + ".rf_access_node");
        setHardness(3.0F);
        setResistance(8.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileRFAccessNode();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRFAccessNode) {
            ((TileRFAccessNode) te).onBreak();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}

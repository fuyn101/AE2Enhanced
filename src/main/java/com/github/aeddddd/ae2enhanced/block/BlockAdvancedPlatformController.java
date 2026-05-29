package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 先进中枢平台控制器方块。
 * 平台核心，ME 网络节点，能量缓冲与分发中枢。
 */
public class BlockAdvancedPlatformController extends Block {

    public BlockAdvancedPlatformController() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "advanced_platform_controller");
        setTranslationKey(AE2Enhanced.MOD_ID + ".advanced_platform_controller");
        setHardness(-1.0F);
        setResistance(6000000.0F);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAdvancedPlatformController();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileAdvancedPlatformController) {
                ((TileAdvancedPlatformController) te).deactivatePlatform();
            }
        }
        super.breakBlock(world, pos, state);
    }
}

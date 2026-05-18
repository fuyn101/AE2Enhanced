package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * F1a：无线频道发生器方块。
 *
 * <p>类似 AE2 无线访问节点的 TileEntity 模式。具有朝向，仅背面连接线缆，
 * 提供 32 密集频道。右键打开 GUI，可将空白频道卡写入自身坐标。</p>
 */
public class BlockWirelessChannelTransmitter extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyEnum<State> STATE = PropertyEnum.create("state", State.class);

    public BlockWirelessChannelTransmitter() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "wireless_channel_transmitter");
        setTranslationKey(AE2Enhanced.MOD_ID + ".wireless_channel_transmitter");
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setDefaultState(blockState.getBaseState()
            .withProperty(FACING, EnumFacing.NORTH)
            .withProperty(STATE, State.OFF));
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, STATE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta));
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        State visualState = State.OFF;
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof TileWirelessChannelTransmitter) {
            TileWirelessChannelTransmitter tile = (TileWirelessChannelTransmitter) te;
            if (tile.isActive()) {
                visualState = State.HAS_CHANNEL;
            } else if (tile.isPowered()) {
                visualState = State.ON;
            }
        }
        return super.getActualState(state, worldIn, pos)
            .withProperty(STATE, visualState);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                             float hitX, float hitY, float hitZ, int meta,
                                             EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                 EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileWirelessChannelTransmitter) {
            ((TileWirelessChannelTransmitter) te).setForward(state.getValue(FACING));
        }
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileWirelessChannelTransmitter();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                     EntityPlayer player, EnumHand hand,
                                     EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) return false;
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileWirelessChannelTransmitter) {
                player.openGui(AE2Enhanced.instance, GuiHandler.GUI_WIRELESS_CHANNEL_TRANSMITTER,
                    world, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileWirelessChannelTransmitter) {
            TileWirelessChannelTransmitter tile = (TileWirelessChannelTransmitter) te;
            tile.dropInventory(world, pos);
        }
        super.breakBlock(world, pos, state);
    }

    public enum State implements IStringSerializable {
        OFF, ON, HAS_CHANNEL;

        @Override
        public String getName() {
            return this.name().toLowerCase();
        }
    }
}

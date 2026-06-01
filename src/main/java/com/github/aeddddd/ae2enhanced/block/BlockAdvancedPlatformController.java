package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileAdvancedPlatformController)) return false;

        TileAdvancedPlatformController controller = (TileAdvancedPlatformController) te;

        // UMC selections 载入为未绑定选区
        ItemStack held = player.getHeldItem(hand);
        if (ItemUniversalMemoryCard.isUniversalMemoryCard(held)) {
            List<ItemUniversalMemoryCard.SelectionEntry> selections = ItemUniversalMemoryCard.getSelections(held);
            if (!selections.isEmpty()) {
                Set<BlockPos> zonePositions = new HashSet<>();
                for (ItemUniversalMemoryCard.SelectionEntry entry : selections) {
                    if (entry.dim == world.provider.getDimension()) {
                        zonePositions.add(entry.pos);
                    }
                }
                if (!zonePositions.isEmpty()) {
                    controller.createZone("Imported Zone", zonePositions);
                    ItemUniversalMemoryCard.clearSelections(held);
                    player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                            "gui.ae2enhanced.umc.msg.zone_loaded", zonePositions.size()));
                }
            }
        }

        player.openGui(AE2Enhanced.instance, GuiHandler.GUI_ADVANCED_PLATFORM_CONTROLLER, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
}

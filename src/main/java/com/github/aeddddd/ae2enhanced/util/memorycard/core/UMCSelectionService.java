package com.github.aeddddd.ae2enhanced.util.memorycard.core;

import ae2.api.parts.IPart;
import ae2.api.parts.IPartHost;
import ae2.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * UMC 选取与绑定逻辑服务.
 */
public class UMCSelectionService {

    public static void handleSelect(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);

        List<ItemUniversalMemoryCard.SelectionEntry> selections = ItemUniversalMemoryCard.getSelections(stack);
        for (int i = 0; i < selections.size(); i++) {
            ItemUniversalMemoryCard.SelectionEntry entry = selections.get(i);
            if (entry.dim == world.provider.getDimension() && entry.pos.equals(pos)) {
                ItemUniversalMemoryCard.removeSelection(stack, i);
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.deselect"));
                return;
            }
        }

        if (te instanceof IPartHost) {
            IPartHost host = (IPartHost) te;
            IPart part = host.getPart(AEPartLocation.fromFacing(face));
            if (part != null) {
                String tileId = part.getClass().getName();
                int side = AEPartLocation.fromFacing(face).ordinal();
                ItemUniversalMemoryCard.addSelection(stack, new ItemUniversalMemoryCard.SelectionEntry(pos, world.provider.getDimension(), tileId, side));
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_part"));
                return;
            }
        }

        if (te != null) {
            String tileId = te.getClass().getName();
            List<BlockPos> connected = findConnectedBlocks(world, pos, te.getClass(), 64);
            for (BlockPos p : connected) {
                ItemUniversalMemoryCard.addSelection(stack, new ItemUniversalMemoryCard.SelectionEntry(p, world.provider.getDimension(), tileId, -1));
            }
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_tile", connected.size()));
        } else {
            String blockId = world.getBlockState(pos).getBlock().getRegistryName().toString();
            ItemUniversalMemoryCard.addSelection(stack, new ItemUniversalMemoryCard.SelectionEntry(pos, world.provider.getDimension(), blockId, -1));
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_block"));
        }
    }

    public static void handleBindSource(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        // 旧版 Central ME Interface 已删除,此功能在新 central/ 包重构后待实现
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_invalid_source"));
    }

    public static void handleClearBindings(EntityPlayer player, BlockPos pos) {
        // 旧版 Central ME Interface 已删除,此功能在新 central/ 包重构后待实现
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_invalid_source"));
    }

    public static void handleBindRecycler(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileMENetworkRecycler)) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_invalid_recycler"));
            return;
        }

        List<ItemUniversalMemoryCard.SelectionEntry> selections = ItemUniversalMemoryCard.getSelections(stack);
        if (selections.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.no_selections"));
            return;
        }

        TileMENetworkRecycler recycler = (TileMENetworkRecycler) te;
        int bound = 0;
        int skipped = 0;
        for (ItemUniversalMemoryCard.SelectionEntry entry : selections) {
            if (recycler.getTargetManager().getTargetCount() >= com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.recycler.maxTargets) {
                skipped++;
                continue;
            }
            TargetManager.TargetRef target = new TargetManager.TargetRef(entry.dim, entry.pos,
                    entry.side >= 0 ? EnumFacing.values()[entry.side] : EnumFacing.UP);
            if (recycler.tryBindTarget(target)) {
                bound++;
            } else {
                skipped++;
            }
        }
        recycler.markDirty();
        ItemUniversalMemoryCard.clearSelections(stack);
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_recycler_success", bound, skipped));
    }

    public static void handleClearRecyclerBindings(EntityPlayer player, BlockPos pos) {
        World world = player.world;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileMENetworkRecycler)) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_invalid_recycler"));
            return;
        }
        TileMENetworkRecycler recycler = (TileMENetworkRecycler) te;
        int count = recycler.getTargetManager().getTargetCount();
        recycler.clearTargets();
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.clear_recycler_bindings", count));
    }

    private static List<BlockPos> findConnectedBlocks(World world, BlockPos start, Class<?> tileClass, int maxCount) {
        List<BlockPos> result = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < maxCount) {
            BlockPos pos = queue.poll();
            if (!world.isBlockLoaded(pos)) continue;
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
            if (te != null && te.getClass() == tileClass) {
                result.add(pos);

                for (EnumFacing facing : EnumFacing.values()) {
                    BlockPos neighbor = pos.offset(facing);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        return result;
    }
}

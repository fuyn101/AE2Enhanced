package com.github.aeddddd.ae2enhanced.recycler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerBindingRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 处理回收节点的目标绑定事件.
 *
 * <p>玩家在绑定模式下右键点击其他方块时,将该方块绑定为回收目标.</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class RecyclerBindingHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) return;

        World world = player.world;
        BlockPos pos = event.getPos();
        EnumFacing face = event.getFace();
        if (face == null) face = EnumFacing.UP;

        UUID playerId = player.getUniqueID();

        // 检查是否点击了回收节点本身
        TileEntity clicked = world.getTileEntity(pos);
        if (clicked instanceof TileMENetworkRecycler) {
            // 由 BlockMENetworkRecycler.onBlockActivated 处理
            return;
        }

        // 查找当前玩家处于绑定模式的回收节点
        TileMENetworkRecycler binder = findBinderForPlayer(world, playerId);
        if (binder == null) return;

        // 绑定目标
        TargetManager.TargetRef target = new TargetManager.TargetRef(
                world.provider.getDimension(), pos, face);
        boolean added = binder.tryBindTarget(target);
        if (added) {
            binder.markDirty();
            player.sendMessage(new TextComponentTranslation(
                    "message.ae2enhanced.me_network_recycler.bound",
                    pos.getX(), pos.getY(), pos.getZ(), world.provider.getDimension()));
        } else {
            player.sendMessage(new TextComponentTranslation(
                    "message.ae2enhanced.me_network_recycler.already_bound"));
        }

        event.setCanceled(true);
    }

    private static TileMENetworkRecycler findBinderForPlayer(@Nonnull World world, @Nonnull UUID playerId) {
        return RecyclerBindingRegistry.findRecycler(world, playerId);
    }
}

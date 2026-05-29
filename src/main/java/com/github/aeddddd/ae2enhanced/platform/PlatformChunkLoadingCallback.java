package com.github.aeddddd.ae2enhanced.platform;

import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

/**
 * ForgeChunkManager 加载回调 —— 世界加载时重新绑定平台控制器的 chunk 票证。
 */
public class PlatformChunkLoadingCallback implements ForgeChunkManager.LoadingCallback {

    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        for (ForgeChunkManager.Ticket ticket : tickets) {
            net.minecraft.nbt.NBTTagCompound data = ticket.getModData();
            int x = data.getInteger("controllerX");
            int y = data.getInteger("controllerY");
            int z = data.getInteger("controllerZ");
            BlockPos pos = new BlockPos(x, y, z);

            net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileAdvancedPlatformController) {
                ((TileAdvancedPlatformController) te).rebindChunkTicket(ticket);
            } else {
                ForgeChunkManager.releaseTicket(ticket);
            }
        }
    }
}

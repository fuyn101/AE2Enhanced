package com.github.aeddddd.ae2enhanced.util.compat;

import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

/**
 * 装配枢纽自动上传功能的辅助类。
 * 被 MixinPatternEncoder 和 ContainerOmniTerm 共用。
 */
public class AssemblyAutoUploadHelper {

    /**
     * 检查样板是否为合成样板（crafting=1），而非处理样板。
     */
    public static boolean isCraftingPattern(ItemStack pattern) {
        if (!pattern.hasTagCompound()) return false;
        NBTTagCompound tag = pattern.getTagCompound();
        return tag.hasKey("crafting", Constants.NBT.TAG_BYTE) && tag.getByte("crafting") == 1;
    }

    /**
     * 尝试将样板自动上传到玩家附近最近的、安装了自动上传升级的装配枢纽。
     * @return true 如果上传成功
     */
    public static boolean tryUploadPattern(World world, EntityPlayer player, ItemStack pattern) {
        if (world.isRemote || pattern.isEmpty()) return false;
        if (!isCraftingPattern(pattern)) return false;

        TileAssemblyController target = findTargetController(world, player, pattern);
        if (target == null) return false;

        return target.tryAutoUploadPattern(pattern);
    }

    private static TileAssemblyController findTargetController(World world, EntityPlayer player, ItemStack pattern) {
        ControllerIndex index = ControllerIndex.get(world);
        if (index == null) return null;

        TileAssemblyController best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : index.getAll()) {
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileAssemblyController)) continue;
            TileAssemblyController controller = (TileAssemblyController) te;
            if (!controller.isFormed()) continue;
            if (!controller.hasAutoUploadUpgrade()) continue;
            if (!controller.canAcceptPattern(pattern)) continue;

            double dist = player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = controller;
            }
        }
        return best;
    }
}

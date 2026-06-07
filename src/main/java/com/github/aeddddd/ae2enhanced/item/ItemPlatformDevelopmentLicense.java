package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlatformGenerateRequest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 平台开发许可.
 * 右键触发平台生成请求,由服务端执行冲突检测与渐进放置.
 */
public class ItemPlatformDevelopmentLicense extends Item {

    public ItemPlatformDevelopmentLicense() {
        setRegistryName(AE2Enhanced.MOD_ID, "platform_development_license");
        setTranslationKey(AE2Enhanced.MOD_ID + ".platform_development_license");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            BlockPos target = new BlockPos(player.posX, player.posY, player.posZ);
            AE2Enhanced.network.sendToServer(new PacketPlatformGenerateRequest(target));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}

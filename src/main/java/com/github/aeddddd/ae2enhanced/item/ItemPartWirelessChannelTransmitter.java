package com.github.aeddddd.ae2enhanced.item;

import appeng.api.AEApi;
import appeng.api.parts.IPartItem;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.part.PartWirelessChannelTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * F1a：无线频道发生器的物品形态。
 */
public class ItemPartWirelessChannelTransmitter extends Item implements IPartItem<PartWirelessChannelTransmitter> {

    public ItemPartWirelessChannelTransmitter() {
        this.setMaxStackSize(64);
        this.setTranslationKey("ae2enhanced.part_wireless_channel_transmitter");
        this.setRegistryName("ae2enhanced", "part_wireless_channel_transmitter");
        this.setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Nullable
    @Override
    public PartWirelessChannelTransmitter createPartFromItemStack(ItemStack is) {
        return new PartWirelessChannelTransmitter(is);
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos,
                                       @Nonnull EnumHand hand, @Nonnull EnumFacing side,
                                       float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, side, player, hand, world);
    }
}

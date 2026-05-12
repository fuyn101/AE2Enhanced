package com.github.aeddddd.ae2enhanced.item;

import appeng.api.AEApi;
import appeng.api.parts.IPartItem;
import com.github.aeddddd.ae2enhanced.part.PartUniversalExportBus;
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
 * E1b：通用输出总线的物品形态。
 * 实现 IPartItem，放置到 AE2 线缆总线上。
 */
public class ItemPartUniversalExportBus extends Item implements IPartItem<PartUniversalExportBus> {

    public ItemPartUniversalExportBus() {
        this.setMaxStackSize(64);
        this.setTranslationKey("ae2enhanced.part_universal_export_bus");
        this.setRegistryName("ae2enhanced", "part_universal_export_bus");
    }

    @Nullable
    @Override
    public PartUniversalExportBus createPartFromItemStack(ItemStack is) {
        return new PartUniversalExportBus(is);
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos,
                                       @Nonnull EnumHand hand, @Nonnull EnumFacing side,
                                       float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, side, player, hand, world);
    }
}

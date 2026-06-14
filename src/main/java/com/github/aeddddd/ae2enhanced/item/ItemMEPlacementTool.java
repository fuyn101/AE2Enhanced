package com.github.aeddddd.ae2enhanced.item;

import appeng.api.features.INetworkEncodable;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ME 放置工具 —— 从 ME 网络直接放置方块、AE2 Part、Facade。
 * 通过 AE2 安全终端绑定。
 */
public class ItemMEPlacementTool extends Item implements INetworkEncodable {

    public ItemMEPlacementTool() {
        setMaxStackSize(1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setTranslationKey("ae2enhanced.me_placement_tool");
        setRegistryName("me_placement_tool");
    }

    // ==================== INetworkEncodable ====================

    @Override
    public String getEncryptionKey(ItemStack item) {
        return SecurityTerminalBindingHelper.getEncryptionKey(item);
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        SecurityTerminalBindingHelper.setEncryptionKey(item, encKey);
    }

    // ==================== Item Use ====================

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;

        ItemStack stack = player.getHeldItem(hand);

        // Shift + 右键安全终端方块：由安全终端 GUI 槽位处理绑定，此处无需额外逻辑。
        // 普通右键执行放置
        if (!player.isSneaking()) {
            if (PlacementToolHelper.placeSingle(player, world, pos, facing, hand, stack, hitX, hitY, hitZ)) {
                return EnumActionResult.SUCCESS;
            }
            return EnumActionResult.FAIL;
        }

        return EnumActionResult.PASS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        // 仅当未指向方块时打开 GUI
        RayTraceResult ray = rayTrace(world, player, false);
        if (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        if (!world.isRemote) {
            int slot = player.inventory.currentItem;
            if (hand == EnumHand.OFF_HAND) {
                slot = 40;
            }
            player.openGui(AE2Enhanced.instance, GuiHandler.GUI_PLACEMENT_TOOL, world, slot, 0, 0);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    // ==================== Tooltip ====================

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        if (SecurityTerminalBindingHelper.isLinked(stack)) {
            tooltip.add(TextFormatting.DARK_AQUA + "● " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_placement_tool.linked"));
        } else {
            tooltip.add(TextFormatting.RED + "● " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_placement_tool.unlinked"));
        }

        PlacementConfig config = new PlacementConfig(stack);
        ItemStack selected = config.getStackInSlot(config.getSelectedSlot());
        if (!selected.isEmpty()) {
            tooltip.add(I18n.format("item.ae2enhanced.me_placement_tool.selected",
                    selected.getDisplayName(), config.getPlacementCount()));
        } else {
            tooltip.add(I18n.format("item.ae2enhanced.me_placement_tool.no_selection"));
        }
    }
}

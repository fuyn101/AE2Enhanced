package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPlacementSelectPresetHandler implements IMessageHandler<PacketPlacementSelectPreset, IMessage> {

    @Override
    public IMessage onMessage(PacketPlacementSelectPreset message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ItemStack stack = player.getHeldItemMainhand();
            if (!(stack.getItem() instanceof ItemMEPlacementTool) && !(stack.getItem() instanceof ItemAdvancedMEOmniTool)) {
                stack = player.getHeldItemOffhand();
                if (!(stack.getItem() instanceof ItemMEPlacementTool) && !(stack.getItem() instanceof ItemAdvancedMEOmniTool)) {
                    return;
                }
            }

            PlacementConfig config = new PlacementConfig(stack);
            int slot = message.getSlot();

            if (slot >= 0 && slot < PlacementConfig.MAX_PRESETS) {
                config.setSelectedSlot(slot);
            } else if (slot == PlacementConfig.MAX_PRESETS) {
                // 选取当前目标
                RayTraceResult ray = player.rayTrace(5.0, 1.0f);
                if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) return;

                net.minecraft.block.state.IBlockState state = player.world.getBlockState(ray.getBlockPos());
                ItemStack pick = state.getBlock().getItem(player.world, ray.getBlockPos(), state);
                if (pick == null || pick.isEmpty()) return;

                int targetSlot = config.getSelectedSlot();
                if (targetSlot < 0 || targetSlot >= PlacementConfig.MAX_PRESETS || !config.getStackInSlot(targetSlot).isEmpty()) {
                    targetSlot = config.getFirstEmptySlot();
                    if (targetSlot < 0) targetSlot = 0;
                }
                config.setStackInSlot(targetSlot, pick);
                config.setSelectedSlot(targetSlot);
            }
        });
        return null;
    }
}

package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.client.gui.GuiPlacementRadialMenu;
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
            } else if (slot == GuiPlacementRadialMenu.SLOT_EMPTY) {
                config.setSelectedSlot(-1);
            } else if (slot == PlacementConfig.MAX_PRESETS) {
                // 中键选取当前准星目标
                RayTraceResult ray = player.rayTrace(5.0, 1.0f);
                if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) return;

                net.minecraft.block.state.IBlockState state = player.world.getBlockState(ray.getBlockPos());
                ItemStack pick = state.getBlock().getItem(player.world, ray.getBlockPos(), state);
                if (pick == null || pick.isEmpty()) return;

                // 合并同种选取：如果已有相同物品（忽略数量），直接选中该槽
                int existing = -1;
                for (int i = 0; i < PlacementConfig.MAX_PRESETS; i++) {
                    ItemStack p = config.getStackInSlot(i);
                    if (!p.isEmpty() && ItemStack.areItemStacksEqual(p, pick)) {
                        existing = i;
                        break;
                    }
                }

                if (existing >= 0) {
                    config.setSelectedSlot(existing);
                } else {
                    int targetSlot = config.getSelectedSlot();
                    if (targetSlot < 0 || targetSlot >= PlacementConfig.MAX_PRESETS || !config.getStackInSlot(targetSlot).isEmpty()) {
                        targetSlot = config.getFirstEmptySlot();
                    }
                    if (targetSlot < 0) targetSlot = 0; // 满了则覆盖第 0 槽
                    config.setStackInSlot(targetSlot, pick);
                    config.setSelectedSlot(targetSlot);
                }
            }
        });
        return null;
    }
}

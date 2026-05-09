package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.network.PacketMEMonitorableAction;
import mekanism.api.gas.IGasItem;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：处理终端中的流体/气体假物品点击。
 * 核心原则：所有假物品的非中键点击一律拦截，不再调用原生 AE2 逻辑。
 *
 * 参考 ae2fc MixinGuiMEMonitorable.func_184098_a 的兜底拦截策略。
 */
@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 1099)
public class MixinGuiMEMonitorableHandleClick {

    @Inject(method = "func_184098_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onHandleMouseClick(Slot slot, int slotId, int mouseButton, ClickType clickType, CallbackInfo ci) {
        if (com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED) return;
        if (!(slot instanceof SlotME)) return;

        GuiContainer screen = (GuiContainer) (Object) this;
        ItemStack mouseItem = screen.mc.player.inventory.getItemStack();
        SlotME s = (SlotME) slot;
        IAEItemStack aeStack = s.getAEStack();
        if (aeStack == null) return;

        boolean isFluid = aeStack.getItem() == com.github.aeddddd.ae2enhanced.ModItems.FLUID_DROP;
        boolean isGas = com.github.aeddddd.ae2enhanced.ModItems.GAS_DROP != null
                && aeStack.getItem() == com.github.aeddddd.ae2enhanced.ModItems.GAS_DROP;

        if (!isFluid && !isGas) return;
        if (mouseButton == 2) return; // 中键请求合成，走原生逻辑

        // 手持物品不为空且是左键：尝试容器交互
        if (!mouseItem.isEmpty() && mouseButton == 0) {
            if (isFluid && mouseItem.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                NBTTagCompound nbt = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
                nbt.setBoolean("shift", org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT));
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(
                        PacketMEMonitorableAction.FLUID_WORK, nbt));
                ci.cancel();
                return;
            }
            if (isGas && mouseItem.getItem() instanceof IGasItem) {
                NBTTagCompound nbt = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
                nbt.setBoolean("shift", org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT));
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(
                        PacketMEMonitorableAction.GAS_WORK, nbt));
                ci.cancel();
                return;
            }
        }

        // 空手或非容器物品点击：发送 OPERATE 网络包
        if (mouseItem.isEmpty()) {
            if (isFluid) {
                NBTTagCompound nbt = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
                nbt.setBoolean("shift", org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT));
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(
                        PacketMEMonitorableAction.FLUID_OPERATE, nbt));
            }
            // 气体空手操作暂不实现（ae2fc 也未完整实现）
        }

        // 兜底：所有假物品的非中键点击一律取消，不走原生逻辑
        ci.cancel();
    }
}

package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.network.PacketMEMonitorableAction;
import com.github.aeddddd.ae2enhanced.util.Ae2fcCompat;
import com.github.aeddddd.ae2enhanced.util.FakeItemRegister;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：处理终端中的流体/气体假物品点击。
 *
 * 复现 ae2fc MixinGuiMEMonitorable.func_184098_a 的精确逻辑：
 * 1. 手持 fluid container 左键任意 SlotME（不限于流体槽位）即可排空。
 *    targetFluid 只有在槽位是流体 drop 时才设置，否则为 null → 服务器强制 drain。
 * 2. 气体逻辑保持不变。
 */
@Mixin(value = AEBaseGui.class, remap = false, priority = 1099)
public class MixinGuiMEMonitorableHandleClick {

    @Inject(method = "func_184098_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onHandleMouseClick(Slot slot, int slotId, int mouseButton, ClickType clickType, CallbackInfo ci) {
        if (Ae2fcCompat.AE2FC_LOADED) return;
        if (!(slot instanceof SlotME)) return;
        if (mouseButton != 0) return; // 只处理左键

        GuiContainer screen = (GuiContainer) (Object) this;
        ItemStack mouseItem = screen.mc.player.inventory.getItemStack();
        SlotME s = (SlotME) slot;
        IAEItemStack aeStack = s.getAEStack();

        // 手持物品不为空：尝试容器交互（fill / drain）
        if (!mouseItem.isEmpty()) {
            boolean isFluid = aeStack != null && aeStack.getItem() == ModItems.FLUID_DROP;
            boolean hasFluidCap = mouseItem.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            boolean hasFluidInHand = hasFluidCap && getFluidFromItem(mouseItem) != null;

            if (hasFluidCap && (isFluid || hasFluidInHand)) {
                FluidStack fluid = isFluid ? FakeItemRegister.getStack(aeStack) : null;
                NBTTagCompound nbt = fluid != null ? fluid.writeToNBT(new NBTTagCompound()) : new NBTTagCompound();
                nbt.setBoolean("shift", Keyboard.isKeyDown(Keyboard.KEY_LSHIFT));
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(
                        PacketMEMonitorableAction.FLUID_WORK, nbt));
                ci.cancel();
                return;
            }

            boolean isGas = aeStack != null && aeStack.getItem() == ModItems.GAS_DROP;
            boolean isGasItem = mouseItem.getItem() instanceof IGasItem;
            boolean hasGasInHand = isGasItem && getGasFromItem(mouseItem) != null;
            if (ModItems.GAS_DROP != null && isGasItem && (isGas || hasGasInHand)) {
                GasStack gas = isGas ? FakeItemRegister.getStack(aeStack) : null;
                NBTTagCompound nbt = gas != null ? gas.write(new NBTTagCompound()) : new NBTTagCompound();
                nbt.setBoolean("shift", Keyboard.isKeyDown(Keyboard.KEY_LSHIFT));
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(
                        PacketMEMonitorableAction.GAS_WORK, nbt));
                ci.cancel();
                return;
            }
        }

        // 空手点击：发送 OPERATE 网络包
        if (mouseItem.isEmpty() && aeStack != null) {
            boolean isFluid = aeStack.getItem() == ModItems.FLUID_DROP;
            boolean isGas = ModItems.GAS_DROP != null && aeStack.getItem() == ModItems.GAS_DROP;

            if (isFluid || isGas) {
                NBTTagCompound nbt = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
                nbt.setBoolean("shift", Keyboard.isKeyDown(Keyboard.KEY_LSHIFT));
                byte type = isFluid ? PacketMEMonitorableAction.FLUID_OPERATE : PacketMEMonitorableAction.GAS_OPERATE;
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(type, nbt));
            }
        }

        // 兜底：所有假物品的非中键点击一律取消，不走原生逻辑
        if (aeStack != null && (aeStack.getItem() == ModItems.FLUID_DROP
                || (ModItems.GAS_DROP != null && aeStack.getItem() == ModItems.GAS_DROP))) {
            ci.cancel();
        }
    }

    private static FluidStack getFluidFromItem(ItemStack stack) {
        if (!stack.isEmpty() && stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler != null) {
                FluidStack drained = handler.drain(Integer.MAX_VALUE, false);
                if (drained != null && drained.amount > 0) {
                    return drained;
                }
            }
        }
        return null;
    }

    private static GasStack getGasFromItem(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof IGasItem) {
            return ((IGasItem) stack.getItem()).getGas(stack);
        }
        return null;
    }
}

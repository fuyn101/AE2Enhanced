package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.network.PacketMEMonitorableAction;
import com.github.aeddddd.ae2enhanced.util.Ae2fcCompat;
import com.github.aeddddd.ae2enhanced.util.FakeEssentias;
import com.github.aeddddd.ae2enhanced.util.FakeItemRegister;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：在标准 AE2 物品终端中拦截流体/气体/源质假物品的点击。
 *
 * 复刻 ae2fc MixinGuiMEMonitorable.func_184098_a 的精确实现：
 * - targeting AEBaseGui（因为 AE2-UEL 保留 obfuscated 名，且我们无法在源码中继承 AEBaseMEGui 调用 super.func_184098_a）
 * - 使用 remap=false + obfuscated 名 "func_184098_a"
 * - 手持容器左键：发送 FLUID_WORK / GAS_WORK 网络包
 * - 空手持假物品：ae2fc 仅处理流体（发送 FLUID_OPERATE），气体直接 return
 * - 完整保留 ae2fc 的条件检查：mouseButton != 2、craftable、ctrl 键状态
 */
@Mixin(value = AEBaseGui.class, remap = false, priority = 1099)
public class MixinGuiMEMonitorableClick {

    @Inject(method = "func_184098_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onHandleMouseClick(Slot slot, int slotId, int mouseButton, ClickType clickType, CallbackInfo ci) {
        if (Ae2fcCompat.AE2FC_LOADED) {
            return;
        }

        if (!(slot instanceof SlotME)) {
            return;
        }

        SlotME s = (SlotME) slot;
        ItemStack mouseItem = ((AEBaseGui) (Object) this).mc.player.inventory.getItemStack();

        // ========== 手持物品左键点击 ==========
        if (!mouseItem.isEmpty() && mouseButton == 0) {
            boolean isFluid = s.getAEStack() != null && s.getAEStack().getItem() == ModItems.FLUID_DROP;
            boolean hasFluidCap = mouseItem.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            boolean hasFluidInHand = hasFluidCap && getFluidFromItem(mouseItem) != null;

            if (hasFluidCap && (isFluid || hasFluidInHand)) {
                FluidStack fluid = isFluid ? FakeItemRegister.getStack(s.getAEStack()) : null;
                NBTTagCompound nbt = fluid != null ? fluid.writeToNBT(new NBTTagCompound()) : new NBTTagCompound();
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.FLUID_WORK, nbt));
                ci.cancel();
                return;
            }

            if (ModItems.GAS_DROP != null && mek$handleMouseClick(s, mouseItem)) {
                ci.cancel();
                return;
            }

            if (ModItems.ESSENTIA_DROP != null && FakeEssentias.isEssentiaFakeItem(mouseItem)) {
                ci.cancel();
                return;
            }
        }

        // ========== 空手持假物品点击 ==========
        // ae2fc 精确条件：mouseButton != 2 && (!craftable || mouseButton != 0 || stackSize != 0 && !ctrlDown)
        if (s.getAEStack() != null
                && (s.getAEStack().getItem() == ModItems.FLUID_DROP
                    || (ModItems.GAS_DROP != null && s.getAEStack().getItem() == ModItems.GAS_DROP))
                && mouseButton != 2
                && (!s.getAEStack().isCraftable()
                    || mouseButton != 0
                    || (s.getAEStack().getStackSize() != 0L && !GuiScreen.isCtrlKeyDown()))) {

            if (s.getAEStack().getItem() == ModItems.FLUID_DROP) {
                NBTTagCompound nbt = s.getAEStack().getDefinition().writeToNBT(new NBTTagCompound());
                nbt.setBoolean("shift", GuiScreen.isShiftKeyDown());
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.FLUID_OPERATE, nbt));
            } else if (ModItems.GAS_DROP != null && s.getAEStack().getItem() == ModItems.GAS_DROP) {
                NBTTagCompound nbt = s.getAEStack().getDefinition().writeToNBT(new NBTTagCompound());
                nbt.setBoolean("shift", GuiScreen.isShiftKeyDown());
                AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.GAS_OPERATE, nbt));
            }
            ci.cancel();
            return;
        }
    }

    /**
     * 气体点击处理（对应 ae2fc 的 mek$handleMouseClick）。
     */
    private boolean mek$handleMouseClick(SlotME s, ItemStack mouseItem) {
        boolean isGas = s.getAEStack() != null && s.getAEStack().getItem() == ModItems.GAS_DROP;
        if (isGas || getGasFromItem(mouseItem) != null) {
            Object gas = isGas ? FakeItemRegister.getStack(s.getAEStack()) : null;
            NBTTagCompound nbt = new NBTTagCompound();
            if (gas != null) {
                try {
                    nbt = (NBTTagCompound) gas.getClass().getMethod("write", NBTTagCompound.class).invoke(gas, new NBTTagCompound());
                } catch (Exception e) {
                    // ignore
                }
            }
            AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.GAS_WORK, nbt));
            return true;
        }
        return false;
    }

    private static FluidStack getFluidFromItem(ItemStack stack) {
        if (!stack.isEmpty() && stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            net.minecraftforge.fluids.capability.IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler != null) {
                FluidStack drained = handler.drain(Integer.MAX_VALUE, false);
                if (drained != null && drained.amount > 0) {
                    return drained;
                }
            }
        }
        return null;
    }

    private static Object getGasFromItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        try {
            Class<?> iGasItemClass = Class.forName("mekanism.api.gas.IGasItem");
            if (iGasItemClass.isInstance(stack.getItem())) {
                return iGasItemClass.getMethod("getGas", ItemStack.class).invoke(stack.getItem(), stack);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}

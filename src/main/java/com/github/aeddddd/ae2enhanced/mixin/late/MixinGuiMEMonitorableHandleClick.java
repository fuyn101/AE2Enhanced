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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：处理终端中的直接容器提取/注入和假物品拖入阻止。
 * 本 mixin 位于 mixins.ae2enhanced.late.json 中，无条件加载。
 */
@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 1099)
public class MixinGuiMEMonitorableHandleClick {

    @Inject(method = "func_184098_a", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onHandleMouseClick(Slot slot, int slotId, int mouseButton, ClickType clickType, CallbackInfo ci) {
        if (!(slot instanceof SlotME) || mouseButton == 2) {
            return;
        }

        GuiContainer screen = (GuiContainer) (Object) this;
        ItemStack mouseItem = screen.mc.player.inventory.getItemStack();
        IAEItemStack aeStack = ((SlotME) slot).getAEStack();
        if (aeStack == null) return;
        ItemStack slotStack = aeStack.createItemStack();

        // 1. 阻止将假物品拖入终端
        if (!mouseItem.isEmpty()) {
            if (ItemFluidDrop.isFluidDrop(mouseItem) || ItemGasDrop.isGasDrop(mouseItem)) {
                ci.cancel();
                return;
            }
        }

        // 2. 手持流体容器点击流体假物品槽位
        if (!com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED
                && !mouseItem.isEmpty() && mouseItem.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)
                && ItemFluidDrop.isFluidDrop(slotStack)) {
            FluidStack fluid = ItemFluidDrop.getFluidStack(slotStack);
            NBTTagCompound nbt = fluid != null ? fluid.writeToNBT(new NBTTagCompound()) : new NBTTagCompound();
            AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(
                    PacketMEMonitorableAction.FLUID_WORK, nbt, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)));
            ci.cancel();
            return;
        }

        // 3. 手持气体容器点击气体假物品槽位
        if (!com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED
                && !mouseItem.isEmpty() && mouseItem.getItem() instanceof IGasItem
                && ItemGasDrop.isGasDrop(slotStack)) {
            String gasName = ItemGasDrop.getGasName(slotStack);
            NBTTagCompound nbt = new NBTTagCompound();
            if (gasName != null) nbt.setString("gasName", gasName);
            AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(
                    PacketMEMonitorableAction.GAS_WORK, nbt, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)));
            ci.cancel();
            return;
        }

        // 4. 空手点击流体假物品槽位 -> 获取装满的桶
        if (!com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED
                && mouseItem.isEmpty() && ItemFluidDrop.isFluidDrop(slotStack)) {
            FluidStack fluid = ItemFluidDrop.getFluidStack(slotStack);
            NBTTagCompound nbt = fluid != null ? fluid.writeToNBT(new NBTTagCompound()) : new NBTTagCompound();
            AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(
                    PacketMEMonitorableAction.FLUID_OPERATE, nbt, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)));
            ci.cancel();
            return;
        }

        // 5. 空手点击气体假物品槽位 -> 获取装满的气体容器（暂不实现，ae2fc 也未完整实现）
        if (!com.github.aeddddd.ae2enhanced.util.Ae2fcCompat.AE2FC_LOADED
                && mouseItem.isEmpty() && ItemGasDrop.isGasDrop(slotStack)) {
            // 气体空手操作需要已知容器类型，Mekanism 没有通用空容器，暂不发送
            ci.cancel();
        }
    }
}

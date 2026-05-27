package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import com.github.aeddddd.ae2enhanced.util.fakeitem.EssentiaFakeItemChecks;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * E2a：在标准 AE2 物品终端中渲染流体/气体/源质假物品的 tooltip。
 *
 * 复刻 ae2fc MixinGuiMEMonitorable.func_191948_b 的精确实现：
 * - targeting GuiMEMonitorable（与 ae2fc 一致）
 * - 使用 remap=false + obfuscated 名 "func_191948_b"
 *   （AE2-UEL jar 保留 obfuscated 名，且 AEBaseGui 在开发环境中无 MCP 名 renderHoveredToolTip）
 * - @Inject at HEAD + cancellable，替代错误的 @Intrinsic
 */
@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 1099)
public abstract class MixinGuiMEMonitorableTooltip {

    @Inject(method = "func_191948_b", at = @At("HEAD"), cancellable = true)
    public void ae2enhanced$onRenderHoveredToolTip(int mouseX, int mouseY, CallbackInfo ci) {
        if (Ae2fcCompat.AE2FC_LOADED) {
            return;
        }

        GuiContainer screen = (GuiContainer) (Object) this;
        Slot slot = screen.getSlotUnderMouse();
        if (slot instanceof SlotME && ((SlotME) slot).isEnabled()) {
            ItemStack mouseItem = Minecraft.getMinecraft().player.inventory.getItemStack();
            if (!mouseItem.isEmpty()) {
                if (renderContainerToolTip(screen, mouseX, mouseY)) {
                    ci.cancel();
                    return;
                }
            } else {
                IAEItemStack aeStack = ((SlotME) slot).getAEStack();
                if (aeStack != null) {
                    if (aeStack.getItem() == ModItems.FLUID_DROP) {
                        if (rendererFluid(screen, aeStack, mouseX, mouseY)) {
                            ci.cancel();
                            return;
                        }
                    }
                    if (ModItems.GAS_DROP != null && aeStack.getItem() == ModItems.GAS_DROP) {
                        if (rendererGas(screen, aeStack, mouseX, mouseY)) {
                            ci.cancel();
                            return;
                        }
                    }
                    if (ModItems.ESSENTIA_DROP != null && aeStack.getItem() == ModItems.ESSENTIA_DROP) {
                        if (rendererEssentia(screen, aeStack, mouseX, mouseY)) {
                            ci.cancel();
                            return;
                        }
                    }
                }
            }
        }
    }

    /* ================================================================ */
    /*  流体 Tooltip Renderer                                           */
    /* ================================================================ */
    private static boolean rendererFluid(GuiContainer gui, IAEItemStack aeStack, int mouseX, int mouseY) {
        if (aeStack == null || aeStack.getItem() != ModItems.FLUID_DROP) return false;

        IAEFluidStack fluidStack = FakeItemRegister.getAEStack(aeStack.copy().setStackSize(1));
        if (fluidStack == null) return false;
        fluidStack.setStackSize(aeStack.getStackSize());

        List<String> tooltip = new ArrayList<>();
        FluidStack fs = fluidStack.getFluidStack();
        tooltip.add(fs != null ? fs.getLocalizedName() : "Unknown Fluid");

        String modName = getFluidModName(fs);
        if (!modName.isEmpty()) {
            tooltip.add(TextFormatting.BLUE.toString() + TextFormatting.ITALIC + modName);
        }

        boolean shift = GuiScreen.isShiftKeyDown();
        long amount = fluidStack.getStackSize();
        String formattedAmount = shift
                ? NumberFormat.getNumberInstance(Locale.US).format(amount) + " mB"
                : NumberFormat.getNumberInstance(Locale.US).format((double) amount / 1000.0) + " B";
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.appliedenergistics2.StoredFluids") + " : " + formattedAmount);

        if (aeStack.isCraftable()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
        }

        gui.drawHoveringText(tooltip, mouseX, mouseY);
        return true;
    }

    /* ================================================================ */
    /*  气体 Tooltip Renderer                                           */
    /* ================================================================ */
    private static boolean rendererGas(GuiContainer gui, IAEItemStack aeStack, int mouseX, int mouseY) {
        if (aeStack == null || aeStack.getItem() != ModItems.GAS_DROP) return false;

        try {
            Object gasStack = FakeItemRegister.getAEStack(aeStack.copy().setStackSize(1));
            if (gasStack == null) return false;
            java.lang.reflect.Method setStackSize = gasStack.getClass().getMethod("setStackSize", long.class);
            setStackSize.invoke(gasStack, aeStack.getStackSize());

            java.lang.reflect.Method getGas = gasStack.getClass().getMethod("getGas");
            Object gas = getGas.invoke(gasStack);
            if (gas == null) return false;
            java.lang.reflect.Method getLocalizedName = gas.getClass().getMethod("getLocalizedName");
            String gasName = (String) getLocalizedName.invoke(gas);

            List<String> tooltip = new ArrayList<>();
            tooltip.add(gasName);
            tooltip.add(TextFormatting.BLUE.toString() + TextFormatting.ITALIC + "Mekanism");

            boolean shift = GuiScreen.isShiftKeyDown();
            java.lang.reflect.Method getStackSize = gasStack.getClass().getMethod("getStackSize");
            long amount = (long) getStackSize.invoke(gasStack);
            String formattedAmount = shift
                    ? NumberFormat.getNumberInstance(Locale.US).format(amount) + " mB"
                    : NumberFormat.getNumberInstance(Locale.US).format((double) amount / 1000.0) + " B";
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.stored") + " : " + formattedAmount);

            if (aeStack.isCraftable()) {
                tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
            }

            gui.drawHoveringText(tooltip, mouseX, mouseY);
            return true;
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to render gas tooltip", e);
            return false;
        }
    }

    /* ================================================================ */
    /*  源质 Tooltip Renderer                                           */
    /* ================================================================ */
    private static boolean rendererEssentia(GuiContainer gui, IAEItemStack aeStack, int mouseX, int mouseY) {
        if (aeStack == null || ModItems.ESSENTIA_DROP == null || aeStack.getItem() != ModItems.ESSENTIA_DROP) {
            return false;
        }
        String aspectTag = EssentiaFakeItemChecks.tryGetAspectTag(aeStack.createItemStack());
        String aspectName = aspectTag != null ? aspectTag : "Unknown";
        List<String> tooltip = new ArrayList<>();
        tooltip.add("\u00A7b" + "Essentia: " + aspectName);
        long amount = aeStack.getStackSize();
        tooltip.add("\u00A77" + "Amount: " + amount);
        gui.drawHoveringText(tooltip, mouseX, mouseY);
        return true;
    }

    /* ================================================================ */
    /*  手持容器 Tooltip Renderer（流体容器 + 气体容器）                   */
    /* ================================================================ */
    private static boolean renderContainerToolTip(GuiContainer gui, int mouseX, int mouseY) {
        ItemStack mouseItem = gui.mc.player.inventory.getItemStack();
        if (mouseItem.isEmpty()) return false;

        // 流体容器
        FluidStack fluidInContainer = getFluidFromItem(mouseItem);
        if (fluidInContainer != null) {
            renderInjectTooltip(gui, mouseX, mouseY, fluidInContainer.getLocalizedName(), mouseItem.getDisplayName());
            return true;
        }

        // 气体容器（反射，避免硬依赖 Mekanism）
        try {
            Class<?> iGasItemClass = Class.forName("mekanism.api.gas.IGasItem");
            if (iGasItemClass.isInstance(mouseItem.getItem())) {
                Object gas = iGasItemClass.getMethod("getGas", ItemStack.class).invoke(mouseItem.getItem(), mouseItem);
                if (gas != null) {
                    int amount = (int) gas.getClass().getField("amount").get(gas);
                    if (amount > 0) {
                        Object gasObj = gas.getClass().getMethod("getGas").invoke(gas);
                        String gasName = (String) gasObj.getClass().getMethod("getLocalizedName").invoke(gasObj);
                        renderInjectTooltip(gui, mouseX, mouseY, gasName, mouseItem.getDisplayName());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to get gas from container in tooltip", e);
        }

        return false;
    }

    private static void renderInjectTooltip(GuiContainer gui, int mouseX, int mouseY,
                                             String contentName, String itemName) {
        String injectKey = "gui.appliedenergistics2.security.inject.name";
        String s = " : " + I18n.format(injectKey) + " " + TextFormatting.RESET;
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.DARK_GRAY + net.minecraft.client.settings.GameSettings.getKeyDisplayString(-100) + s + contentName);
        tooltip.add(TextFormatting.DARK_GRAY + net.minecraft.client.settings.GameSettings.getKeyDisplayString(-99) + s + itemName);
        gui.drawHoveringText(tooltip, mouseX, mouseY);
    }

    private static FluidStack getFluidFromItem(ItemStack stack) {
        if (!stack.isEmpty() && stack.hasCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            net.minecraftforge.fluids.capability.IFluidHandlerItem handler = stack.getCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler != null) {
                FluidStack drained = handler.drain(Integer.MAX_VALUE, false);
                if (drained != null && drained.amount > 0) {
                    return drained;
                }
            }
        }
        return null;
    }

    private static String getFluidModName(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return "";
        String defaultName = net.minecraftforge.fluids.FluidRegistry.getDefaultFluidName(fluidStack.getFluid());
        if (defaultName == null) return "";
        net.minecraft.util.ResourceLocation rl = new net.minecraft.util.ResourceLocation(defaultName);
        String modid = rl.getNamespace();
        net.minecraftforge.fml.common.ModContainer mod = net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get(modid);
        return mod != null ? mod.getName() : modid;
    }
}

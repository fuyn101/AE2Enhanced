package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.me.SlotME;
import appeng.fluids.util.AEFluidStack;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.util.Ae2fcCompat;
import com.github.aeddddd.ae2enhanced.util.FakeItemRegister;
import com.mekeng.github.common.me.data.IAEGasStack;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * E2a：在标准 AE2 物品终端中渲染流体/气体假物品的 tooltip。
 *
 * 复现 ae2fc MixinGuiMEMonitorable.func_191948_b 的精确实现：
 * - 使用 @Intrinsic 在 GuiMEMonitorable 中覆盖 func_191948_b
 * - 继承 GuiContainer 以便调用 super.renderHoveredToolTip（MCP 名）
 * - 空手悬停流体槽位：rendererFluid（通过 FakeItemRegister.getAEStack 获取 IAEFluidStack）
 * - 空手悬停气体槽位：rendererGas（通过 FakeItemRegister.getAEStack 获取 IAEGasStack）
 * - 手持容器悬停：renderContainerToolTip（流体 + 气体）
 *
 * 扩展性：后续添加源质(Essentia)等其他假物品类型时，只需在 func_191948_b 的
 * 空手分支中添加对应的 renderer 调用即可（参照 rendererFluid/rendererGas 模式）。
 */
@Mixin(value = GuiMEMonitorable.class, remap = false, priority = 1099)
public abstract class MixinGuiMEMonitorableFluid extends GuiContainer {

    public MixinGuiMEMonitorableFluid(Container container) {
        super(container);
    }

    @Intrinsic
    public void func_191948_b(int mouseX, int mouseY) {
        if (Ae2fcCompat.AE2FC_LOADED) {
            super.renderHoveredToolTip(mouseX, mouseY);
            return;
        }

        Slot slot = this.getSlotUnderMouse();
        if (slot instanceof SlotME && ((SlotME) slot).isEnabled()) {
            ItemStack mouseItem = this.mc.player.inventory.getItemStack();
            if (!mouseItem.isEmpty()) {
                if (renderContainerToolTip(this, mouseX, mouseY)) {
                    return;
                }
            } else {
                IAEItemStack aeStack = ((SlotME) slot).getAEStack();
                // ========== EXTENSION POINT ==========
                // 后续添加新的假物品类型（如 Essentia）时，在此链中增加判断：
                // if (aeStack != null && aeStack.getItem() == ModItems.ESSENTIA_DROP) {
                //     if (rendererEssentia(this, aeStack, mouseX, mouseY)) return;
                // }
                // =====================================
                if (aeStack != null && aeStack.getItem() == ModItems.FLUID_DROP) {
                    if (rendererFluid(this, aeStack, mouseX, mouseY)) {
                        return;
                    }
                }
                if (ModItems.GAS_DROP != null && aeStack != null && aeStack.getItem() == ModItems.GAS_DROP) {
                    if (rendererGas(this, aeStack, mouseX, mouseY)) {
                        return;
                    }
                }
            }
        }
        super.renderHoveredToolTip(mouseX, mouseY);
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
        tooltip.add(fluidStack.getFluidStack().getLocalizedName());

        String modName = getFluidModName(fluidStack.getFluidStack());
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

        IAEGasStack gasStack = FakeItemRegister.getAEStack(aeStack.copy().setStackSize(1));
        if (gasStack == null) return false;
        gasStack.setStackSize(aeStack.getStackSize());

        List<String> tooltip = new ArrayList<>();
        tooltip.add(gasStack.getGas().getLocalizedName());

        // 气体目前均来自 Mekanism
        String modName = "Mekanism";
        tooltip.add(TextFormatting.BLUE.toString() + TextFormatting.ITALIC + modName);

        boolean shift = GuiScreen.isShiftKeyDown();
        long amount = gasStack.getStackSize();
        String formattedAmount = shift
                ? NumberFormat.getNumberInstance(Locale.US).format(amount) + " mB"
                : NumberFormat.getNumberInstance(Locale.US).format((double) amount / 1000.0) + " B";
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.stored") + " : " + formattedAmount);

        if (aeStack.isCraftable()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
        }

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

        // 气体容器
        if (mouseItem.getItem() instanceof IGasItem) {
            GasStack gas = ((IGasItem) mouseItem.getItem()).getGas(mouseItem);
            if (gas != null && gas.amount > 0) {
                renderInjectTooltip(gui, mouseX, mouseY, gas.getGas().getLocalizedName(), mouseItem.getDisplayName());
                return true;
            }
        }

        return false;
    }

    private static void renderInjectTooltip(GuiContainer gui, int mouseX, int mouseY,
                                             String contentName, String itemName) {
        String injectKey = "gui.appliedenergistics2.security.inject.name";
        String s = " : " + I18n.format(injectKey) + " " + TextFormatting.RESET;
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-100) + s + contentName);
        tooltip.add(TextFormatting.DARK_GRAY + GameSettings.getKeyDisplayString(-99) + s + itemName);
        gui.drawHoveringText(tooltip, mouseX, mouseY);
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

    private static String getFluidModName(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return "";
        String defaultName = FluidRegistry.getDefaultFluidName(fluidStack.getFluid());
        if (defaultName == null) return "";
        ResourceLocation rl = new ResourceLocation(defaultName);
        String modid = rl.getNamespace();
        ModContainer mod = Loader.instance().getIndexedModList().get(modid);
        return mod != null ? mod.getName() : modid;
    }
}

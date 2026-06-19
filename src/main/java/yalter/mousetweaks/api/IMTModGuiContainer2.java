package yalter.mousetweaks.api;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

/**
 * MouseTweaks API stub for compile-time only.
 * AEBaseGui uses {@link net.minecraftforge.fml.common.Optional.Interface}
 * so this class is stripped at runtime when MouseTweaks is not present.
 */
public interface IMTModGuiContainer2 {
    boolean MT_isMouseTweaksDisabled();

    boolean MT_isWheelTweakDisabled();

    Container MT_getContainer();

    Slot MT_getSlotUnderMouse();

    boolean MT_isCraftingOutput(Slot slot);

    boolean MT_isIgnored(Slot slot);

    boolean MT_disableRMBDraggingFunctionality();
}

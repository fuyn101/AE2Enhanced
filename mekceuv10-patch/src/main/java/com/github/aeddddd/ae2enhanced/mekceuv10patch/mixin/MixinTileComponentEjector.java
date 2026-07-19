package com.github.aeddddd.ae2enhanced.mekceuv10patch.mixin;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.FluidUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

@Mixin(value = TileComponentEjector.class, remap = false)
public abstract class MixinTileComponentEjector {

    private static final String REDIRECTOR = "com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector";
    private static Method itemRedirect;
    private static Method fluidRedirect;
    private static boolean redirectorResolved;

    @Shadow
    private TileEntityContainerBlock tileEntity;

    @Inject(
            method = "outputItems(Ljava/util/List;Ljava/util/Set;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void ae2enhancedmekceuv10patch$redirectItemsBeforeOutput(List<IInventorySlot> outputSlots,
                                                                       Set<EnumFacing> outputSides,
                                                                       CallbackInfo ci) {
        World world = this.tileEntity.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        for (IInventorySlot slot : outputSlots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()
                    || slot.extractItem(1, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty()) {
                continue;
            }

            ItemStack remainder = redirectItem(stack.copy(), world, this.tileEntity.getPos());
            if (remainder.getCount() != stack.getCount()) {
                slot.setStack(remainder);
            }
        }
    }

    @Redirect(
            method = "ejectFluid(Ljava/util/Set;Lmekanism/api/fluid/IExtendedFluidTank;Lmekanism/common/util/EjectSpeedController;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/util/FluidUtils;emit(Ljava/util/Set;Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraft/tileentity/TileEntity;)I"
            ),
            remap = false
    )
    private int ae2enhancedmekceuv10patch$redirectFluidBeforeOutput(Set<EnumFacing> outputSides,
                                                                      FluidStack stack,
                                                                      TileEntity source) {
        World world = source.getWorld();
        if (stack == null || stack.amount <= 0) {
            return 0;
        }
        if (world == null || world.isRemote) {
            return FluidUtils.emit(outputSides, stack, source);
        }

        FluidStack redirectStack = stack.copy();
        FluidStack remainder = redirectFluid(redirectStack, world, source.getPos());
        int remainderAmount = remainder == null ? 0 : Math.min(redirectStack.amount, remainder.amount);
        int redirected = redirectStack.amount - remainderAmount;
        if (redirected <= 0) {
            return FluidUtils.emit(outputSides, stack, source);
        }

        int pipeAmount = stack.amount - redirected;
        if (pipeAmount <= 0) {
            return redirected;
        }

        FluidStack pipeStack = stack.copy();
        pipeStack.amount = pipeAmount;
        return redirected + FluidUtils.emit(outputSides, pipeStack, source);
    }

    private static ItemStack redirectItem(ItemStack stack, World world, BlockPos pos) {
        resolveRedirector();
        if (itemRedirect == null) {
            return stack;
        }
        try {
            return (ItemStack) itemRedirect.invoke(null, stack, world, pos);
        } catch (ReflectiveOperationException ignored) {
            return stack;
        }
    }

    private static FluidStack redirectFluid(FluidStack stack, World world, BlockPos pos) {
        resolveRedirector();
        if (fluidRedirect == null) {
            return stack;
        }
        try {
            return (FluidStack) fluidRedirect.invoke(null, stack, world, pos);
        } catch (ReflectiveOperationException ignored) {
            return stack;
        }
    }

    private static synchronized void resolveRedirector() {
        if (redirectorResolved) {
            return;
        }
        redirectorResolved = true;
        try {
            Class<?> redirector = Class.forName(REDIRECTOR);
            itemRedirect = redirector.getMethod("tryRedirect", ItemStack.class, World.class, BlockPos.class);
            fluidRedirect = redirector.getMethod("tryRedirectFluid", FluidStack.class, World.class, BlockPos.class);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}

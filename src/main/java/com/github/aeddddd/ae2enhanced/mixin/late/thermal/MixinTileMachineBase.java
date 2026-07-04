package com.github.aeddddd.ae2enhanced.mixin.late.thermal;

import cofh.core.util.core.SlotConfig;
import cofh.thermalexpansion.block.machine.TileMachineBase;
import com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Thermal Expansion 机器产物直注 Mixin。
 *
 * <p>在机器调用 {@link TileMachineBase#transferOutput()} 之前，
 * 把所有“可提取槽”（即输出槽）中的物品尝试重定向到已绑定的 ME 网络回收节点。
 * 同时扫描流体储罐并尝试把流体产物重定向到网络。</p>
 */
@Mixin(value = TileMachineBase.class, remap = false)
public class MixinTileMachineBase {

    private static final Field FIELD_SLOT_CONFIG;
    private static final Field FIELD_INVENTORY;
    private static final Field FIELD_WORLD;
    private static final Field FIELD_POS;

    static {
        Field slotConfigField = null;
        Field inventoryField = null;
        Field worldField = null;
        Field posField = null;
        try {
            Class<?> clazz = Class.forName("cofh.thermalexpansion.block.machine.TileMachineBase");
            slotConfigField = findField(clazz, "slotConfig");
            inventoryField = findField(clazz, "inventory");
            worldField = findField(clazz, "field_145850_b");
            posField = findField(clazz, "field_174879_c");
        } catch (Exception ignored) {
        }
        FIELD_SLOT_CONFIG = slotConfigField;
        FIELD_INVENTORY = inventoryField;
        FIELD_WORLD = worldField;
        FIELD_POS = posField;
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @Inject(method = "func_73660_a", at = @At(value = "INVOKE", target = "Lcofh/thermalexpansion/block/machine/TileMachineBase;transferOutput()V"))
    private void ae2enhanced$redirectOutputsBeforeTransfer(CallbackInfo ci) {
        if (FIELD_SLOT_CONFIG == null || FIELD_INVENTORY == null || FIELD_WORLD == null || FIELD_POS == null) {
            return;
        }
        try {
            World world = (World) FIELD_WORLD.get(this);
            if (world == null || world.isRemote) {
                return;
            }
            SlotConfig slotConfig = (SlotConfig) FIELD_SLOT_CONFIG.get(this);
            ItemStack[] inventory = (ItemStack[]) FIELD_INVENTORY.get(this);
            BlockPos pos = (BlockPos) FIELD_POS.get(this);
            if (slotConfig == null || inventory == null) {
                return;
            }

            int limit = Math.min(inventory.length, slotConfig.allowExtractionSlot.length);
            for (int i = 0; i < limit; i++) {
                if (!slotConfig.allowExtractionSlot[i]) {
                    continue;
                }
                ItemStack stack = inventory[i];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                ItemStack remainder = MachineOutputRedirector.tryRedirect(stack, world, pos);
                inventory[i] = remainder;
            }

            // 流体产物重定向
            redirectFluids(world, pos);
        } catch (IllegalAccessException ignored) {
        }
    }

    private void redirectFluids(World world, BlockPos pos) {
        TileEntity tile = (TileEntity) (Object) this;
        IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
        if (handler != null) {
            redirectFluidHandler(handler, world, pos);
            return;
        }
        redirectFluidTankFields(world, pos);
    }

    private void redirectFluidHandler(IFluidHandler handler, World world, BlockPos pos) {
        for (IFluidTankProperties prop : handler.getTankProperties()) {
            if (prop == null) continue;
            FluidStack contents = prop.getContents();
            if (contents == null || contents.amount <= 0) continue;
            FluidStack remainder = MachineOutputRedirector.tryRedirectFluid(contents, world, pos);
            if (remainder == null || remainder.amount == 0) {
                handler.drain(contents, true);
            } else if (remainder.amount < contents.amount) {
                int accepted = contents.amount - remainder.amount;
                handler.drain(new FluidStack(contents.getFluid(), accepted), true);
            }
        }
    }

    private void redirectFluidTankFields(World world, BlockPos pos) {
        Class<?> clazz = this.getClass();
        while (clazz != null && !Object.class.getName().equals(clazz.getName())) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    if (FluidTank.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        FluidTank tank = (FluidTank) field.get(this);
                        if (tank != null) redirectFluidTank(tank, world, pos);
                    } else if (field.getType().isArray() && FluidTank.class.isAssignableFrom(field.getType().getComponentType())) {
                        field.setAccessible(true);
                        FluidTank[] tanks = (FluidTank[]) field.get(this);
                        if (tanks != null) {
                            for (FluidTank tank : tanks) {
                                if (tank != null) redirectFluidTank(tank, world, pos);
                            }
                        }
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private void redirectFluidTank(FluidTank tank, World world, BlockPos pos) {
        FluidStack fluid = tank.getFluid();
        if (fluid == null || fluid.amount <= 0) return;
        FluidStack remainder = MachineOutputRedirector.tryRedirectFluid(fluid, world, pos);
        if (remainder == null || remainder.amount == 0) {
            tank.drain(fluid.amount, true);
        } else if (remainder.amount < fluid.amount) {
            tank.drain(fluid.amount - remainder.amount, true);
        }
    }
}

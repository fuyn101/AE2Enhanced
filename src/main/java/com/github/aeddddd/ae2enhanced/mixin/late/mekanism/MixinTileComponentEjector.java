package com.github.aeddddd.ae2enhanced.mixin.late.mekanism;

import com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Mekanism 机器产物直注 Mixin。
 *
 * <p>在 {@code mekanism.common.tile.component.TileComponentEjector#tick()}
 * 调用 {@code outputItems()} 之前，把当前配置的物品输出槽中的产物重定向到已绑定的 ME 网络回收节点。
 * 覆盖所有使用 TileComponentEjector 的 Mekanism 机器（包括工厂）。</p>
 */
@Mixin(targets = "mekanism.common.tile.component.TileComponentEjector", remap = false)
public class MixinTileComponentEjector {

    private static final Field FIELD_TILE_ENTITY;
    private static final Field FIELD_SIDE_DATA;
    private static final Field FIELD_AVAILABLE_SLOTS;
    private static final Object TRANSMISSION_TYPE_ITEM;

    static {
        Field tileField = null;
        Field sideDataField = null;
        Field availableSlotsField = null;
        Object itemType = null;
        try {
            Class<?> clazz = Class.forName("mekanism.common.tile.component.TileComponentEjector");
            tileField = clazz.getDeclaredField("tileEntity");
            tileField.setAccessible(true);
            sideDataField = clazz.getDeclaredField("sideData");
            sideDataField.setAccessible(true);

            Class<?> sideDataClass = Class.forName("mekanism.common.SideData");
            availableSlotsField = sideDataClass.getDeclaredField("availableSlots");
            availableSlotsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Class<Enum> transmissionTypeClass = (Class<Enum>) Class.forName("mekanism.api.transmitters.TransmissionType");
            itemType = Enum.valueOf(transmissionTypeClass, "ITEM");
        } catch (Exception ignored) {
        }
        FIELD_TILE_ENTITY = tileField;
        FIELD_SIDE_DATA = sideDataField;
        FIELD_AVAILABLE_SLOTS = availableSlotsField;
        TRANSMISSION_TYPE_ITEM = itemType;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lmekanism/common/tile/component/TileComponentEjector;outputItems()V"))
    private void ae2enhanced$redirectItemsBeforeOutput(CallbackInfo ci) {
        if (FIELD_TILE_ENTITY == null || FIELD_SIDE_DATA == null || FIELD_AVAILABLE_SLOTS == null || TRANSMISSION_TYPE_ITEM == null) {
            return;
        }
        try {
            Object tile = FIELD_TILE_ENTITY.get(this);
            if (!(tile instanceof TileEntity)) {
                return;
            }
            TileEntity tileEntity = (TileEntity) tile;
            World world = tileEntity.getWorld();
            if (world == null || world.isRemote) {
                return;
            }
            if (!(tile instanceof IInventory)) {
                return;
            }
            IInventory inventory = (IInventory) tile;

            @SuppressWarnings("unchecked")
            Map<Object, Object> sideData = (Map<Object, Object>) FIELD_SIDE_DATA.get(this);
            if (sideData == null) {
                return;
            }

            Object data = sideData.get(TRANSMISSION_TYPE_ITEM);
            if (data == null) {
                return;
            }

            int[] availableSlots = (int[]) FIELD_AVAILABLE_SLOTS.get(data);
            if (availableSlots == null) {
                return;
            }

            BlockPos pos = tileEntity.getPos();
            for (int slot : availableSlots) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                ItemStack remainder = MachineOutputRedirector.tryRedirect(stack, world, pos);
                if (remainder.getCount() != stack.getCount()) {
                    inventory.setInventorySlotContents(slot, remainder);
                }
            }
        } catch (IllegalAccessException ignored) {
        }
    }
}

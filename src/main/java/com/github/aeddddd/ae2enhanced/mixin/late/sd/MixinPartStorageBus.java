package com.github.aeddddd.ae2enhanced.mixin.late.sd;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.parts.misc.PartStorageBus;
import com.github.aeddddd.ae2enhanced.integration.drawer.DrawerMonitorWrapper;
import com.github.aeddddd.ae2enhanced.integration.drawer.sd.StorageDrawersAdapter;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 替换 StorageDrawers 的 ItemRepositoryAdapter 为 Hash 索引优化版本。
 *
 * <p>在 {@link PartStorageBus#getInventoryWrapper} 返回 ItemRepositoryAdapter 时，
 * 使用 {@link StorageDrawersAdapter} + {@link DrawerMonitorWrapper} 替代，
 * 使 getAvailableItems() 利用 TileEntityController.drawerPrimaryLookup Hash 索引。</p>
 */
@Mixin(value = PartStorageBus.class, remap = false)
public class MixinPartStorageBus {

    @Inject(method = "getInventoryWrapper", at = @At("RETURN"), cancellable = true)
    private void ae2enhanced$replaceItemRepositoryAdapter(TileEntity target, CallbackInfoReturnable<IMEInventory<IAEItemStack>> cir) {
        IMEInventory<IAEItemStack> result = cir.getReturnValue();
        // 只有当返回的是 ItemRepositoryAdapter 时才替换
        if (result != null && result.getClass().getName().equals("appeng.parts.misc.ItemRepositoryAdapter")) {
            try {
                EnumFacing targetSide = ((PartStorageBus) (Object) this).getSide().getFacing().getOpposite();
                if (PartStorageBus.ITEM_REPOSITORY_CAPABILITY != null
                        && target.hasCapability(PartStorageBus.ITEM_REPOSITORY_CAPABILITY, targetSide)) {
                    IItemRepository repo = target.getCapability(PartStorageBus.ITEM_REPOSITORY_CAPABILITY, targetSide);
                    if (repo != null) {
                        appeng.api.storage.channels.IItemStorageChannel channel =
                                appeng.api.AEApi.instance().storage().getStorageChannel(
                                        appeng.api.storage.channels.IItemStorageChannel.class);
                        StorageDrawersAdapter adapter = new StorageDrawersAdapter(repo, target, channel);
                        DrawerMonitorWrapper monitor = new DrawerMonitorWrapper(adapter, channel);
                        cir.setReturnValue(monitor);
                    }
                }
            } catch (Exception ignored) {
                // 任何异常保持原有行为
            }
        }
    }
}

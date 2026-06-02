package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.util.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

/**
 * 优化 ContainerMEMonitorable 的终端同步：在 func_75142_b 发送完成后清理 items 中的空 bucket，
 * 防止 ItemList 永久膨胀导致后续 tick 的 isEmpty() / 迭代产生不必要的开销。
 *
 * AE2-UEL 的 ItemList 依赖 MeaningfulItemIterator 在迭代时自动清理 !isMeaningful() 的条目，
 * 但空的 ItemVariantList 仍留在 ItemList.records 中。本 Mixin 在 resetStatus() 后彻底移除
 * 这些空 bucket，使后续 tick 的 isEmpty() 和迭代更快返回。
 *
 * 注意：ItemVariantList 是 package-private，因此本 Mixin 通过反射操作，避免直接引用。
 */
@Mixin(value = ContainerMEMonitorable.class, remap = false)
public class MixinContainerMEMonitorable {

    @Shadow
    private IItemList<IAEItemStack> items;

    @Inject(method = "func_75142_b", at = @At("TAIL"))
    private void ae2enhanced$cleanupItemList(CallbackInfo ci) {
        if (!Platform.isServer()) {
            return;
        }
        cleanupItemList(this.items);
    }

    private static void cleanupItemList(IItemList<IAEItemStack> itemList) {
        try {
            Object target = itemList;

            // Unwrap ItemListIgnoreCrafting if present
            if (target.getClass().getName().equals("appeng.util.inv.ItemListIgnoreCrafting")) {
                Field targetField = target.getClass().getDeclaredField("target");
                targetField.setAccessible(true);
                target = targetField.get(target);
            }

            if (!target.getClass().getName().equals("appeng.util.item.ItemList")) {
                return;
            }

            Field recordsField = target.getClass().getDeclaredField("records");
            recordsField.setAccessible(true);
            Map<?, ?> records = (Map<?, ?>) recordsField.get(target);
            if (records == null) {
                return;
            }

            Iterator<?> it = records.values().iterator();
            while (it.hasNext()) {
                Object variantList = it.next();
                // getRecords() is package-private in ItemVariantList
                Method getRecordsMethod = variantList.getClass().getDeclaredMethod("getRecords");
                getRecordsMethod.setAccessible(true);
                Map<?, ?> variantRecords = (Map<?, ?>) getRecordsMethod.invoke(variantList);
                if (variantRecords != null && variantRecords.isEmpty()) {
                    it.remove();
                }
            }
        } catch (Exception e) {
            // Silently fail if reflection doesn't work on this AE2 build.
            // MeaningfulItemIterator already handles the common case of skipping zero entries.
        }
    }
}

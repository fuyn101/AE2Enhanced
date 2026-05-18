package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.CreativeCellInventory;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修改创造型ME存储元件的显示/可用数量上限。
 *
 * <p>AE2 原版硬编码为 {@link Integer#MAX_VALUE}，导致大规模虚拟合成时
 * crafting plan 阶段看到的可用量被限制在 2^31-1，无法支撑 Int.MAX_VALUE
 * 级别的八重压缩圆石等深层配方链。</p>
 *
 * <p>修复：当网络中已存在同种物品时，避免 {@code Long.MAX_VALUE} 累加导致
 * signed long 溢出为负数。改为直接设置 stackSize 为 {@code Long.MAX_VALUE}。</p>
 */
@Mixin(CreativeCellInventory.class)
public abstract class MixinCreativeCellInventory {

    @Shadow(remap = false)
    @Final
    private IItemList<IAEItemStack> itemListCache;

    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEItemStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;",
            remap = false
        )
    )
    private IAEStack<IAEItemStack> ae2e$setStackSize(
            IAEItemStack instance,
            long stackSize,
            Operation<IAEStack<IAEItemStack>> original
    ) {
        return original.call(instance, Long.MAX_VALUE);
    }

    @Inject(
        method = "getAvailableItems",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    @SuppressWarnings("unchecked")
    private void ae2e$getAvailableItems(IItemList out, CallbackInfoReturnable<IItemList> cir) {
        for (IAEItemStack ais : this.itemListCache) {
            IAEItemStack existing = (IAEItemStack) out.findPrecise(ais);
            if (existing == null) {
                out.add(ais);
            } else {
                existing.setStackSize(Long.MAX_VALUE);
            }
        }
        cir.setReturnValue(out);
    }
}

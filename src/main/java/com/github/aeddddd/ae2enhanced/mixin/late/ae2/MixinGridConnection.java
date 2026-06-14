package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import com.github.aeddddd.ae2enhanced.pathing.IEnhancedPathItem;

/**
 * 为 GridConnection 添加 PR #8285 快速频道路径算法所需的额外方法。
 */
@Mixin(value = GridConnection.class, remap = false)
public abstract class MixinGridConnection implements IEnhancedPathItem {

    @Shadow
    private int usedChannels;

    @Shadow
    private GridNode sideA;

    @Shadow
    private GridNode sideB;

    @Override
    public void ae2enhanced$setAdHocChannels(int channels) {
        this.usedChannels = channels;
    }

    @Override
    public GridNode ae2enhanced$getHighestSimilarAncestor() {
        return null;
    }

    @Override
    public boolean ae2enhanced$getSubtreeAllowsCompressedChannels() {
        return false;
    }

    @Override
    public int ae2enhanced$propagateChannelsUpwards(boolean consumesChannel) {
        // 对 GridConnection 忽略 consumesChannel 参数，使用无参语义。
        // sideA 总是朝向 Controller 的一侧（setControllerRoute 会翻转）。
        if (this.sideB.getControllerRoute() == (IPathItem) this) {
            this.usedChannels = ((IEnhancedPathItem) this.sideB).ae2enhanced$getUsedChannels();
        } else {
            this.usedChannels = 0;
        }
        return this.usedChannels;
    }

    @Override
    public int ae2enhanced$getSubtreeMaxChannels() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int ae2enhanced$getMaxChannels() {
        return AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS)
                ? AEConfig.instance().getDenseChannelCapacity()
                : Integer.MAX_VALUE;
    }

    @Override
    public int ae2enhanced$getUsedChannels() {
        return this.usedChannels;
    }

    /**
     * 当设置 Controller 路由时清空工作态频道数（高版本行为）。
     */
    @Inject(method = "setControllerRoute", at = @At("HEAD"), remap = false)
    private void ae2enhanced$onSetControllerRoute(IPathItem fast, boolean zeroOut, CallbackInfo ci) {
        if (zeroOut) {
            this.usedChannels = 0;
        }
    }
}

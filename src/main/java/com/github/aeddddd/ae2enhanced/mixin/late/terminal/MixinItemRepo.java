package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.me.ItemRepo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.regex.Pattern;

/**
 * 优化 ItemRepo.addIAE()：缓存 Pattern.compile 结果，避免每次 updateView() 重建时
 * 对全部物品重复编译正则表达式（50 万+ 物品种类时这是客户端主要瓶颈）。
 *
 * updateView() 在一次调用中对每个物品都调用 addIAE()，而 addIAE() 内部每次都用
 * Pattern.compile(this.innerSearch, 2) 创建新的 Pattern。缓存后，同一 tick 内只编译一次。
 */
@Mixin(value = ItemRepo.class, remap = false)
public class MixinItemRepo {

    @Shadow
    private String innerSearch;

    @Unique
    private Pattern ae2enhanced$cachedPattern;

    @Unique
    private String ae2enhanced$cachedPatternString;

    @Redirect(
            method = "addIAE",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/regex/Pattern;compile(Ljava/lang/String;I)Ljava/util/regex/Pattern;"
            )
    )
    private Pattern ae2enhanced$compilePattern(String regex, int flags) {
        if (ae2enhanced$cachedPattern != null && regex.equals(ae2enhanced$cachedPatternString)) {
            return ae2enhanced$cachedPattern;
        }

        Pattern result = Pattern.compile(regex, flags);
        // 只缓存原始搜索字符串，不缓存 catch 块里的 Pattern.quote 回退版本
        if (regex.equals(this.innerSearch)) {
            ae2enhanced$cachedPatternString = regex;
            ae2enhanced$cachedPattern = result;
        }
        return result;
    }
}

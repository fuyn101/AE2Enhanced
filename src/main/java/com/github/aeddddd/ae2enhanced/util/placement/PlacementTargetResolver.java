package com.github.aeddddd.ae2enhanced.util.placement;

import appeng.api.util.AEColor;
import appeng.items.parts.ItemPart;
import appeng.items.parts.PartType;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 解析当前应该放置的目标物品。
 *
 * 优先级：
 * 1. 副手物品（如果是方块/线缆/Part）。
 * 2. 当前径向预设。
 * 3. 批量模式下被点击的方块（建筑手杖模式：对着相同方块铺设）。
 */
public final class PlacementTargetResolver {

    private PlacementTargetResolver() {}

    /**
     * 解析单格/线缆放置的目标物品。
     *
     * @param player     玩家
     * @param config     工具配置
     * @param world      世界
     * @param clickedPos 被点击的方块位置
     * @return 目标物品，无则 EMPTY
     */
    public static ItemStack resolveSingleOrCable(EntityPlayer player, PlacementConfig config,
                                                  World world, BlockPos clickedPos) {
        // 1. 副手优先
        ItemStack off = player.getHeldItemOffhand();
        if (isPlaceable(off)) {
            return off.copy();
        }

        // 2. 当前预设
        ItemStack preset = config.getSelectedStack();
        if (!preset.isEmpty()) {
            return preset.copy();
        }

        // 3. 批量模式下的被点击方块（如果玩家配置了点击同材质铺设）
        // 单格模式不走这条，返回空
        return ItemStack.EMPTY;
    }

    /**
     * 解析批量放置的目标物品。
     *
     * @param player     玩家
     * @param config     工具配置
     * @param world      世界
     * @param clickedPos 被点击的方块位置
     * @return 目标物品，无则 EMPTY
     */
    public static ItemStack resolveBulk(EntityPlayer player, PlacementConfig config,
                                         World world, BlockPos clickedPos) {
        // 1. 副手优先
        ItemStack off = player.getHeldItemOffhand();
        if (off.getItem() instanceof ItemBlock) {
            return off.copy();
        }

        // 2. 当前预设
        ItemStack preset = config.getSelectedStack();
        if (!preset.isEmpty() && preset.getItem() instanceof ItemBlock) {
            return preset.copy();
        }

        // 3. 建筑手杖模式：使用被点击方块本身
        IBlockState state = world.getBlockState(clickedPos);
        Block block = state.getBlock();
        ItemStack pickStack = block.getItem(world, clickedPos, state);
        if (!pickStack.isEmpty() && pickStack.getItem() instanceof ItemBlock) {
            return pickStack;
        }

        return ItemStack.EMPTY;
    }

    /**
     * 判断物品是否可放置（方块、AE2 Part、Facade）。
     */
    public static boolean isPlaceable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof ItemBlock
                || stack.getItem() instanceof appeng.api.parts.IPartItem
                || stack.getItem() instanceof appeng.facade.IFacadeItem;
    }

    /**
     * 判断物品是否为 AE2 线缆。
     */
    public static boolean isCable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ItemPart)) return false;
        PartType type = ((ItemPart) stack.getItem()).getTypeByStack(stack);
        return type != null && type.isCable();
    }

    /**
     * 创建指定颜色的线缆物品。
     *
     * @param baseCable 原始线缆物品（用于确定线缆类型）
     * @param color     目标颜色
     * @return 对应颜色的线缆 stack，失败返回 EMPTY
     */
    public static ItemStack createCableOfColor(ItemStack baseCable, AEColor color) {
        if (!isCable(baseCable)) return ItemStack.EMPTY;
        ItemPart itemPart = (ItemPart) baseCable.getItem();
        PartType type = itemPart.getTypeByStack(baseCable);
        if (type == null) return ItemStack.EMPTY;
        // 通过 PartType + AEColor 创建对应 meta 的 stack
        int damage = itemPart.getDamageByType(type);
        if (damage < 0) return ItemStack.EMPTY;
        // 基础 damage 对应 TRANSPARENT，需要加上颜色偏移
        int colorOffset = color.ordinal();
        return new ItemStack(itemPart, 1, damage + colorOffset);
    }

    /**
     * 获取线缆当前颜色。
     */
    public static AEColor getCableColor(ItemStack cable) {
        if (!isCable(cable)) return AEColor.TRANSPARENT;
        ItemPart itemPart = (ItemPart) cable.getItem();
        PartType type = itemPart.getTypeByStack(cable);
        if (type == null) return AEColor.TRANSPARENT;
        int baseDamage = itemPart.getDamageByType(type);
        int colorIndex = cable.getMetadata() - baseDamage;
        if (colorIndex < 0 || colorIndex >= AEColor.values().length) {
            return AEColor.TRANSPARENT;
        }
        return AEColor.values()[colorIndex];
    }
}

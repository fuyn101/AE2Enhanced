package com.github.aeddddd.ae2enhanced.crafting;

import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑洞生成仪式配方.
 * 以 ME 控制器方块为中心,扫描周围 5×5×5 区域内的物品实体,累加数量后匹配输入.
 * 输入支持 ItemStack(含 metadata 区分).
 */
public class SingularityRecipe {

    private final String id;
    private final List<ItemStack> inputs;

    public SingularityRecipe(String id, List<ItemStack> inputs) {
        this.id = id;
        this.inputs = inputs;
    }

    public String getId() {
        return id;
    }

    public List<ItemStack> getInputs() {
        return inputs;
    }

    private static String stackKey(ItemStack stack) {
        if (stack.isEmpty()) return "";
        return stack.getItem().getRegistryName() + "#" + stack.getMetadata();
    }

    /**
     * 检测以 center 为中心的 5×5×5 区域内是否有足够材料.
     */
    public boolean matches(World world, BlockPos center) {
        AxisAlignedBB area = new AxisAlignedBB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3
        );
        Map<String, Integer> found = new HashMap<>();
        for (EntityItem entityItem : world.getEntitiesWithinAABB(EntityItem.class, area)) {
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;
            found.merge(stackKey(stack), stack.getCount(), Integer::sum);
        }
        for (ItemStack need : inputs) {
            if (found.getOrDefault(stackKey(need), 0) < need.getCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行仪式：消耗区域内材料,在中心位置生成微型奇点方块.
     */
    public void craft(World world, BlockPos center) {
        AxisAlignedBB area = new AxisAlignedBB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3
        );
        Map<String, Integer> remaining = new HashMap<>();
        for (ItemStack need : inputs) {
            remaining.put(stackKey(need), need.getCount());
        }
        for (EntityItem entityItem : world.getEntitiesWithinAABB(EntityItem.class, area)) {
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;
            String key = stackKey(stack);
            int needed = remaining.getOrDefault(key, 0);
            if (needed > 0) {
                int consume = Math.min(needed, stack.getCount());
                stack.shrink(consume);
                remaining.put(key, needed - consume);
                if (stack.isEmpty()) {
                    entityItem.setDead();
                }
            }
        }
        // 生成微型奇点方块
        world.setBlockState(center, BlockRegistry.MICRO_SINGULARITY.getDefaultState());
    }
}

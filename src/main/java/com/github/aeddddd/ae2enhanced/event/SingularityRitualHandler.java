package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 黑洞合成仪式触发器.
 * 玩家手持下界之星右键 AE2 ME 控制器方块时,扫描周围 5×5×5 区域内的物品实体,
 * 如果匹配黑洞合成配方,则消耗材料并生成产物.
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class SingularityRitualHandler {

    private static final Block CONTROLLER_BLOCK = Block.getBlockFromName("appliedenergistics2:controller");

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote) return;

        BlockPos pos = event.getPos();
        if (CONTROLLER_BLOCK == null) return;
        if (world.getBlockState(pos).getBlock() != CONTROLLER_BLOCK) return;

        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = event.getItemStack();
        if (held.getItem() != Items.NETHER_STAR) return;

        // 取消默认交互,防止打开其他 GUI
        event.setCanceled(true);

        SingularityRecipe recipe = SingularityRecipeRegistry.findMatching(world, pos);
        if (recipe != null) {
            // 消耗下界之星
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
            // 执行合成
            recipe.craft(world, pos);
            // 特效
            world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0, 0);
            world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN,
                    SoundCategory.BLOCKS, 1.0f, 0.5f);
        } else {
            // 配方不匹配：闷响提示
            world.playSound(null, pos, SoundEvents.ENTITY_GHAST_SHOOT,
                    SoundCategory.BLOCKS, 0.5f, 0.5f);
        }
    }
}

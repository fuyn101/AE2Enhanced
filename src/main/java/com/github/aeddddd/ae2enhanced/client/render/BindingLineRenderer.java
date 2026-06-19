package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端绑定高亮渲染器.
 *
 * 当玩家主手持有 Universal Memory Card 且准心指向某个 1 对多网络中的设备时,
 * 只为该网络渲染高亮描边边框：
 * - 中枢 ME 接口(source)：青色描边
 * - 目标机器(target)：橙色描边
 */
public class BindingLineRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // 旧版 Central ME Interface 已删除,绑定线渲染暂时无操作
    }
}

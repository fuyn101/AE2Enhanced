package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 客户端绑定线渲染器。
 *
 * 当玩家主手持有 Universal Memory Card 且内存卡记录了绑定源时，
 * 渲染从 Central ME Interface 到其所有绑定目标的青色连线。
 */
public class BindingLineRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        ItemStack held = player.getHeldItemMainhand();
        if (!(held.getItem() instanceof ItemUniversalMemoryCard)) return;
        if (!ItemUniversalMemoryCard.hasBinding(held)) return;

        NBTTagCompound binding = ItemUniversalMemoryCard.getBinding(held);
        BlockPos sourcePos = BlockPos.fromLong(binding.getLong("pos"));
        int sourceDim = binding.getInteger("dim");

        if (player.world.provider.getDimension() != sourceDim) return;

        TileEntity te = player.world.getTileEntity(sourcePos);
        if (!(te instanceof TileCentralMEInterface)) return;

        TileCentralMEInterface source = (TileCentralMEInterface) te;
        List<TargetBinding> bindings = source.getInterfaceDuality().getBindings();
        if (bindings.isEmpty()) return;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.glLineWidth(2.0f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        for (TargetBinding target : bindings) {
            BlockPos tp = target.pos;
            buf.pos(sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5)
               .color(0.0f, 1.0f, 1.0f, 0.8f).endVertex();
            buf.pos(tp.getX() + 0.5, tp.getY() + 0.5, tp.getZ() + 0.5)
               .color(0.0f, 1.0f, 1.0f, 0.8f).endVertex();
        }

        tess.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}

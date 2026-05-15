package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternEncoder;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = ContainerPatternEncoder.class, remap = false, priority = 1000)
public class MixinPatternEncoder {

    private static Field patternSlotOUTField;
    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;

    private static void tryInitReflection() {
        if (reflectionReady || reflectionFailed) return;
        try {
            patternSlotOUTField = ContainerPatternEncoder.class.getDeclaredField("patternSlotOUT");
            patternSlotOUTField.setAccessible(true);
            reflectionReady = true;
        } catch (Exception e) {
            reflectionFailed = true;
            AE2Enhanced.LOGGER.error("[AE2E] MixinPatternEncoder reflection init failed: {}", e.toString());
        }
    }

    @Inject(method = "encode", at = @At("RETURN"))
    private void onEncodeReturn(CallbackInfo ci) {
        if (reflectionFailed) return;
        try {
            tryInitReflection();
            if (!reflectionReady) return;

            ContainerPatternEncoder container = (ContainerPatternEncoder) (Object) this;

            Object slotObj = patternSlotOUTField.get(container);
            if (!(slotObj instanceof Slot)) return;
            Slot patternSlotOUT = (Slot) slotObj;

            ItemStack pattern = patternSlotOUT.getStack();
            if (pattern.isEmpty()) return;
            if (!isCraftingPattern(pattern)) return; // 仅上传合成样板，跳过处理样板

            InventoryPlayer invPlayer = ((AEBaseContainer) container).getPlayerInv();
            if (invPlayer == null) return;
            EntityPlayer player = invPlayer.player;
            if (player == null) return;

            World world = player.world;
            if (world.isRemote) return;

            TileAssemblyController target = findTargetController(world, player, pattern);
            if (target == null) return;

            boolean uploaded = target.tryAutoUploadPattern(pattern);
            if (uploaded) {
                patternSlotOUT.putStack(ItemStack.EMPTY);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] AutoUpload unexpected error: {}", e.toString());
        }
    }

    /**
     * 检查样板是否为合成样板（Crafting Pattern），而非处理样板（Processing Pattern）。
     * AE2 编码样板的 NBT 中 "crafting" 字段为 1 表示合成样板，0 表示处理样板。
     */
    private static boolean isCraftingPattern(ItemStack pattern) {
        if (!pattern.hasTagCompound()) return false;
        NBTTagCompound tag = pattern.getTagCompound();
        return tag.hasKey("crafting", Constants.NBT.TAG_BYTE) && tag.getByte("crafting") == 1;
    }

    private static TileAssemblyController findTargetController(World world, EntityPlayer player, ItemStack pattern) {
        ControllerIndex index = ControllerIndex.get(world);
        if (index == null) return null;

        TileAssemblyController best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : index.getAll()) {
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileAssemblyController)) continue;
            TileAssemblyController controller = (TileAssemblyController) te;
            if (!controller.isFormed()) continue;
            if (!controller.hasAutoUploadUpgrade()) continue;
            if (!controller.canAcceptPattern(pattern)) continue;

            double dist = player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = controller;
            }
        }
        return best;
    }
}

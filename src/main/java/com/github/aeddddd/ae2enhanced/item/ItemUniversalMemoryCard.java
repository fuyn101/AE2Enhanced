package com.github.aeddddd.ae2enhanced.item;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.PacketUMCAction;
import com.github.aeddddd.ae2enhanced.util.memorycard.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.MemoryCardHandlerRegistry;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * 通用内存卡：复制/粘贴 AE2 设备配置（含升级卡），选取世界中的目标方块。
 */
public class ItemUniversalMemoryCard extends Item {

    private static final String NBT_CONFIG = "ae2e:umc_config";
    private static final String NBT_SELECTIONS = "ae2e:umc_selections";
    public static final int GUI_ID = 10;

    public ItemUniversalMemoryCard() {
        setMaxStackSize(1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setTranslationKey("ae2enhanced.universal_memory_card");
        setRegistryName("universal_memory_card");
    }

    // ============================================================
    // SelectionEntry
    // ============================================================

    public static class SelectionEntry {
        public final BlockPos pos;
        public final int dim;
        public final String tileId;
        public final int side; // AEPartLocation.ordinal(), -1 for TileEntity

        public SelectionEntry(BlockPos pos, int dim, String tileId, int side) {
            this.pos = pos;
            this.dim = dim;
            this.tileId = tileId;
            this.side = side;
        }

        public NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("pos", pos.toLong());
            tag.setInteger("dim", dim);
            tag.setString("id", tileId);
            tag.setInteger("side", side);
            return tag;
        }

        public static SelectionEntry fromNBT(NBTTagCompound tag) {
            return new SelectionEntry(
                    BlockPos.fromLong(tag.getLong("pos")),
                    tag.getInteger("dim"),
                    tag.getString("id"),
                    tag.hasKey("side") ? tag.getInteger("side") : -1
            );
        }
    }

    // ============================================================
    // NBT Helpers
    // ============================================================

    public static boolean hasConfig(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_CONFIG);
    }

    @Nullable
    public static NBTTagCompound getConfig(ItemStack stack) {
        if (!hasConfig(stack)) return null;
        return stack.getTagCompound().getCompoundTag(NBT_CONFIG);
    }

    public static void setConfig(ItemStack stack, String handlerId, String name, NBTTagCompound data) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound config = new NBTTagCompound();
        config.setString("handler", handlerId);
        config.setString("name", name);
        config.setTag("data", data);
        stack.getTagCompound().setTag(NBT_CONFIG, config);
    }

    public static void clearConfig(ItemStack stack) {
        if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag(NBT_CONFIG);
        }
    }

    public static List<SelectionEntry> getSelections(ItemStack stack) {
        List<SelectionEntry> list = new ArrayList<>();
        if (!stack.hasTagCompound()) return list;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_SELECTIONS)) return list;
        NBTTagList selections = tag.getTagList(NBT_SELECTIONS, 10);
        for (int i = 0; i < selections.tagCount(); i++) {
            list.add(SelectionEntry.fromNBT(selections.getCompoundTagAt(i)));
        }
        return list;
    }

    public static int getSelectionCount(ItemStack stack) {
        return getSelections(stack).size();
    }

    public static void addSelection(ItemStack stack, SelectionEntry entry) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagList selections;
        if (stack.getTagCompound().hasKey(NBT_SELECTIONS)) {
            selections = stack.getTagCompound().getTagList(NBT_SELECTIONS, 10);
        } else {
            selections = new NBTTagList();
        }
        // 去重
        for (int i = 0; i < selections.tagCount(); i++) {
            NBTTagCompound tag = selections.getCompoundTagAt(i);
            if (BlockPos.fromLong(tag.getLong("pos")).equals(entry.pos) && tag.getInteger("dim") == entry.dim) {
                return;
            }
        }
        selections.appendTag(entry.toNBT());
        stack.getTagCompound().setTag(NBT_SELECTIONS, selections);
    }

    public static void removeSelection(ItemStack stack, int index) {
        if (!stack.hasTagCompound()) return;
        NBTTagList selections = stack.getTagCompound().getTagList(NBT_SELECTIONS, 10);
        if (index >= 0 && index < selections.tagCount()) {
            selections.removeTag(index);
        }
        if (selections.tagCount() == 0) {
            stack.getTagCompound().removeTag(NBT_SELECTIONS);
        }
    }

    public static void clearSelections(ItemStack stack) {
        if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag(NBT_SELECTIONS);
        }
    }

    // ============================================================
    // Client Event: send network packet on right-click block
    // ============================================================

    @SideOnly(Side.CLIENT)
    public static void registerClientEvents() {
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }

    @SideOnly(Side.CLIENT)
    public static class ClientEvents {
        @SideOnly(Side.CLIENT)
        @SubscribeEvent
        public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getWorld().isRemote && event.getHand() == EnumHand.MAIN_HAND) {
                ItemStack stack = event.getEntityPlayer().getHeldItemMainhand();
                if (stack.getItem() instanceof ItemUniversalMemoryCard) {
                    EntityPlayer player = event.getEntityPlayer();
                    boolean isSneaking = player.isSneaking();
                    boolean isCtrl = net.minecraft.client.Minecraft.getMinecraft().gameSettings.keyBindSprint.isKeyDown();

                    PacketUMCAction.ActionType type;
                    if (isCtrl) {
                        type = PacketUMCAction.ActionType.SELECT;
                    } else if (isSneaking) {
                        type = PacketUMCAction.ActionType.COPY;
                    } else {
                        type = PacketUMCAction.ActionType.PASTE;
                    }

                    AE2Enhanced.network.sendToServer(new PacketUMCAction(type, event.getPos(), event.getFace()));
                    event.setCanceled(true);
                }
            }
        }
    }

    // ============================================================
    // Server Action Handler (called by PacketUMCAction.Handler)
    // ============================================================

    public static void handleServerAction(EntityPlayer player, PacketUMCAction message) {
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemUniversalMemoryCard)) return;

        switch (message.getType()) {
            case COPY:
                handleCopy(player, stack, message.getPos(), message.getFace());
                break;
            case PASTE:
                handlePaste(player, stack, message.getPos(), message.getFace());
                break;
            case SELECT:
                handleSelect(player, stack, message.getPos(), message.getFace());
                break;
            case CLEAR_CONFIG:
                clearConfig(stack);
                break;
            case CLEAR_SELECTIONS:
                clearSelections(stack);
                break;
            case REMOVE_SELECTION:
                removeSelection(stack, message.getIndex());
                break;
        }
    }

    private static void handleCopy(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        Object target = findTarget(world, pos, face);
        if (target == null) {
            player.sendMessage(new TextComponentString("\u00a7c无法复制该设备"));
            return;
        }

        IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(target);
        if (handler == null) {
            player.sendMessage(new TextComponentString("\u00a7c该设备不支持配置复制"));
            return;
        }

        NBTTagCompound data = handler.copy(target);
        if (data == null) {
            player.sendMessage(new TextComponentString("\u00a7c复制失败"));
            return;
        }

        String handlerId;
        if (target instanceof appeng.parts.AEBasePart) handlerId = "ae2_part";
        else if (target instanceof appeng.tile.AEBaseTile) handlerId = "ae2_tile";
        else handlerId = "ae2e_custom";

        setConfig(stack, handlerId, handler.getDisplayName(target), data);
        player.sendMessage(new TextComponentString("\u00a7a已复制 \u00a7e" + handler.getDisplayName(target) + " \u00a7a的配置"));
    }

    private static void handlePaste(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        if (!hasConfig(stack)) {
            player.sendMessage(new TextComponentString("\u00a7c内存卡中没有配置"));
            return;
        }

        NBTTagCompound config = getConfig(stack);
        NBTTagCompound data = config.getCompoundTag("data");

        World world = player.world;
        Object target = findTarget(world, pos, face);
        if (target == null) {
            player.sendMessage(new TextComponentString("\u00a7c无法粘贴到该设备"));
            return;
        }

        // 检查是否是批量粘贴
        List<SelectionEntry> selections = getSelections(stack);
        boolean isBulk = false;
        for (SelectionEntry entry : selections) {
            if (entry.dim == world.provider.getDimension() && entry.pos.equals(pos)) {
                isBulk = true;
                break;
            }
        }

        if (isBulk) {
            int success = 0;
            int failed = 0;
            for (SelectionEntry entry : selections) {
                if (entry.dim != world.provider.getDimension()) continue;
                Object bulkTarget = resolveTarget(world, entry);
                if (bulkTarget == null) continue;
                IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(bulkTarget);
                if (handler == null) continue;
                IMemoryCardHandler.PasteResult result = handler.paste(bulkTarget, data, player);
                if (result == IMemoryCardHandler.PasteResult.SUCCESS) success++;
                else failed++;
            }
            player.sendMessage(new TextComponentString("\u00a7a批量粘贴完成：成功 \u00a7e" + success + "\u00a7a 个，失败 \u00a7e" + failed + "\u00a7a 个"));
        } else {
            IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(target);
            if (handler == null) {
                player.sendMessage(new TextComponentString("\u00a7c该设备不支持配置粘贴"));
                return;
            }
            IMemoryCardHandler.PasteResult result = handler.paste(target, data, player);
            switch (result) {
                case SUCCESS:
                    player.sendMessage(new TextComponentString("\u00a7a配置已粘贴"));
                    break;
                case MISSING_UPGRADES:
                    player.sendMessage(new TextComponentString("\u00a7c背包中升级卡不足"));
                    break;
                case INVALID_MACHINE:
                    player.sendMessage(new TextComponentString("\u00a7c设备类型不匹配"));
                    break;
                case FAILED:
                    player.sendMessage(new TextComponentString("\u00a7c粘贴失败"));
                    break;
            }
        }
    }

    private static void handleSelect(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        TileEntity te = world.getTileEntity(pos);

        // 检查是否已在选取列表中
        List<SelectionEntry> selections = getSelections(stack);
        for (int i = 0; i < selections.size(); i++) {
            SelectionEntry entry = selections.get(i);
            if (entry.dim == world.provider.getDimension() && entry.pos.equals(pos)) {
                removeSelection(stack, i);
                player.sendMessage(new TextComponentString("\u00a7a已取消选取"));
                return;
            }
        }

        if (te instanceof IPartHost) {
            IPartHost host = (IPartHost) te;
            IPart part = host.getPart(AEPartLocation.fromFacing(face));
            if (part != null) {
                String tileId = part.getClass().getName();
                int side = AEPartLocation.fromFacing(face).ordinal();
                addSelection(stack, new SelectionEntry(pos, world.provider.getDimension(), tileId, side));
                player.sendMessage(new TextComponentString("\u00a7a已选取 Part"));
                return;
            }
        }

        if (te != null) {
            String tileId = te.getClass().getName();
            // BFS 近邻同类方块
            List<BlockPos> connected = findConnectedBlocks(world, pos, te.getClass(), 64);
            for (BlockPos p : connected) {
                addSelection(stack, new SelectionEntry(p, world.provider.getDimension(), tileId, -1));
            }
            player.sendMessage(new TextComponentString("\u00a7a已选取 \u00a7e" + connected.size() + "\u00a7a 个同类方块"));
        } else {
            // 非 TileEntity 方块
            String blockId = world.getBlockState(pos).getBlock().getRegistryName().toString();
            addSelection(stack, new SelectionEntry(pos, world.provider.getDimension(), blockId, -1));
            player.sendMessage(new TextComponentString("\u00a7a已选取方块"));
        }
    }

    // ============================================================
    // Target Resolution
    // ============================================================

    @Nullable
    private static Object findTarget(World world, BlockPos pos, EnumFacing face) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IPartHost) {
            IPartHost host = (IPartHost) te;
            IPart part = host.getPart(AEPartLocation.fromFacing(face));
            if (part != null) return part;
        }
        if (te != null) return te;
        return null;
    }

    @Nullable
    private static Object resolveTarget(World world, SelectionEntry entry) {
        if (entry.dim != world.provider.getDimension()) return null;
        if (!world.isBlockLoaded(entry.pos)) return null;
        TileEntity te = world.getTileEntity(entry.pos);
        if (te == null) return null;
        if (entry.side >= 0 && te instanceof IPartHost) {
            IPart part = ((IPartHost) te).getPart(AEPartLocation.fromOrdinal(entry.side));
            if (part != null) return part;
        }
        return te;
    }

    // ============================================================
    // BFS for connected same-class TileEntities
    // ============================================================

    private static List<BlockPos> findConnectedBlocks(World world, BlockPos start, Class<?> tileClass, int maxCount) {
        List<BlockPos> result = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < maxCount) {
            BlockPos pos = queue.poll();
            if (!world.isBlockLoaded(pos)) continue;
            TileEntity te = world.getTileEntity(pos);
            if (te != null && te.getClass() == tileClass) {
                result.add(pos);

                for (EnumFacing facing : EnumFacing.values()) {
                    BlockPos neighbor = pos.offset(facing);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        return result;
    }

    // ============================================================
    // GUI Open (right-click air)
    // ============================================================

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            player.openGui(AE2Enhanced.instance, GUI_ID, world, 0, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    // ============================================================
    // Tooltip
    // ============================================================

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        if (hasConfig(stack)) {
            NBTTagCompound config = getConfig(stack);
            tooltip.add("\u00a7b来源: \u00a7f" + config.getString("name"));
            NBTTagCompound data = config.getCompoundTag("data");
            if (data.hasKey("ae2e:upgrades")) {
                NBTTagList upgrades = data.getTagList("ae2e:upgrades", 10);
                tooltip.add("\u00a7b升级: \u00a7f" + upgrades.tagCount() + " 种");
            }
        } else {
            tooltip.add("\u00a77无配置");
        }

        int count = getSelectionCount(stack);
        if (count > 0) {
            tooltip.add("\u00a7b已选取: \u00a7f" + count + " 个目标");
        }

        tooltip.add("");
        tooltip.add("\u00a77Sneak+右键: 复制配置");
        tooltip.add("\u00a77右键: 粘贴配置");
        tooltip.add("\u00a77Ctrl+右键: 选取/取消选取");
        tooltip.add("\u00a77对空气右键: 打开管理界面");
    }
}

package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.container.slot.SlotGhost;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementUpdateSlot;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfigInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * ME 放置工具配置容器。
 * 显示 9 个幽灵槽（当前页），底部为玩家背包。
 */
public class ContainerPlacementTool extends Container {

    private final EntityPlayer player;
    private final ItemStack toolStack;
    private final PlacementConfig config;
    private final PlacementConfigInventory inventory;
    private int currentPage = 0;

    public ContainerPlacementTool(EntityPlayer player, ItemStack toolStack) {
        this.player = player;
        this.toolStack = toolStack;
        this.config = new PlacementConfig(toolStack);
        this.inventory = new PlacementConfigInventory(config);
        this.currentPage = config.getCurrentPage();

        rebuildSlots();
    }

    private void rebuildSlots() {
        this.inventorySlots.clear();
        this.inventoryItemStacks.clear();

        // 当前页的 9 个幽灵槽（3x3）
        int startX = 62;
        int startY = 17;
        for (int i = 0; i < PlacementConfig.SLOTS_PER_PAGE; i++) {
            int row = i / 3;
            int col = i % 3;
            int actualIndex = currentPage * PlacementConfig.SLOTS_PER_PAGE + i;
            addSlotToContainer(new SlotGhost(inventory, actualIndex,
                    startX + col * 18, startY + row * 18));
        }

        // 玩家背包
        InventoryPlayer playerInv = player.inventory;
        int playerInvX = 8;
        int playerInvY = 84;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9,
                        playerInvX + col * 18, playerInvY + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col,
                    playerInvX + col * 18, playerInvY + 58));
        }
    }

    public void setPage(int page) {
        if (page < 0 || page >= PlacementConfig.MAX_PAGES) return;
        this.currentPage = page;
        rebuildSlots();
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public PlacementConfig getConfig() {
        return config;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return playerIn.getHeldItemMainhand() == toolStack || playerIn.getHeldItemOffhand() == toolStack;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (slotId >= 0 && slotId < inventorySlots.size()) {
            Slot slot = inventorySlots.get(slotId);
            if (slot instanceof SlotGhost) {
                ItemStack carried = player.inventory.getItemStack();
                if (!carried.isEmpty()) {
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    slot.putStack(copy);
                } else {
                    slot.putStack(ItemStack.EMPTY);
                }
                if (player.world.isRemote) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.network.sendToServer(
                            new PacketPlacementUpdateSlot(slot.getSlotIndex(), slot.getStack()));
                }
                return player.inventory.getItemStack();
            }
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        // NBT 已在 slotClick 中同步，关闭时无需额外操作
    }
}

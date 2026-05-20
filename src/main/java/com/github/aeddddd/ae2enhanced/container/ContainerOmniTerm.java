package com.github.aeddddd.ae2enhanced.container;

import appeng.api.AEApi;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotCraftingTerm;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.ContainerNull;
import appeng.helpers.IContainerCraftingPacket;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;

import java.util.List;

/**
 * 全能无线终端 Container —— 物品库 + 合成栏 + 81槽位编码样板 + 右侧存储
 */
public class ContainerOmniTerm extends ContainerMEMonitorable
        implements IAEAppEngInventory, IOptionalSlotHost, IContainerCraftingPacket {

    // === 合成栏 ===
    private final SlotCraftingMatrix[] craftingSlots = new SlotCraftingMatrix[9];
    private SlotCraftingTerm craftOutputSlot;
    private final AppEngInternalInventory craftingOutput = new AppEngInternalInventory(this, 1);
    private IRecipe currentRecipe;
    private IItemHandler craftingInv;

    // === 编码区 ===
    private IItemHandler patternCraftingInv;     // 81格假合成矩阵
    private IItemHandler patternOutputInv;       // 27格输出
    private IItemHandler patternInv;             // 2格样板槽（空白/编码）
    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    private final RCSlotFakeCraftingMatrix[][] craftingSlotGroup = new RCSlotFakeCraftingMatrix[9][9];
    private final RCSlotPatternOutputs[][] outputSlotGroup = new RCSlotPatternOutputs[9][3];
    private SlotPatternTerm craftSlot;
    private SlotRestrictedInput patternSlotIN;
    private SlotRestrictedInput patternSlotOUT;

    @GuiSync(97)
    public boolean craftingMode = true;
    @GuiSync(96)
    public boolean substitute = false;

    private int scrollOffset = 0;

    // === 右侧存储 ===
    private final AppEngInternalInventory rightPatternStorage = new AppEngInternalInventory(null, 27);
    private final AppEngInternalInventory rightUpgradeStorage = new AppEngInternalInventory(null, 9);

    // === 宿主 ===
    private final ITerminalHost terminalHost;

    public ContainerOmniTerm(InventoryPlayer ip, ITerminalHost host) {
        super(ip, host, host instanceof appeng.api.implementations.guiobjects.IGuiItemObject ? (appeng.api.implementations.guiobjects.IGuiItemObject) host : null, false);
        this.terminalHost = host;

        // 1. 合成栏
        this.setupCraftingArea(ip, host);

        // 2. 编码区（81输入 + 27输出）
        this.setupPatternArea(ip, host);

        // 3. 右侧存储（样板 + 升级卡）
        this.setupRightStorage();

        // 4. 玩家背包（手动定位）
        this.addCustomPlayerInventory(ip, 8, 167, 225);

        // 5. 移除显示元件槽位（WirelessTerminalGuiObject 默认实现了 IViewCellStorage）
        for (int i = 0; i < this.cellView.length; i++) {
            if (this.cellView[i] != null) {
                this.inventorySlots.remove(this.cellView[i]);
                this.cellView[i] = null;
            }
        }

        // 6. 重新编号所有 slot（移除 viewCell 后 slotNumber 与实际索引不一致）
        for (int i = 0; i < this.inventorySlots.size(); i++) {
            this.inventorySlots.get(i).slotNumber = i;
        }

        // 7. 从 NBT 恢复数据
        this.loadFromNBT();
    }

    // ================== 合成栏 ==================

    private void setupCraftingArea(InventoryPlayer ip, ITerminalHost host) {
        // 合成矩阵物品栏（3x3）
        this.craftingInv = new AppEngInternalInventory(this, 9);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int idx = x + y * 3;
                this.craftingSlots[idx] = new SlotCraftingMatrix(this, this.craftingInv, idx, 26 + x * 18, 93 + y * 18);
                this.func_75146_a(this.craftingSlots[idx]);
            }
        }
        // 合成输出槽
        this.craftOutputSlot = new SlotCraftingTerm(ip.player, this.getActionSource(), this.getPowerSource(), host,
                this.craftingInv, this.craftingInv, this.craftingOutput, 133, 111, this);
        this.func_75146_a(this.craftOutputSlot);
        // AppEngInternalInventory 会自动触发 onChangeInventory，无需手动调用 onCraftMatrixChanged
    }

    // ================== 编码区 ==================

    private void setupPatternArea(InventoryPlayer ip, ITerminalHost host) {
        // 81格假合成矩阵
        this.patternCraftingInv = new AppEngInternalInventory(null, 81);
        // 27格输出
        this.patternOutputInv = new AppEngInternalInventory(null, 27);

        for (int g = 0; g < 9; g++) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int idx = g * 9 + r * 3 + c;
                    int x = 187 + c * 18;
                    int y = 93 + r * 18;
                    RCSlotFakeCraftingMatrix slot = new RCSlotFakeCraftingMatrix(this.patternCraftingInv, idx, x, y);
                    this.craftingSlotGroup[g][r * 3 + c] = slot;
                    this.func_75146_a(slot);
                }
            }
            for (int r = 0; r < 3; r++) {
                int idx = g * 3 + r;
                int x = 281;
                int y = 93 + r * 18;
                RCSlotPatternOutputs slot = new RCSlotPatternOutputs(this.patternOutputInv, this, idx, x, y, 0, 0, 1);
                this.outputSlotGroup[g][r] = slot;
                this.func_75146_a(slot);
                slot.setRenderDisabled(false);
                slot.setIIcon(-1);
            }
        }

        // Crafting 模式输出槽（SlotPatternTerm）— 位于编码区右侧中间
        this.patternInv = new AppEngInternalInventory(null, 2); // 样板槽 2格
        this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), this.getPowerSource(), host,
                this.patternCraftingInv, this.patternInv, this.cOut, 281, 111, this, 2, this);
        this.func_75146_a(this.craftSlot);
        this.craftSlot.setIIcon(-1);

        // 空白样板槽 / 编码样板槽 —— 位于编码区右侧
        this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                this.patternInv, 0, 319, 86, this.getInventoryPlayer());
        this.func_75146_a(this.patternSlotIN);

        this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                this.patternInv, 1, 319, 133, this.getInventoryPlayer());
        this.func_75146_a(this.patternSlotOUT);
        this.patternSlotOUT.setStackLimit(1);

        // 初始显示第0组
        this.setRCSlot(0);
    }

    // ================== 右侧存储 ==================

    private void setupRightStorage() {
        // 9列 x 3行 样板存储
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = r * 9 + c;
                this.func_75146_a(new AppEngSlot(this.rightPatternStorage, idx, 180 + c * 18, 167 + r * 18));
            }
        }
        // 9列 x 1行 升级卡存储
        for (int c = 0; c < 9; c++) {
            this.func_75146_a(new AppEngSlot(this.rightUpgradeStorage, c, 180 + c * 18, 221));
        }
    }

    // ================== 背包绑定 ==================

    private void addCustomPlayerInventory(InventoryPlayer ip, int invX, int invY, int hotbarY) {
        PlayerInvWrapper wrapper = new PlayerInvWrapper(ip);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.func_75146_a(new SlotPlayerInv(wrapper, j + i * 9 + 9, invX + j * 18, invY + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            this.func_75146_a(new SlotPlayerHotBar(wrapper, i, invX + i * 18, hotbarY));
        }
    }

    // ================== 滚动控制 ==================

    public void setRCSlot(int offset) {
        if (this.craftingMode) {
            offset = 0;
        }
        this.scrollOffset = offset;
        for (int g = 0; g < 9; g++) {
            boolean visible = (g == offset);
            for (int i = 0; i < 9; i++) {
                RCSlotFakeCraftingMatrix slot = this.craftingSlotGroup[g][i];
                if (visible) {
                    slot.xPos = slot.getDefX();
                    slot.visible = true;
                } else {
                    slot.xPos = -9000;
                    slot.visible = false;
                }
            }
            for (int i = 0; i < 3; i++) {
                RCSlotPatternOutputs slot = this.outputSlotGroup[g][i];
                if (visible) {
                    slot.xPos = this.craftingMode ? -9000 : slot.getDefX();
                    slot.visible = true;
                } else {
                    slot.xPos = -9000;
                    slot.visible = false;
                }
            }
        }
    }

    public int getScrollOffset() {
        return this.scrollOffset;
    }

    public int getMaxScrollOffset() {
        return this.craftingMode ? 0 : 8;
    }

    public void updateOrderOfOutputSlots() {
        if (!this.craftingMode) {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = -9000;
            }
        } else {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = this.craftSlot.getX();
            }
        }
    }

    // ================== NBT 持久化 ==================

    private static final String NBT_CRAFTING = "omni_crafting";
    private static final String NBT_PATTERN_IN = "omni_pattern_in";
    private static final String NBT_PATTERN_OUT = "omni_pattern_out";
    private static final String NBT_PATTERN_SLOTS = "omni_pattern_slots";
    private static final String NBT_RIGHT_PATTERN = "omni_right_pattern";
    private static final String NBT_RIGHT_UPGRADE = "omni_right_upgrade";
    private static final String NBT_CRAFTING_MODE = "omni_crafting_mode";
    private static final String NBT_SUBSTITUTE = "omni_substitute";
    private static final String NBT_SCROLL_OFFSET = "omni_scroll_offset";

    private void loadInventory(IItemHandler inv, NBTTagCompound tag, String key, int size) {
        if (!tag.hasKey(key)) return;
        NBTTagList list = tag.getTagList(key, 10);
        for (int i = 0; i < size; i++) {
            inv.extractItem(i, Integer.MAX_VALUE, false);
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound stackTag = list.getCompoundTagAt(i);
            int slot = stackTag.hasKey("Slot", 3) ? stackTag.getInteger("Slot") : i;
            if (slot >= 0 && slot < size) {
                ItemStack stack = new ItemStack(stackTag);
                if (!stack.isEmpty()) {
                    inv.insertItem(slot, stack, false);
                }
            }
        }
    }

    private void saveInventory(IItemHandler inv, NBTTagCompound tag, String key, int size) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound stackTag = new NBTTagCompound();
                stackTag.setInteger("Slot", i);
                stack.writeToNBT(stackTag);
                list.appendTag(stackTag);
            }
        }
        tag.setTag(key, list);
    }

    private void loadFromNBT() {
        if (!(this.terminalHost instanceof appeng.helpers.WirelessTerminalGuiObject)) return;
        appeng.helpers.WirelessTerminalGuiObject wt = (appeng.helpers.WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);

        loadInventory(this.craftingInv, tag, NBT_CRAFTING, 9);
        loadInventory(this.patternCraftingInv, tag, NBT_PATTERN_IN, 81);
        loadInventory(this.patternOutputInv, tag, NBT_PATTERN_OUT, 27);
        loadInventory(this.patternInv, tag, NBT_PATTERN_SLOTS, 2);
        loadInventory(this.rightPatternStorage, tag, NBT_RIGHT_PATTERN, 27);
        loadInventory(this.rightUpgradeStorage, tag, NBT_RIGHT_UPGRADE, 9);
        if (tag.hasKey(NBT_CRAFTING_MODE)) {
            this.craftingMode = tag.getBoolean(NBT_CRAFTING_MODE);
        }
        if (tag.hasKey(NBT_SUBSTITUTE)) {
            this.substitute = tag.getBoolean(NBT_SUBSTITUTE);
        }
        if (tag.hasKey(NBT_SCROLL_OFFSET)) {
            this.scrollOffset = tag.getInteger(NBT_SCROLL_OFFSET);
        }
        this.setCraftingMode(this.craftingMode);
    }

    private void saveToNBT() {
        if (!(this.terminalHost instanceof appeng.helpers.WirelessTerminalGuiObject)) return;
        if (!Platform.isServer()) return;
        appeng.helpers.WirelessTerminalGuiObject wt = (appeng.helpers.WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);

        saveInventory(this.craftingInv, tag, NBT_CRAFTING, 9);
        saveInventory(this.patternCraftingInv, tag, NBT_PATTERN_IN, 81);
        saveInventory(this.patternOutputInv, tag, NBT_PATTERN_OUT, 27);
        saveInventory(this.patternInv, tag, NBT_PATTERN_SLOTS, 2);
        saveInventory(this.rightPatternStorage, tag, NBT_RIGHT_PATTERN, 27);
        saveInventory(this.rightUpgradeStorage, tag, NBT_RIGHT_UPGRADE, 9);
        tag.setBoolean(NBT_CRAFTING_MODE, this.craftingMode);
        tag.setBoolean(NBT_SUBSTITUTE, this.substitute);
        tag.setInteger(NBT_SCROLL_OFFSET, this.scrollOffset);
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        this.saveToNBT();
    }

    // ================== 模式切换 ==================

    public void setCraftingMode(boolean mode) {
        this.craftingMode = mode;
        int newDefX = this.craftingMode ? 187 : 196;
        for (int g = 0; g < 9; g++) {
            for (int i = 0; i < 9; i++) {
                this.craftingSlotGroup[g][i].setDefX(newDefX);
            }
        }
        this.updateOrderOfOutputSlots();
        this.setRCSlot(this.scrollOffset);
        this.detectAndSendChanges();
    }

    public boolean isCraftingMode() {
        return this.craftingMode;
    }

    public void setSubstitute(boolean substitute) {
        this.substitute = substitute;
        this.detectAndSendChanges();
    }

    public boolean isSubstitute() {
        return this.substitute;
    }

    // ================== IOptionalSlotHost ==================

    @Override
    public boolean isSlotEnabled(int idx) {
        if (idx == 1) {
            return Platform.isServer() ? !this.craftingMode : !this.craftingMode;
        }
        if (idx == 2) {
            return Platform.isServer() ? this.craftingMode : this.craftingMode;
        }
        return false;
    }

    // ================== 合成栏配方更新 ==================

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation op, ItemStack removed, ItemStack added) {
        if (inv == this.craftingInv) {
            this.updateCraftingRecipe();
        }
    }

    private void updateCraftingRecipe() {
        InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < 9; i++) {
            ic.setInventorySlotContents(i, this.craftingSlots[i].getStack());
        }
        if (this.currentRecipe == null || !this.currentRecipe.matches(ic, this.getPlayerInv().player.world)) {
            this.currentRecipe = CraftingManager.findMatchingRecipe(ic, this.getPlayerInv().player.world);
        }
        if (this.currentRecipe == null) {
            this.craftOutputSlot.putStack(ItemStack.EMPTY);
        } else {
            this.craftOutputSlot.putStack(this.currentRecipe.getCraftingResult(ic));
        }
    }

    // ================== 编码逻辑 ==================

    public void encode() {
        this.encode(false);
    }

    public void encodeAndMoveToInventory() {
        this.encode(true);
    }

    private void encode(boolean moveToInventory) {
        ItemStack blankPattern = this.patternSlotIN.getStack();
        if (blankPattern.isEmpty()) {
            return;
        }

        ItemStack encoded = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).orElse(ItemStack.EMPTY);
        if (encoded.isEmpty()) {
            return;
        }

        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList tagIn = new NBTTagList();
        NBTTagList tagOut = new NBTTagList();

        // 输入（81格）
        for (int i = 0; i < 81; i++) {
            ItemStack stack = this.patternCraftingInv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                tagIn.appendTag(this.createItemTag(stack));
            }
        }

        // 输出
        if (this.craftingMode) {
            ItemStack out = this.craftSlot.getStack();
            if (!out.isEmpty()) {
                tagOut.appendTag(this.createItemTag(out));
            }
        } else {
            for (int i = 0; i < 27; i++) {
                ItemStack out = this.patternOutputInv.getStackInSlot(i);
                if (!out.isEmpty()) {
                    tagOut.appendTag(this.createItemTag(out));
                }
            }
        }

        if (tagIn.tagCount() == 0 || tagOut.tagCount() == 0) {
            return;
        }

        tag.setTag("in", tagIn);
        tag.setTag("out", tagOut);
        tag.setBoolean("crafting", this.craftingMode);
        tag.setBoolean("substitute", this.substitute);
        encoded.setTagCompound(tag);

        // 扣除空白样板并放入编码样板
        blankPattern.shrink(1);
        if (blankPattern.getCount() <= 0) {
            this.patternSlotIN.putStack(ItemStack.EMPTY);
        }

        if (moveToInventory) {
            if (!this.getPlayerInv().addItemStackToInventory(encoded)) {
                this.patternSlotOUT.putStack(encoded);
            }
        } else {
            this.patternSlotOUT.putStack(encoded);
        }

        this.detectAndSendChanges();
    }

    private NBTTagCompound createItemTag(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        if (!stack.isEmpty()) {
            stack.writeToNBT(tag);
            tag.setInteger("Count", stack.getCount());
        }
        return tag;
    }

    // ================== 数量调整 ==================

    public void multiply(int multiple) {
        IItemHandler inv = this.craftingMode ? this.craftingInv : this.patternCraftingInv;
        int size = this.craftingMode ? 9 : 81;
        IItemHandler outInv = this.craftingMode ? this.craftingOutput : this.patternOutputInv;
        int outSize = this.craftingMode ? 1 : 27;

        boolean canMul = true;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() * multiple < 1) {
                canMul = false;
                break;
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() * multiple < 1) {
                canMul = false;
                break;
            }
        }
        if (!canMul) return;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() * multiple);
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() * multiple);
            }
        }
        this.detectAndSendChanges();
    }

    public void divide(int divide) {
        IItemHandler inv = this.craftingMode ? this.craftingInv : this.patternCraftingInv;
        int size = this.craftingMode ? 9 : 81;
        IItemHandler outInv = this.craftingMode ? this.craftingOutput : this.patternOutputInv;
        int outSize = this.craftingMode ? 1 : 27;

        boolean canDiv = true;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() % divide != 0) {
                canDiv = false;
                break;
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() % divide != 0) {
                canDiv = false;
                break;
            }
        }
        if (!canDiv) return;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() / divide);
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() / divide);
            }
        }
        this.detectAndSendChanges();
    }

    public void increase(int amount) {
        IItemHandler inv = this.craftingMode ? this.craftingInv : this.patternCraftingInv;
        int size = this.craftingMode ? 9 : 81;
        IItemHandler outInv = this.craftingMode ? this.craftingOutput : this.patternOutputInv;
        int outSize = this.craftingMode ? 1 : 27;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(Math.max(1, s.getCount() + amount));
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(Math.max(1, s.getCount() + amount));
            }
        }
        this.detectAndSendChanges();
    }

    public void decrease(int amount) {
        IItemHandler inv = this.craftingMode ? this.craftingInv : this.patternCraftingInv;
        int size = this.craftingMode ? 9 : 81;
        IItemHandler outInv = this.craftingMode ? this.craftingOutput : this.patternOutputInv;
        int outSize = this.craftingMode ? 1 : 27;

        boolean canDecrease = true;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() - amount < 1) {
                canDecrease = false;
                break;
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() - amount < 1) {
                canDecrease = false;
                break;
            }
        }
        if (!canDecrease) return;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() - amount);
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() - amount);
            }
        }
        this.detectAndSendChanges();
    }

    public void maximizeCount() {
        IItemHandler inv = this.craftingMode ? this.craftingInv : this.patternCraftingInv;
        int size = this.craftingMode ? 9 : 81;
        IItemHandler outInv = this.craftingMode ? this.craftingOutput : this.patternOutputInv;
        int outSize = this.craftingMode ? 1 : 27;

        int maxGrowth = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                maxGrowth = Math.min(maxGrowth, s.getMaxStackSize() - s.getCount());
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                maxGrowth = Math.min(maxGrowth, s.getMaxStackSize() - s.getCount());
            }
        }
        if (maxGrowth <= 0 || maxGrowth == Integer.MAX_VALUE) return;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() + maxGrowth);
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() + maxGrowth);
            }
        }
        this.detectAndSendChanges();
    }

    // ================== 清除 ==================

    public void clearPattern() {
        if (this.craftingMode) {
            for (int i = 0; i < 9; i++) {
                this.craftingInv.extractItem(i, Integer.MAX_VALUE, false);
            }
            this.craftingOutput.extractItem(0, Integer.MAX_VALUE, false);
        } else {
            for (int i = 0; i < 81; i++) {
                this.patternCraftingInv.extractItem(i, Integer.MAX_VALUE, false);
            }
            for (int i = 0; i < 27; i++) {
                this.patternOutputInv.extractItem(i, Integer.MAX_VALUE, false);
            }
        }
        this.detectAndSendChanges();
    }

    // ================== IContainerCraftingPacket ==================

    @Override
    public appeng.api.networking.IGridNode getNetworkNode() {
        if (this.terminalHost instanceof appeng.api.networking.IGridHost) {
            return ((appeng.api.networking.IGridHost) this.terminalHost).getGridNode(appeng.api.util.AEPartLocation.INTERNAL);
        }
        return null;
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("crafting".equals(name)) {
            return this.craftingInv;
        }
        if ("output".equals(name)) {
            return this.craftingOutput;
        }
        if ("pattern".equals(name)) {
            return new appeng.tile.inventory.AppEngInternalInventory(null, 2);
        }
        return null;
    }

    @Override
    public appeng.api.networking.security.IActionSource getActionSource() {
        return super.getActionSource();
    }

    @Override
    public boolean useRealItems() {
        return true;
    }

    @Override
    public ItemStack[] getViewCells() {
        return new ItemStack[0];
    }

    // ================== 物品库更新回调 ==================

    public interface IInventoryUpdateListener {
        void onInventoryUpdate(List<IAEItemStack> list);
    }

    private IInventoryUpdateListener inventoryListener;

    public void setInventoryListener(IInventoryUpdateListener listener) {
        this.inventoryListener = listener;
    }

    @Override
    public void saveChanges() {
        // AppEngInternalInventory 已自动处理，无需额外保存
    }

    @Override
    public void postUpdate(List<IAEItemStack> list) {
        for (IAEItemStack is : list) {
            this.items.add(is);
        }
        if (this.inventoryListener != null) {
            this.inventoryListener.onInventoryUpdate(list);
        }
    }

    // ================== 辅助类 ==================
}

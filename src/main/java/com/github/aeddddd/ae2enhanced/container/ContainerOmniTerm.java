package com.github.aeddddd.ae2enhanced.container;

import appeng.api.AEApi;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.channels.IItemStorageChannel;
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
import appeng.helpers.ItemStackHelper;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.client.gui.slot.SlotOmniUpgrade;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private IRecipe currentPatternRecipe;
    private AppEngInternalInventory craftingInv;

    // === 编码区 ===
    public AppEngInternalInventory patternCraftingInv;
    public AppEngInternalInventory patternOutputInv;
    private AppEngInternalInventory patternInv;
    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    private final RCSlotFakeCraftingMatrix[][] patternInputSlots = new RCSlotFakeCraftingMatrix[9][9];
    private final RCSlotPatternOutputs[][] outputSlotGroup = new RCSlotPatternOutputs[9][3];
    private SlotPatternTerm craftSlot;
    private SlotRestrictedInput patternSlotIN;
    private SlotRestrictedInput patternSlotOUT;

    @GuiSync(97)
    public boolean patternCraftMode = true;
    @GuiSync(96)
    public boolean substitute = false;

    private int scrollOffset = 0;

    // === 右侧存储 ===
    private final AppEngInternalInventory rightPatternStorage = new AppEngInternalInventory(null, 27);
    private final AppEngInternalInventory rightUpgradeStorage = new AppEngInternalInventory(null, 9);

    // === 宿主 ===
    private final ITerminalHost terminalHost;

    // === 编码区布局常量（可扩展） ===
    public static final int CRAFTING_GRID_BASE_X = 187;
    public static final int PROCESSING_GRID_BASE_X = 196;
    public static final int GRID_Y = 93;
    public static final int GRID_COL_SPACING = 18;
    public static final int GRID_ROW_SPACING = 18;

    // === NBT Keys ===
    private static final String NBT_CRAFTING = "omni_crafting";
    private static final String NBT_PATTERN_IN = "omni_pattern_in";
    private static final String NBT_PATTERN_OUT = "omni_pattern_out";
    private static final String NBT_PATTERN_SLOTS = "omni_pattern_slots";
    private static final String NBT_RIGHT_PATTERN = "omni_right_pattern";
    private static final String NBT_RIGHT_UPGRADE = "omni_right_upgrade";
    private static final String NBT_CRAFTING_MODE = "omni_crafting_mode";
    private static final String NBT_SUBSTITUTE = "omni_substitute";
    private static final String NBT_SCROLL_OFFSET = "omni_scroll_offset";

    public ContainerOmniTerm(InventoryPlayer ip, ITerminalHost host) {
        super(ip, host, host instanceof appeng.api.implementations.guiobjects.IGuiItemObject ? (appeng.api.implementations.guiobjects.IGuiItemObject) host : null, false);
        this.terminalHost = host;

        this.setupCraftingArea(ip, host);
        this.setupPatternArea(ip, host);
        this.setupRightStorage();
        this.addCustomPlayerInventory(ip, 8, 167, 225);

        for (int i = 0; i < this.cellView.length; i++) {
            if (this.cellView[i] != null) {
                this.inventorySlots.remove(this.cellView[i]);
                this.cellView[i] = null;
            }
        }

        for (int i = 0; i < this.inventorySlots.size(); i++) {
            this.inventorySlots.get(i).slotNumber = i;
        }

        this.loadFromNBT();
    }

    // ================== 合成栏 ==================

    private void setupCraftingArea(InventoryPlayer ip, ITerminalHost host) {
        this.craftingInv = new AppEngInternalInventory(this, 9);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int idx = x + y * 3;
                this.craftingSlots[idx] = new SlotCraftingMatrix(this, this.craftingInv, idx, 26 + x * 18, 93 + y * 18);
                this.func_75146_a(this.craftingSlots[idx]);
            }
        }
        this.craftOutputSlot = new SlotCraftingTerm(ip.player, this.getActionSource(), this.getPowerSource(), host,
                this.craftingInv, this.craftingInv, this.craftingOutput, 133, 111, this);
        this.func_75146_a(this.craftOutputSlot);
    }

    // ================== 编码区 ==================

    private void setupPatternArea(InventoryPlayer ip, ITerminalHost host) {
        this.patternCraftingInv = new AppEngInternalInventory(this, 81);
        this.patternOutputInv = new AppEngInternalInventory(this, 27);

        for (int g = 0; g < 9; g++) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int idx = g * 9 + r * 3 + c;
                    int x = CRAFTING_GRID_BASE_X + c * GRID_COL_SPACING;
                    int y = GRID_Y + r * GRID_ROW_SPACING;
                    RCSlotFakeCraftingMatrix slot = new RCSlotFakeCraftingMatrix(this.patternCraftingInv, idx, x, y, CRAFTING_GRID_BASE_X);
                    this.patternInputSlots[g][r * 3 + c] = slot;
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

        this.patternInv = new AppEngInternalInventory(null, 2);
        this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), this.getPowerSource(), host,
                this.patternCraftingInv, this.patternInv, this.cOut, 281, 111, this, 2, this);
        this.func_75146_a(this.craftSlot);
        this.craftSlot.setIIcon(-1);

        this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                this.patternInv, 0, 319, 86, this.getInventoryPlayer());
        this.func_75146_a(this.patternSlotIN);

        this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                this.patternInv, 1, 319, 133, this.getInventoryPlayer());
        this.func_75146_a(this.patternSlotOUT);
        this.patternSlotOUT.setStackLimit(1);

        this.setRCSlot(0);
    }

    // ================== 右侧存储 ==================

    private void setupRightStorage() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = r * 9 + c;
                this.func_75146_a(new AppEngSlot(this.rightPatternStorage, idx, 180 + c * 18, 167 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.func_75146_a(new SlotOmniUpgrade(this.rightUpgradeStorage, c, 180 + c * 18, 221));
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
        if (this.patternCraftMode) {
            offset = 0;
        }
        this.scrollOffset = Math.max(0, Math.min(offset, getMaxScrollOffset()));
        for (int g = 0; g < 9; g++) {
            boolean visible = (g == offset);
            for (int i = 0; i < 9; i++) {
                RCSlotFakeCraftingMatrix slot = this.patternInputSlots[g][i];
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
                    slot.xPos = this.patternCraftMode ? -9000 : slot.getDefX();
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
        return this.patternCraftMode ? 0 : 8;
    }

    public void updateOrderOfOutputSlots() {
        if (!this.patternCraftMode) {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = -9000;
            }
        } else {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = this.craftSlot.getX();
            }
        }
    }

    // ================== 模式切换 ==================

    private void applyPatternCraftMode() {
        int newBaseX = this.patternCraftMode ? CRAFTING_GRID_BASE_X : PROCESSING_GRID_BASE_X;
        for (int g = 0; g < 9; g++) {
            for (int i = 0; i < 9; i++) {
                this.patternInputSlots[g][i].setDefX(newBaseX);
            }
        }
        this.updateOrderOfOutputSlots();
        this.setRCSlot(this.scrollOffset);
    }

    public void setPatternCraftMode(boolean mode) {
        if (this.patternCraftMode == mode) {
            return;
        }
        this.patternCraftMode = mode;
        this.saveCraftingModeToNBT();
        this.applyPatternCraftMode();
        this.detectAndSendChanges();
    }

    public boolean isPatternCraftMode() {
        return this.patternCraftMode;
    }

    public void setSubstitute(boolean substitute) {
        this.substitute = substitute;
        this.saveSubstituteToNBT();
        this.detectAndSendChanges();
    }

    public boolean isSubstitute() {
        return this.substitute;
    }

    @Override
    public void onUpdate(String field, Object oldValue, Object newValue) {
        super.onUpdate(field, oldValue, newValue);
        if ("patternCraftMode".equals(field)) {
            this.applyPatternCraftMode();
        }
    }

    // ================== IOptionalSlotHost ==================

    @Override
    public boolean isSlotEnabled(int idx) {
        if (idx == 1) {
            return !this.patternCraftMode;
        }
        if (idx == 2) {
            return this.patternCraftMode;
        }
        return false;
    }

    // ================== 合成栏配方更新 ==================

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation op, ItemStack removed, ItemStack added) {
        if (inv == this.craftingInv) {
            this.updateRealCraftingRecipe();
        } else if (inv == this.patternCraftingInv) {
            if (this.patternCraftMode) {
                this.fixPatternCraftingRecipes();
                this.updatePatternCraftingRecipe();
            }
        }
    }

    private void fixPatternCraftingRecipes() {
        if (!this.patternCraftMode) return;
        for (int x = 0; x < 9; x++) {
            ItemStack is = this.patternCraftingInv.getStackInSlot(x);
            if (!is.isEmpty()) {
                is.setCount(1);
            }
        }
    }

    private void updateRealCraftingRecipe() {
        InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < 9; i++) {
            ic.setInventorySlotContents(i, this.craftingInv.getStackInSlot(i));
        }
        if (this.currentRecipe == null || !this.currentRecipe.matches(ic, this.getPlayerInv().player.world)) {
            this.currentRecipe = CraftingManager.findMatchingRecipe(ic, this.getPlayerInv().player.world);
        }
        if (this.currentRecipe == null) {
            this.craftOutputSlot.putStack(ItemStack.EMPTY);
            this.craftingOutput.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            ItemStack result = this.currentRecipe.getCraftingResult(ic);
            this.craftOutputSlot.putStack(result);
            this.craftingOutput.setStackInSlot(0, result);
        }
    }

    private void updatePatternCraftingRecipe() {
        if (!this.patternCraftMode) return;
        InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < 9; i++) {
            ic.setInventorySlotContents(i, this.patternCraftingInv.getStackInSlot(i));
        }
        if (this.currentPatternRecipe == null || !this.currentPatternRecipe.matches(ic, this.getPlayerInv().player.world)) {
            this.currentPatternRecipe = CraftingManager.findMatchingRecipe(ic, this.getPlayerInv().player.world);
        }
        if (this.currentPatternRecipe == null) {
            this.cOut.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            ItemStack result = this.currentPatternRecipe.getCraftingResult(ic);
            this.cOut.setStackInSlot(0, result);
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
        ItemStack output = this.patternSlotOUT.getStack();
        ItemStack[] in = this.getInputs();
        ItemStack[] out = this.getOutputs();
        if (in == null || out == null) {
            return;
        }

        if (!output.isEmpty() && !this.isPattern(output)) {
            return;
        }

        if (output.isEmpty()) {
            output = this.patternSlotIN.getStack();
            if (output.isEmpty() || !this.isPattern(output)) {
                return;
            }
            output.shrink(1);
            if (output.getCount() <= 0) {
                this.patternSlotIN.putStack(ItemStack.EMPTY);
            }

            Optional<ItemStack> maybePattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
            if (maybePattern.isPresent()) {
                output = maybePattern.get();
            } else {
                return;
            }
        }

        NBTTagCompound encodedValue = new NBTTagCompound();
        NBTTagList tagIn = new NBTTagList();
        NBTTagList tagOut = new NBTTagList();

        if (this.patternCraftMode) {
            for (int i = 0; i < Math.min(in.length, 9); i++) {
                tagIn.appendTag(this.createItemTag(in[i]));
            }
            if (out.length > 0) {
                tagOut.appendTag(this.createItemTag(out[0]));
            }
        } else {
            int inCount = 0;
            for (ItemStack i : in) {
                if (!i.isEmpty() && inCount < 16) {
                    tagIn.appendTag(this.createItemTag(i));
                    inCount++;
                }
            }
            int outCount = 0;
            for (ItemStack i : out) {
                if (!i.isEmpty() && outCount < 6) {
                    tagOut.appendTag(this.createItemTag(i));
                    outCount++;
                }
            }
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.patternCraftMode);
        encodedValue.setBoolean("substitute", this.substitute);
        output.setTagCompound(encodedValue);

        if (moveToInventory) {
            if (!this.getPlayerInv().addItemStackToInventory(output)) {
                this.patternSlotOUT.putStack(output);
            }
        } else {
            this.patternSlotOUT.putStack(output);
        }

        this.detectAndSendChanges();
    }

    private ItemStack[] getInputs() {
        int size = this.patternCraftMode ? 9 : this.patternCraftingInv.getSlots();
        ItemStack[] input = new ItemStack[size];
        boolean hasValue = false;
        for (int i = 0; i < size; i++) {
            input[i] = this.patternCraftingInv.getStackInSlot(i);
            if (!input[i].isEmpty()) {
                hasValue = true;
            }
        }
        return hasValue ? input : null;
    }

    private ItemStack[] getOutputs() {
        if (this.patternCraftMode) {
            this.updatePatternCraftingRecipe();
            ItemStack out = this.cOut.getStackInSlot(0);
            if (!out.isEmpty() && out.getCount() > 0) {
                return new ItemStack[]{out};
            }
        } else {
            ArrayList<ItemStack> list = new ArrayList<>();
            boolean hasValue = false;
            for (int g = 0; g < 9; g++) {
                for (int r = 0; r < 3; r++) {
                    ItemStack out = this.outputSlotGroup[g][r].getStack();
                    if (!out.isEmpty() && out.getCount() > 0) {
                        list.add(out);
                        hasValue = true;
                    }
                }
            }
            if (hasValue) {
                return list.toArray(new ItemStack[0]);
            }
        }
        return null;
    }

    private boolean isPattern(ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }
        return AEApi.instance().definitions().items().encodedPattern().isSameAs(output)
                || AEApi.instance().definitions().materials().blankPattern().isSameAs(output);
    }

    private NBTTagCompound createItemTag(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        if (!stack.isEmpty()) {
            ItemStackHelper.stackWriteToNBT(stack, tag);
        }
        return tag;
    }

    // ================== 数量调整 ==================

    public void multiply(int multiple) {
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

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
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

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
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

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
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

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
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

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

    // ================== JEI 配方转移 ==================

    public void loadPattern(byte mode, boolean isCrafting, java.util.Map<Integer, ItemStack> inputs, java.util.Map<Integer, ItemStack> outputs) {
        // mode: 0=default/both, 1=encoding only (shift), 2=crafting only (alt)

        if (isCrafting) {
            // Crafting recipe: 可以填充左边合成台和右边编码区

            // 1. 填充左边真实合成台（mode=0 或 mode=2）
            if (mode == 0 || mode == 2) {
                this.fillCraftingGridFromJEI(inputs);
            }

            // 2. 填充右边编码区（mode=0 或 mode=1）
            if (mode == 0 || mode == 1) {
                if (!this.patternCraftMode) {
                    this.setPatternCraftMode(true);
                }
                for (int i = 0; i < 9; i++) {
                    this.patternCraftingInv.setStackInSlot(i, ItemStack.EMPTY);
                }
                this.cOut.setStackInSlot(0, ItemStack.EMPTY);
                for (java.util.Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
                    int slot = entry.getKey();
                    if (slot >= 0 && slot < 9) {
                        this.patternCraftingInv.setStackInSlot(slot, entry.getValue().copy());
                    }
                }
                if (!outputs.isEmpty()) {
                    this.cOut.setStackInSlot(0, outputs.values().iterator().next().copy());
                }
                this.updatePatternCraftingRecipe();
            }
        } else {
            // Processing recipe: 只填充编码区
            if (mode == 2) {
                mode = 0;
            }
            if (mode == 0 || mode == 1) {
                if (this.patternCraftMode) {
                    this.setPatternCraftMode(false);
                }
                // 清空当前 scrollOffset 对应的输入和输出
                int startIdx = this.scrollOffset * 9;
                for (int i = 0; i < 9 && startIdx + i < 81; i++) {
                    this.patternCraftingInv.setStackInSlot(startIdx + i, ItemStack.EMPTY);
                }
                int startOutIdx = this.scrollOffset * 3;
                for (int i = 0; i < 3 && startOutIdx + i < 27; i++) {
                    this.patternOutputInv.setStackInSlot(startOutIdx + i, ItemStack.EMPTY);
                }
                // 填充输入：按 JEI slot index 映射到当前 group 的本地 slot
                for (java.util.Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
                    int slot = entry.getKey();
                    int localSlot = slot; // JEI slot index 直接映射
                    if (localSlot >= 0 && localSlot < 9 && startIdx + localSlot < 81) {
                        this.patternCraftingInv.setStackInSlot(startIdx + localSlot, entry.getValue().copy());
                    }
                }
                // 填充输出
                int outIdx = 0;
                for (ItemStack out : outputs.values()) {
                    if (outIdx < 3 && startOutIdx + outIdx < 27) {
                        this.patternOutputInv.setStackInSlot(startOutIdx + outIdx, out.copy());
                        outIdx++;
                    }
                }
            }
        }

        this.detectAndSendChanges();
    }

    /**
     * 从 JEI 配方填充左侧真实合成台。先尝试从网络提取物品，网络中没有则从玩家背包移动。
     */
    private void fillCraftingGridFromJEI(java.util.Map<Integer, ItemStack> inputs) {
        // 1. 清空现有物品并返还网络/背包
        for (int i = 0; i < 9; i++) {
            ItemStack existing = this.craftingInv.extractItem(i, Integer.MAX_VALUE, false);
            if (!existing.isEmpty()) {
                this.returnToNetworkOrPlayer(existing);
            }
        }
        this.craftingOutput.setStackInSlot(0, ItemStack.EMPTY);

        // 2. 按 JEI slot index 提取并放入所需物品
        for (java.util.Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= 9) continue;
            ItemStack needed = entry.getValue().copy();

            // 先尝试从 AE 网络提取
            boolean filled = false;
            if (this.getPowerSource() != null && this.getCellInventory() != null) {
                try {
                    IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                    IAEItemStack toExtract = channel.createStack(needed);
                    if (toExtract != null) {
                        toExtract.setStackSize(needed.getCount());
                        IAEItemStack extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), toExtract, this.getActionSource());
                        if (extracted != null && extracted.getStackSize() > 0) {
                            this.craftingInv.setStackInSlot(slot, extracted.createItemStack());
                            filled = true;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (filled) continue;

            // 网络没有，尝试从玩家背包移动
            if (this.moveFromPlayerToCrafting(slot, needed)) {
                continue;
            }

            // 都没有，slot 保持为空
        }

        this.updateRealCraftingRecipe();
    }

    /**
     * 从玩家背包寻找匹配物品并移动到 craftingInv 的指定 slot。
     */
    private boolean moveFromPlayerToCrafting(int craftingSlot, ItemStack needed) {
        for (int i = 0; i < this.getPlayerInv().getSizeInventory(); i++) {
            ItemStack playerStack = this.getPlayerInv().getStackInSlot(i);
            if (!playerStack.isEmpty() && playerStack.isItemEqual(needed) && ItemStack.areItemStackTagsEqual(playerStack, needed)) {
                int toMove = Math.min(playerStack.getCount(), needed.getCount());
                playerStack.shrink(toMove);
                if (playerStack.getCount() <= 0) {
                    this.getPlayerInv().setInventorySlotContents(i, ItemStack.EMPTY);
                }
                ItemStack placed = needed.copy();
                placed.setCount(toMove);
                this.craftingInv.setStackInSlot(craftingSlot, placed);
                return true;
            }
        }
        return false;
    }

    // ================== 清除 ==================

    public void clearCrafting() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.craftingInv.extractItem(i, Integer.MAX_VALUE, false);
            this.returnToNetworkOrPlayer(stack);
        }
        ItemStack output = this.craftingOutput.extractItem(0, Integer.MAX_VALUE, false);
        this.returnToNetworkOrPlayer(output);
        this.updateRealCraftingRecipe();
        this.detectAndSendChanges();
    }

    public void clearPattern() {
        if (this.patternCraftMode) {
            for (int i = 0; i < 9; i++) {
                this.patternCraftingInv.setStackInSlot(i, ItemStack.EMPTY);
            }
            this.cOut.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            for (int i = 0; i < 81; i++) {
                this.patternCraftingInv.setStackInSlot(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < 27; i++) {
                this.patternOutputInv.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        this.detectAndSendChanges();
    }

    private void returnToNetworkOrPlayer(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (this.getPowerSource() == null || this.getCellInventory() == null) {
            if (!this.getPlayerInv().addItemStackToInventory(stack)) {
                this.getPlayerInv().player.dropItem(stack, false);
            }
            return;
        }
        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IAEItemStack ais = channel.createStack(stack);
        IAEItemStack remainder = Platform.poweredInsert(this.getPowerSource(), this.getCellInventory(), ais, this.getActionSource());
        if (remainder != null && remainder.getStackSize() > 0) {
            ItemStack left = remainder.createItemStack();
            if (!this.getPlayerInv().addItemStackToInventory(left)) {
                this.getPlayerInv().player.dropItem(left, false);
            }
        }
    }

    // ================== NBT 持久化 ==================

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
        if (!(this.terminalHost instanceof WirelessTerminalGuiObject)) return;
        WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) this.terminalHost;
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
            this.patternCraftMode = tag.getBoolean(NBT_CRAFTING_MODE);
        }
        if (tag.hasKey(NBT_SUBSTITUTE)) {
            this.substitute = tag.getBoolean(NBT_SUBSTITUTE);
        }
        if (tag.hasKey(NBT_SCROLL_OFFSET)) {
            this.scrollOffset = Math.max(0, Math.min(tag.getInteger(NBT_SCROLL_OFFSET), getMaxScrollOffset()));
        }

        this.applyPatternCraftMode();
    }

    private void saveToNBT() {
        if (!(this.terminalHost instanceof WirelessTerminalGuiObject)) return;
        if (!Platform.isServer()) return;
        WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);

        saveInventory(this.craftingInv, tag, NBT_CRAFTING, 9);
        saveInventory(this.patternCraftingInv, tag, NBT_PATTERN_IN, 81);
        saveInventory(this.patternOutputInv, tag, NBT_PATTERN_OUT, 27);
        saveInventory(this.patternInv, tag, NBT_PATTERN_SLOTS, 2);
        saveInventory(this.rightPatternStorage, tag, NBT_RIGHT_PATTERN, 27);
        saveInventory(this.rightUpgradeStorage, tag, NBT_RIGHT_UPGRADE, 9);
        tag.setBoolean(NBT_CRAFTING_MODE, this.patternCraftMode);
        tag.setBoolean(NBT_SUBSTITUTE, this.substitute);
        tag.setInteger(NBT_SCROLL_OFFSET, this.scrollOffset);
    }

    private void saveCraftingModeToNBT() {
        if (!(this.terminalHost instanceof WirelessTerminalGuiObject)) return;
        WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setBoolean(NBT_CRAFTING_MODE, this.patternCraftMode);
    }

    private void saveSubstituteToNBT() {
        if (!(this.terminalHost instanceof WirelessTerminalGuiObject)) return;
        WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setBoolean(NBT_SUBSTITUTE, this.substitute);
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        super.onContainerClosed(playerIn);
        this.saveToNBT();
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
            return this.patternInv;
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
}

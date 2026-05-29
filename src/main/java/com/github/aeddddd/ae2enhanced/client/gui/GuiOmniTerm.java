package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IConfigManager;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.InternalSlotME;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.client.me.SlotME;
import appeng.client.ActionKey;
import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import com.github.aeddddd.ae2enhanced.client.me.CraftingStatus;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniTermAction;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.io.IOException;

/**
 * 全能无线终端 GUI —— 物品库 + 合成栏 + 81槽位编码样板 + 右侧存储
 */
public class GuiOmniTerm extends GuiMEMonitorable implements IJEIGhostIngredients {

    private static final ResourceLocation OMNI_BG = new ResourceLocation("ae2enhanced", "textures/gui/omnigui.png");
    private static final ResourceLocation PATTERN_MODES = new ResourceLocation("ae2enhanced", "textures/gui/pattern_modes.png");
    private static final ResourceLocation CRAFTING_HIGHLIGHT = new ResourceLocation("ae2enhanced", "textures/gui/crafting.png");

    private final ContainerOmniTerm container;
    private GuiScrollbar patternScrollBar;

    // 按钮
    private GuiTabButton tabCraftButton;
    private GuiTabButton tabProcessButton;
    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;
    private GuiImgButton clearPatternBtn;

    // 动态高度相关
    private int omniRows = 3;
    private int extraHeight = 0;
    private final java.util.Map<Slot, Integer> originalSlotY = new java.util.HashMap<>();
    private boolean slotPositionsSaved = false;

    // 鼠标跟踪
    private int currentMouseX;
    private int currentMouseY;

    // 搜索框缓存（避免 keyTyped 中重复反射）
    private MEGuiTextField omniSearchField;



    public GuiOmniTerm(InventoryPlayer inventoryPlayer, ITerminalHost host) {
        super(inventoryPlayer, host, new ContainerOmniTerm(inventoryPlayer, host));
        this.container = (ContainerOmniTerm) this.inventorySlots;
        this.xSize = 357;

        // 通过反射将 final repo 替换为 OmniItemRepo（支持合成置顶）
        try {
            java.lang.reflect.Field repoField = appeng.client.gui.implementations.GuiMEMonitorable.class.getDeclaredField("repo");
            repoField.setAccessible(true);
            repoField.set(this, new com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo(this.getScrollBar(), this));
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error("[AE2E] Failed to replace ItemRepo with OmniItemRepo", e);
        }
    }

    @Override
    public void initGui() {
        this.repo.setRowSize(18);

        // 计算行数：SMALL 固定 5 行，TALL 根据屏幕高度动态
        TerminalStyle style = (TerminalStyle) AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);
        if (style == TerminalStyle.SMALL) {
            this.omniRows = 5;
        } else {
            int maxExtraRows = Math.max(0, (this.height - 251 - 32) / 18);
            this.omniRows = Math.max(3, 3 + maxExtraRows);
        }
        this.extraHeight = (this.omniRows - 3) * 18;
        final int desiredYSize = 251 + this.extraHeight;
        this.ySize = desiredYSize;
        this.xSize = 357;

        super.initGui();

        final int oldGuiTop = this.guiTop;
        final int oldGuiLeft = this.guiLeft;

        // super.initGui() 会覆盖 xSize/ySize，导致 JEI 错误识别 GUI 区域，必须恢复
        this.xSize = 357;
        this.ySize = desiredYSize;
        this.guiLeft = (this.width - 357) / 2;
        this.guiTop = (this.height - desiredYSize) / 2;

        // 1. 保存并恢复槽位原始 y 位置（AppEngSlot 用 getY() 绕过 repositionSlot 的副作用）
        if (!this.slotPositionsSaved) {
            for (Slot s : this.inventorySlots.inventorySlots) {
                if (s instanceof AppEngSlot) {
                    this.originalSlotY.put(s, ((AppEngSlot) s).getY());
                } else {
                    this.originalSlotY.put(s, s.yPos);
                }
            }
            this.slotPositionsSaved = true;
        }
        for (Slot s : this.inventorySlots.inventorySlots) {
            Integer originalY = this.originalSlotY.get(s);
            if (originalY != null) {
                s.yPos = originalY;
            }
            if (s.yPos >= 86) {
                s.yPos += this.extraHeight;
            }
        }

        // 2. 移除 super 创建的 SlotME，重新创建 18 列 × omniRows 行
        this.inventorySlots.inventorySlots.removeIf(s -> s instanceof SlotME);
        this.getMeSlots().clear();
        for (int row = 0; row < this.omniRows; row++) {
            for (int col = 0; col < 18; col++) {
                this.getMeSlots().add(new InternalSlotME(this.repo, col + row * 18, 8 + col * 18, 18 + row * 18));
            }
        }
        for (InternalSlotME me : this.getMeSlots()) {
            this.inventorySlots.inventorySlots.add(new SlotME(me));
        }

        // 4. 重新定位 AE2 标准按钮（使用相对于旧 guiLeft/guiTop 的偏移，保持原始布局比例）
        for (GuiButton btn : this.buttonList) {
            if (btn instanceof GuiImgButton) {
                GuiImgButton imgBtn = (GuiImgButton) btn;
                Settings setting = imgBtn.getSetting();
                if (setting == Settings.SORT_BY || setting == Settings.VIEW_MODE
                        || setting == Settings.SORT_DIRECTION || setting == Settings.SEARCH_MODE
                        || setting == Settings.TERMINAL_STYLE) {
                    btn.y = btn.y - oldGuiTop + this.guiTop;
                    btn.x = btn.x - oldGuiLeft + this.guiLeft;
                }
            } else if (btn instanceof GuiTabButton) {
                btn.y = btn.y - oldGuiTop + this.guiTop;
                btn.x = btn.x - oldGuiLeft + this.guiLeft;
            }
        }

        // 5. 替换搜索框
        try {
            Field searchFieldField = GuiMEMonitorable.class.getDeclaredField("searchField");
            searchFieldField.setAccessible(true);
            MEGuiTextField oldField = (MEGuiTextField) searchFieldField.get(this);
            String oldText = oldField != null ? oldField.getText() : "";
            boolean wasFocused = oldField != null && oldField.isFocused();
            boolean autoFocus = false;
            try {
                Field autoFocusField = GuiMEMonitorable.class.getDeclaredField("isAutoFocus");
                autoFocusField.setAccessible(true);
                autoFocus = autoFocusField.getBoolean(this);
            } catch (Exception ignored) {}
            
            MEGuiTextField newField = new MEGuiTextField(this.fontRenderer, this.guiLeft + 204, this.guiTop + 4, 125, 11);
            newField.setMaxStringLength(35);
            newField.setTextColor(0xFFFFFF);
            newField.setSelectionColor(-16744448);
            newField.setEnableBackgroundDrawing(false);
            newField.setFocused(autoFocus || wasFocused);
            newField.setText(oldText);
            searchFieldField.set(this, newField);
            this.omniSearchField = newField;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 6. 设置物品库滚动条
        GuiScrollbar itemScrollBar = this.getScrollBar();
        if (itemScrollBar != null) {
            itemScrollBar.setLeft(337).setTop(18).setHeight(this.omniRows * 18);
            itemScrollBar.setRange(0, Math.max(0, (this.repo.size() + 17) / 18 - this.omniRows), 1);
        }

        // 7. 添加合成计划按钮（先移除 super.initGui() 在 viewCell=true 时创建的中间位置旧按钮）
        try {
            Field oldField = GuiMEMonitorable.class.getDeclaredField("craftingStatusBtn");
            oldField.setAccessible(true);
            GuiTabButton oldBtn = (GuiTabButton) oldField.get(this);
            if (oldBtn != null) {
                this.buttonList.remove(oldBtn);
            }
        } catch (Exception ignored) {}

        GuiTabButton craftingStatusBtn = new GuiTabButton(this.guiLeft + 335, this.guiTop - 4, 178, GuiText.CraftingStatus.getLocal(), this.itemRender);
        craftingStatusBtn.setHideEdge(13);
        this.buttonList.add(craftingStatusBtn);
        try {
            Field craftingStatusBtnField = GuiMEMonitorable.class.getDeclaredField("craftingStatusBtn");
            craftingStatusBtnField.setAccessible(true);
            craftingStatusBtnField.set(this, craftingStatusBtn);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 8. 添加编码区按钮
        this.setupPatternButtons();

        // 9. 编码区滚动条
        this.patternScrollBar = new GuiScrollbar();
        this.patternScrollBar.setLeft(180).setTop(88 + this.extraHeight).setHeight(66);
        this.patternScrollBar.setRange(0, this.container.getMaxScrollOffset(), 1);

        // 10. 反射修正 rows/perRow
        try {
            Field rowsField = GuiMEMonitorable.class.getDeclaredField("rows");
            rowsField.setAccessible(true);
            rowsField.setInt(this, this.omniRows);
            Field perRowField = GuiMEMonitorable.class.getDeclaredField("perRow");
            perRowField.setAccessible(true);
            perRowField.setInt(this, 18);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 11. 设置容器物品库更新回调
        this.container.setInventoryListener(list -> {
            for (IAEItemStack is : list) {
                this.repo.postUpdate(is);
            }
            this.repo.updateView();
            this.updateItemScrollRange();
        });
        this.container.setGui(this);
    }

    private void setupPatternButtons() {
        int gl = this.guiLeft;
        int gt = this.guiTop;
        int fy = gt + this.extraHeight; // 固定区域基准 y（随物品库行数增加而下移）

        // 切换 Crafting/Processing 模式按钮 — 位于编码区右上角
        this.tabCraftButton = new GuiTabButton(gl + 335, fy + 74, new ItemStack(Blocks.CRAFTING_TABLE), "Crafting", this.itemRender);
        this.buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(gl + 335, fy + 74, new ItemStack(Blocks.FURNACE), "Processing", this.itemRender);
        this.buttonList.add(this.tabProcessButton);

        // Substitute / Clear 按钮 — 位于合成区左上方
        this.substitutionsEnabledBtn = new GuiImgButton(gl + 240, fy + 92, Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(gl + 240, fy + 92, Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        this.clearBtn = new GuiImgButton(gl + 80, fy + 92, Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        // 编码区清空按钮 — 位于处理模式九宫格右上角右侧，间隔1像素
        this.clearPatternBtn = new GuiImgButton(gl + 251, fy + 92, Settings.ACTIONS, ActionItems.CLOSE);
        this.clearPatternBtn.setHalfSize(true);
        this.buttonList.add(this.clearPatternBtn);

        // 编码区快捷操作按钮（位于编码区右侧，避免与合成区重叠）
        this.x3Btn = new GuiImgButton(gl + 180, fy + 157, Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(gl + 190, fy + 157, Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(gl + 200, fy + 157, Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(gl + 210, fy + 157, Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(gl + 220, fy + 157, Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(gl + 230, fy + 157, Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.buttonList.add(this.minusOneBtn);

        this.encodeBtn = new GuiImgButton(gl + 319, fy + 110, Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);
    }

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        try {
            String action = null;
            String value = "1";

            if (this.tabCraftButton == btn || this.tabProcessButton == btn) {
                action = "CraftMode";
                value = this.tabProcessButton == btn ? "1" : "0";
            } else if (this.encodeBtn == btn) {
                action = "Encode";
                value = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) ? "2" : "1";
            } else if (this.clearBtn == btn) {
                action = "ClearCrafting";
            } else if (this.clearPatternBtn == btn) {
                action = "ClearPattern";
            } else if (this.x2Btn == btn) {
                action = "MultiplyByTwo";
            } else if (this.x3Btn == btn) {
                action = "MultiplyByThree";
            } else if (this.divTwoBtn == btn) {
                action = "DivideByTwo";
            } else if (this.divThreeBtn == btn) {
                action = "DivideByThree";
            } else if (this.plusOneBtn == btn) {
                action = "IncreaseByOne";
            } else if (this.minusOneBtn == btn) {
                action = "DecreaseByOne";
            } else if (this.substitutionsEnabledBtn == btn || this.substitutionsDisabledBtn == btn) {
                action = "Substitute";
                value = this.substitutionsEnabledBtn == btn ? "0" : "1";
            }

            if (action != null) {
                AE2Enhanced.network.sendToServer(new PacketOmniTermAction(action, value));
            }
            
            // 让 AE2 标准按钮（SortDir/ViewMode/TerminalStyle 等）也能正常工作
            super.actionPerformed(btn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // 动态拼接 omnigui.png 背景
        this.mc.getTextureManager().bindTexture(OMNI_BG);
        // 顶部固定 18 像素
        Gui.drawModalRectWithCustomSizedTexture(offsetX, offsetY, 0, 0, 357, 18, 512, 512);
        // 物品库可重复行（纹理 y=36~54 为可重复的一行）
        for (int i = 0; i < this.omniRows; i++) {
            Gui.drawModalRectWithCustomSizedTexture(offsetX, offsetY + 18 + i * 18, 0, 36, 357, 18, 512, 512);
        }
        // 底部固定区域（纹理 y=72~251）
        int bottomY = offsetY + 18 + this.omniRows * 18;
        Gui.drawModalRectWithCustomSizedTexture(offsetX, bottomY, 0, 72, 357, 251 - 72, 512, 512);

        // 绑定并绘制 pattern_modes.png 编码区背景
        this.mc.getTextureManager().bindTexture(PATTERN_MODES);
        int modeY = this.container.isPatternCraftMode() ? 0 : 66;
        this.drawTexturedModalRect(offsetX + 180, offsetY + 86 + this.extraHeight, 0, modeY, 124, 66);

        // 合成置顶：第一行高亮背景（crafting.png 只有 9 格，需平铺两次覆盖 18 格）
        java.util.List<CraftingStatus> activeCrafting = this.container.getClientActiveCrafting();
        if (!activeCrafting.isEmpty()) {
            this.mc.getTextureManager().bindTexture(CRAFTING_HIGHLIGHT);
            int hlLeft = offsetX + 7; // 向左偏移 1 像素
            this.drawTexturedModalRect(hlLeft, offsetY + 18, 0, 0, 162, 18);
            this.drawTexturedModalRect(hlLeft + 162, offsetY + 18, 0, 0, 162, 18);
            // 为每个正在合成的物品单独绘制动态颜色描边
            int top = offsetY + 18;
            int bottom = top + 18;
            long time = System.currentTimeMillis();
            int count = Math.min(activeCrafting.size(), 18);
            for (int i = 0; i < count; i++) {
                CraftingStatus status = activeCrafting.get(i);
                int borderColor = this.getCraftingBorderColor(status, time);
                if ((borderColor >>> 24) == 0) {
                    continue; // 已完成（alpha=0），跳过描边
                }
                int slotLeft = hlLeft + i * 18;
                int slotRight = slotLeft + 18;
                Gui.drawRect(slotLeft, top, slotRight, top + 1, borderColor);     // 上
                Gui.drawRect(slotLeft, bottom - 1, slotRight, bottom, borderColor); // 下
                Gui.drawRect(slotLeft, top, slotLeft + 1, bottom, borderColor);     // 左
                Gui.drawRect(slotRight - 1, top, slotRight, bottom, borderColor);   // 右
            }
        }

        // 手动绘制搜索框（因为 super.drawBG 被覆盖）
        if (this.omniSearchField != null) {
            this.omniSearchField.drawTextBox();
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;

        // 更新按钮可见性
        if (this.container.isPatternCraftMode()) {
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;
            this.clearBtn.visible = true;
            this.clearPatternBtn.visible = true;
            this.x2Btn.visible = false;
            this.x3Btn.visible = false;
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = false;
            this.minusOneBtn.visible = false;
            if (this.container.isSubstitute()) {
                this.substitutionsEnabledBtn.visible = true;
                this.substitutionsDisabledBtn.visible = false;
            } else {
                this.substitutionsEnabledBtn.visible = false;
                this.substitutionsDisabledBtn.visible = true;
            }
        } else {
            this.tabCraftButton.visible = false;
            this.tabProcessButton.visible = true;
            this.clearBtn.visible = true;
            this.clearPatternBtn.visible = true;
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = false;
            this.x2Btn.visible = true;
            this.x3Btn.visible = true;
            this.divTwoBtn.visible = true;
            this.divThreeBtn.visible = true;
            this.plusOneBtn.visible = true;
            this.minusOneBtn.visible = true;
        }

        // 绘制编码区滚动条
        if (!this.container.isPatternCraftMode() && this.patternScrollBar != null) {
            this.patternScrollBar.draw(this);
        }

        // 绘制标题文本
        this.fontRenderer.drawString(net.minecraft.client.resources.I18n.format("gui.ae2enhanced.omni_terminal.title"), 8, 6, 0x404040);
        this.fontRenderer.drawString(net.minecraft.client.resources.I18n.format("gui.ae2enhanced.omni_terminal.inventory"), 8, 155 + this.extraHeight, 0x404040);

    }

    /**
     * 根据合成进度计算动态描边颜色。
     * 进度从高（刚开始）到低（快完成）：橙色 → 绿色，同时带呼吸脉冲效果。
     * 已完成（remaining <= 0）时返回完全透明。
     */
    private int getCraftingBorderColor(CraftingStatus status, long time) {
        if (status.isDone()) {
            return 0;
        }
        float ratio = status.getRatio(); // 1.0=刚开始, 0.0=即将完成
        // 颜色插值：橙色(255,136,0) → 绿色(0,255,0)
        int r = (int) (255 * ratio);
        int g = (int) (255 - 119 * ratio);
        int b = 0;
        // 呼吸脉冲：频率随进度加快（快完成时闪烁更快）
        double pulse = Math.sin(time / (200.0 + 300.0 * ratio)) * 0.5 + 0.5;
        int alpha = (int) (120 + pulse * 80 + (1.0f - ratio) * 40);
        if (alpha > 255) alpha = 255;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void updateScreen() {
        // 同步 active crafting 数据到 OmniItemRepo
        if (this.repo instanceof com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) {
            com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo omniRepo =
                    (com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo) this.repo;
            omniRepo.setActiveCrafting(this.container.getClientActiveCrafting());
        }

        super.updateScreen();
        // 修复物品库滚动条位置和范围（super.updateScreen 中的 setScrollBar 会重置它们）
        GuiScrollbar bar = this.getScrollBar();
        if (bar != null) {
            bar.setLeft(335).setTop(18).setHeight(this.omniRows * 18);
            int maxScroll = Math.max(0, (this.repo.size() + 17) / 18 - this.omniRows);
            bar.setRange(0, maxScroll, 1);
        }
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        // 中键点击 ghost slot → 打开数量输入子 GUI
        if (btn == 2) {
            Slot slot = this.getSlotUnderMouse(xCoord, yCoord);
            if (slot instanceof RCSlotFakeCraftingMatrix) {
                int amount = slot.getStack().isEmpty() ? 1 : slot.getStack().getCount();
                this.mc.displayGuiScreen(new GuiOmniAmount(this, 0, slot.getSlotIndex(), amount));
                return;
            } else if (slot instanceof RCSlotPatternOutputs) {
                int amount = slot.getStack().isEmpty() ? 1 : slot.getStack().getCount();
                this.mc.displayGuiScreen(new GuiOmniAmount(this, 1, slot.getSlotIndex(), amount));
                return;
            }
        }

        // 编码区滚动条点击
        if (!this.container.isPatternCraftMode() && this.patternScrollBar != null) {
            this.patternScrollBar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
            int newOffset = this.patternScrollBar.getCurrentScroll();
            if (newOffset != this.container.getScrollOffset()) {
                this.container.setRCSlot(newOffset);
                AE2Enhanced.network.sendToServer(new PacketOmniTermAction("Scroll", String.valueOf(newOffset)));
            }
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            MEGuiTextField searchField = this.omniSearchField;
            if (searchField != null) {
                // F 键 JEI 搜索（在 TOGGLE_FOCUS 和搜索栏输入之前处理）
                if (key == Keyboard.KEY_F && !searchField.isFocused()) {
                    JEISearchKeyHandler.performSearch(this, searchField);
                    return;
                }

                if (AppEng.proxy.isActionKey(ActionKey.TOGGLE_FOCUS, key)) {
                    searchField.setFocused(!searchField.isFocused());
                    return;
                }

                if (searchField.isFocused() && key == 28) {
                    searchField.setFocused(false);
                    return;
                }
                if (character == ' ' && searchField.getText().isEmpty()) {
                    return;
                }
                boolean mouseInGui = this.isPointInRegion(0, 0, this.xSize, this.ySize, this.currentMouseX, this.currentMouseY);
                boolean wasSearchFieldFocused = searchField.isFocused();

                String searchMode = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE).name();
                boolean isAutoFocus = searchMode.contains("AUTOSEARCH");
                if (isAutoFocus && !searchField.isFocused() && mouseInGui) {
                    searchField.setFocused(true);
                }
                if (searchField.textboxKeyTyped(character, key)) {
                    this.repo.setSearchString(searchField.getText());
                    this.repo.updateView();
                    this.updateItemScrollRange();
                } else {
                    if (!wasSearchFieldFocused) {
                        searchField.setFocused(false);
                    }
                    super.keyTyped(character, key);
                }
                return;
            }
        }
        super.keyTyped(character, key);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        this.repo.updateView();
    }

    @Override
    public void handleMouseInput() throws IOException {
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            int mx = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int my = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            boolean inPatternArea = !this.container.isPatternCraftMode()
                    && mx >= this.guiLeft + 180 && mx <= this.guiLeft + 316
                    && my >= this.guiTop + 88 + this.extraHeight && my <= this.guiTop + 154 + this.extraHeight;
            if (inPatternArea) {
                // 编码区滚轮独立处理，阻止 super 同时滚动物品库
                this.patternScrollBar.wheel(delta);
                int newOffset = this.patternScrollBar.getCurrentScroll();
                if (newOffset != this.container.getScrollOffset()) {
                    this.container.setRCSlot(newOffset);
                    AE2Enhanced.network.sendToServer(new PacketOmniTermAction("Scroll", String.valueOf(newOffset)));
                }
                return;
            }
        }
        super.handleMouseInput();
    }

    private void updateItemScrollRange() {
        int maxScroll = Math.max(0, (this.repo.size() + 17) / 18 - this.omniRows);
        GuiScrollbar bar = this.getScrollBar();
        if (bar != null) {
            bar.setRange(0, maxScroll, 1);
        }
    }

    private Slot getSlotUnderMouse(int mouseX, int mouseY) {
        for (int i = 0; i < this.inventorySlots.inventorySlots.size(); i++) {
            Slot slot = this.inventorySlots.inventorySlots.get(i);
            if (this.isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    // ---- JEI Ghost Ingredient Handler ----

    private final java.util.Map<mezz.jei.api.gui.IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new java.util.HashMap<>();

    @Override
    public java.util.List<mezz.jei.api.gui.IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        this.mapTargetSlot.clear();

        boolean isItem = ingredient instanceof ItemStack;
        boolean isFluid = ingredient instanceof net.minecraftforge.fluids.FluidStack;
        boolean isGas = false;
        if (!isItem && !isFluid) {
            try {
                isGas = ingredient.getClass().getName().equals("mekanism.api.gas.GasStack");
            } catch (Exception ignored) {
            }
        }

        if (!isItem && !isFluid && !isGas) {
            return java.util.Collections.emptyList();
        }

        java.util.ArrayList<mezz.jei.api.gui.IGhostIngredientHandler.Target<?>> targets = new java.util.ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof RCSlotFakeCraftingMatrix) {
                if (((RCSlotFakeCraftingMatrix) slot).visible) {
                    GhostIngredientTarget target = new GhostIngredientTarget(this.getGuiLeft(), this.getGuiTop(), slot);
                    targets.add(target);
                    this.mapTargetSlot.putIfAbsent(target, slot);
                }
            } else if (slot instanceof RCSlotPatternOutputs) {
                if (((RCSlotPatternOutputs) slot).visible) {
                    GhostIngredientTarget target = new GhostIngredientTarget(this.getGuiLeft(), this.getGuiTop(), slot);
                    targets.add(target);
                    this.mapTargetSlot.putIfAbsent(target, slot);
                }
            }
        }
        return targets;
    }

    @Override
    public java.util.Map<mezz.jei.api.gui.IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return this.mapTargetSlot;
    }
}

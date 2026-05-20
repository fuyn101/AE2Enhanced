package com.github.aeddddd.ae2enhanced.client.gui;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IConfigManager;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.InternalSlotME;
import appeng.container.slot.AppEngSlot;
import appeng.client.me.SlotME;
import appeng.client.ActionKey;
import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.util.IConfigManagerHost;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.network.PacketOmniTermAction;
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
import java.util.Iterator;

/**
 * 全能无线终端 GUI —— 物品库 + 合成栏 + 81槽位编码样板 + 右侧存储
 */
public class GuiOmniTerm extends GuiMEMonitorable {

    private static final ResourceLocation OMNI_BG = new ResourceLocation("ae2enhanced", "textures/gui/omnigui.png");
    private static final ResourceLocation PATTERN_MODES = new ResourceLocation("ae2enhanced", "textures/gui/pattern_modes.png");

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
    private GuiImgButton maxCountBtn;

    // 鼠标跟踪
    private int currentMouseX;
    private int currentMouseY;

    public GuiOmniTerm(InventoryPlayer inventoryPlayer, ITerminalHost host) {
        super(inventoryPlayer, host, new ContainerOmniTerm(inventoryPlayer, host));
        this.container = (ContainerOmniTerm) this.inventorySlots;
        this.xSize = 357;
        this.ySize = 251;
    }

    @Override
    public void initGui() {
        // 设置 repo 行大小为 18（我们的物品库是 18 列）
        this.repo.setRowSize(18);

        // super.initGui() 会计算 rows/ySize、创建 InternalSlotME、搜索框、滚动条、按钮
        super.initGui();

        // 保存 super.initGui() 计算出的旧 guiTop（可能基于极大的 ySize）
        final int oldGuiTop = this.guiTop;

        // 修正尺寸和位置（固定 357×251）
        this.xSize = 357;
        this.ySize = 251;
        this.guiLeft = (this.width - this.xSize) / 2;
        this.guiTop = (this.height - this.ySize) / 2;

        // 1. 恢复被 repositionSlot 修改的所有 AppEngSlot 的 y 位置
        // repositionSlot 只改 yPos，不改 xPos；xPos 由 setRCSlot 控制，不可恢复
        for (Slot s : this.inventorySlots.inventorySlots) {
            if (s instanceof AppEngSlot) {
                AppEngSlot aeSlot = (AppEngSlot) s;
                s.yPos = aeSlot.getY();
            }
        }

        // 2. 移除 super.initGui() 创建的 SlotME，重新创建 18 列 × 3 行
        this.inventorySlots.inventorySlots.removeIf(s -> s instanceof SlotME);
        this.getMeSlots().clear();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 18; col++) {
                this.getMeSlots().add(new InternalSlotME(this.repo, col + row * 18, 8 + col * 18, 18 + row * 18));
            }
        }
        for (InternalSlotME me : this.getMeSlots()) {
            this.inventorySlots.inventorySlots.add(new SlotME(me));
        }

        // 2b. 重新编号所有 slot（移除并重新添加 SlotME 后保持索引一致）
        for (int i = 0; i < this.inventorySlots.inventorySlots.size(); i++) {
            this.inventorySlots.inventorySlots.get(i).slotNumber = i;
        }

        // 3. 重新定位 AE2 标准按钮
        for (GuiButton btn : this.buttonList) {
            if (btn instanceof GuiImgButton) {
                GuiImgButton imgBtn = (GuiImgButton) btn;
                Settings setting = imgBtn.getSetting();
                if (setting == Settings.SORT_BY || setting == Settings.VIEW_MODE
                        || setting == Settings.SORT_DIRECTION || setting == Settings.SEARCH_MODE
                        || setting == Settings.TERMINAL_STYLE) {
                    btn.y = btn.y - oldGuiTop + this.guiTop;
                    btn.x = this.guiLeft - 18;
                }
            } else if (btn instanceof GuiTabButton) {
                // craftingStatusBtn — 右上角
                btn.y = btn.y - oldGuiTop + this.guiTop;
                btn.x = this.guiLeft + 335;
            }
        }

        // 4. 替换搜索框为正确尺寸
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
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 5. 设置物品库滚动条
        GuiScrollbar itemScrollBar = this.getScrollBar();
        if (itemScrollBar != null) {
            itemScrollBar.setLeft(337).setTop(18).setHeight(52);
            itemScrollBar.setRange(0, Math.max(0, (this.repo.size() + 17) / 18 - 3), 1);
        }

        // 6. 添加编码区按钮
        this.setupPatternButtons();

        // 7. 编码区滚动条
        this.patternScrollBar = new GuiScrollbar();
        this.patternScrollBar.setLeft(185).setTop(88).setHeight(66);
        this.patternScrollBar.setRange(0, this.container.getMaxScrollOffset(), 1);

        // 8. 反射修正 rows/perRow 为固定值，防止 super.updateScreen 中的 setScrollBar 计算异常
        try {
            Field rowsField = GuiMEMonitorable.class.getDeclaredField("rows");
            rowsField.setAccessible(true);
            rowsField.setInt(this, 3);
            Field perRowField = GuiMEMonitorable.class.getDeclaredField("perRow");
            perRowField.setAccessible(true);
            perRowField.setInt(this, 18);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 9. 设置容器物品库更新回调
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

        // 切换 Crafting/Processing 模式按钮 — 位于编码区右上角
        this.tabCraftButton = new GuiTabButton(gl + 335, gt + 74, new ItemStack(Blocks.CRAFTING_TABLE), "Crafting", this.itemRender);
        this.buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(gl + 335, gt + 74, new ItemStack(Blocks.FURNACE), "Processing", this.itemRender);
        this.buttonList.add(this.tabProcessButton);

        // Substitute / Clear 按钮 — 位于合成区左上方
        this.substitutionsEnabledBtn = new GuiImgButton(gl + 250, gt + 92, Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(gl + 250, gt + 92, Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        this.clearBtn = new GuiImgButton(gl + 80, gt + 92, Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        // 编码区快捷操作按钮（位于编码区右侧，避免与合成区重叠）
        this.x3Btn = new GuiImgButton(gl + 180, gt + 157, Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(gl + 190, gt + 155, Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(gl + 200, gt + 155, Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(gl + 210, gt + 155, Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(gl + 220, gt + 155, Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(gl + 230, gt + 155, Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.buttonList.add(this.minusOneBtn);

        this.encodeBtn = new GuiImgButton(gl + 319, gt + 107, Settings.ACTIONS, ActionItems.ENCODE);
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
                action = "Clear";
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
            // ignore
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // 绑定并绘制 omnigui.png 整体背景 (512x512 texture)
        this.mc.getTextureManager().bindTexture(OMNI_BG);
        Gui.drawModalRectWithCustomSizedTexture(offsetX, offsetY, 0, 0, 357, 251, 512, 512);

        // 绑定并绘制 pattern_modes.png 编码区背景
        this.mc.getTextureManager().bindTexture(PATTERN_MODES);
        int modeY = this.container.isCraftingMode() ? 0 : 66;
        this.drawTexturedModalRect(offsetX + 180, offsetY + 86, 0, modeY, 124, 66);

        // 手动绘制搜索框（因为 super.drawBG 被覆盖）
        try {
            Field searchFieldField = GuiMEMonitorable.class.getDeclaredField("searchField");
            searchFieldField.setAccessible(true);
            MEGuiTextField searchField = (MEGuiTextField) searchFieldField.get(this);
            if (searchField != null) {
                searchField.drawTextBox();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;

        // 更新按钮可见性
        if (this.container.isCraftingMode()) {
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;
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
        if (!this.container.isCraftingMode() && this.patternScrollBar != null) {
            this.patternScrollBar.draw(this);
        }

        // 绘制标题文本
        this.fontRenderer.drawString("Omni Terminal", 8, 6, 0x404040);
        this.fontRenderer.drawString("Inventory", 8, 155, 0x404040);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 修复物品库滚动条位置和范围（super.updateScreen 中的 setScrollBar 会重置它们）
        GuiScrollbar bar = this.getScrollBar();
        if (bar != null) {
            bar.setLeft(335).setTop(18).setHeight(52);
            int maxScroll = Math.max(0, (this.repo.size() + 17) / 18 - 3);
            bar.setRange(0, maxScroll, 1);
        }
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        // 编码区滚动条点击
        if (!this.container.isCraftingMode() && this.patternScrollBar != null) {
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
            if (AppEng.proxy.isActionKey(ActionKey.TOGGLE_FOCUS, key)) {
                try {
                    Field searchFieldField = GuiMEMonitorable.class.getDeclaredField("searchField");
                    searchFieldField.setAccessible(true);
                    MEGuiTextField searchField = (MEGuiTextField) searchFieldField.get(this);
                    if (searchField != null) {
                        searchField.setFocused(!searchField.isFocused());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            try {
                Field searchFieldField = GuiMEMonitorable.class.getDeclaredField("searchField");
                searchFieldField.setAccessible(true);
                MEGuiTextField searchField = (MEGuiTextField) searchFieldField.get(this);
                if (searchField != null) {
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
            } catch (Exception e) {
                e.printStackTrace();
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
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            int mx = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int my = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            boolean inPatternArea = !this.container.isCraftingMode()
                    && mx >= this.guiLeft + 180 && mx <= this.guiLeft + 304
                    && my >= this.guiTop + 88 && my <= this.guiTop + 154;
            boolean inPatternScroll = !this.container.isCraftingMode() && this.patternScrollBar != null
                    && mx >= this.guiLeft + 160 && mx <= this.guiLeft + 172
                    && my >= this.guiTop + 88 && my <= this.guiTop + 154;
            if (inPatternArea || inPatternScroll) {
                this.patternScrollBar.wheel(delta);
                int newOffset = this.patternScrollBar.getCurrentScroll();
                if (newOffset != this.container.getScrollOffset()) {
                    this.container.setRCSlot(newOffset);
                    AE2Enhanced.network.sendToServer(new PacketOmniTermAction("Scroll", String.valueOf(newOffset)));
                }
            }
        }
    }

    private void updateItemScrollRange() {
        int maxScroll = Math.max(0, (this.repo.size() + 17) / 18 - 3);
        GuiScrollbar bar = this.getScrollBar();
        if (bar != null) {
            bar.setRange(0, maxScroll, 1);
        }
    }
}

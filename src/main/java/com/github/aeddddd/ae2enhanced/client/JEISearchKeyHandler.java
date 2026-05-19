package com.github.aeddddd.ae2enhanced.client;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ItemRepo;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IIngredientListOverlay;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * F7：在 AE2 终端打开时，对 JEI 物品列表中的物品按下配置键位，
 * 自动将该物品的显示名称填入终端搜索栏。
 *
 * <p>扩展设计：
 * <ul>
 *   <li>当前支持 GuiMEMonitorable 及其所有子类（合成终端、无线终端等）</li>
 *   <li>当前仅处理 ItemStack / FluidStack 类型</li>
 *   <li>键位通过 {@link AE2EnhancedConfig#client#jeiSearchKey} 配置（LWJGL key code）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class JEISearchKeyHandler {

    private static IJeiRuntime jeiRuntime = null;

    public static void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @SubscribeEvent
    public static void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        int configKey = AE2EnhancedConfig.client.jeiSearchKey;
        if (configKey <= 0) return; // 功能已禁用
        if (!Keyboard.getEventKeyState()) return; // 只处理按键按下

        int key = Keyboard.getEventKey();
        if (key == 0) {
            key = Keyboard.getEventCharacter() + 256;
        }
        if (key != configKey) return;

        if (!(event.getGui() instanceof GuiMEMonitorable)) return;
        GuiMEMonitorable gui = (GuiMEMonitorable) event.getGui();

        // 获取搜索栏，检查是否已获得焦点
        MEGuiTextField searchField;
        try {
            Field searchFieldField = GuiMEMonitorable.class.getDeclaredField("searchField");
            searchFieldField.setAccessible(true);
            searchField = (MEGuiTextField) searchFieldField.get(gui);
            if (searchField == null) return;
            if (searchField.isFocused()) return; // 搜索栏已聚焦时不触发，避免打断输入
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to access terminal search field", e);
            return;
        }

        // 获取 JEI 悬停物品
        if (jeiRuntime == null) return;
        IIngredientListOverlay overlay = jeiRuntime.getIngredientListOverlay();
        if (overlay == null) return;
        Object ingredient = overlay.getIngredientUnderMouse();
        if (ingredient == null) return;

        String searchText = extractSearchText(ingredient);
        if (searchText == null || searchText.isEmpty()) return;

        // 设置搜索栏文本并立即刷新 repo
        try {
            searchField.setText(searchText);

            // 更新 repo 搜索字符串（立即生效，无需等待 drawScreen）
            Field repoField = GuiMEMonitorable.class.getDeclaredField("repo");
            repoField.setAccessible(true);
            ItemRepo repo = (ItemRepo) repoField.get(gui);
            if (repo != null) {
                repo.setSearchString(searchText);
            }

            // 更新 static memoryText（使关闭再打开 GUI 时保留搜索词）
            Field memoryTextField = GuiMEMonitorable.class.getDeclaredField("memoryText");
            memoryTextField.setAccessible(true);
            memoryTextField.set(null, searchText);

            // 调整滚动条以匹配新的搜索结果数量
            Method setScrollBarMethod = GuiMEMonitorable.class.getDeclaredMethod("setScrollBar");
            setScrollBarMethod.setAccessible(true);
            setScrollBarMethod.invoke(gui);

            // 聚焦搜索栏，方便用户继续输入或确认
            searchField.setFocused(true);

            AE2Enhanced.LOGGER.debug("[AE2E] JEI search key pressed: set terminal search to '{}'", searchText);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to set terminal search text from JEI hover", e);
            return;
        }

        // 消费按键事件，防止输入字符进入 GUI
        event.setCanceled(true);
    }

    private static String extractSearchText(Object ingredient) {
        String raw = null;
        if (ingredient instanceof ItemStack) {
            raw = ((ItemStack) ingredient).getDisplayName();
        } else if (ingredient instanceof net.minecraftforge.fluids.FluidStack) {
            raw = ((net.minecraftforge.fluids.FluidStack) ingredient).getLocalizedName();
        } else {
            // 对未知类型尝试通用 getDisplayName
            try {
                Method m = ingredient.getClass().getMethod("getDisplayName");
                Object result = m.invoke(ingredient);
                if (result instanceof String) raw = (String) result;
            } catch (Exception ignored) {
            }
        }
        if (raw == null) return null;
        // 去除 Minecraft 颜色代码（§[0-9a-fk-or]）
        return TextFormatting.getTextWithoutFormattingCodes(raw);
    }
}

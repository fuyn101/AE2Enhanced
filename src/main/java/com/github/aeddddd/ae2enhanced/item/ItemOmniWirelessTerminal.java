package com.github.aeddddd.ae2enhanced.item;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.features.IWirelessTermHandler;
import appeng.util.ConfigManager;
import appeng.api.util.IConfigManager;
import appeng.core.AEConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 全能无线终端 —— 物品库 + 合成栏 + 编码样板(81槽位) + 右侧存储
 */
public class ItemOmniWirelessTerminal extends AEBasePoweredItem implements IWirelessTermHandler {

    public ItemOmniWirelessTerminal() {
        super(AEConfig.instance().getWirelessTerminalBattery());
        setMaxStackSize(1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setTranslationKey("ae2enhanced.omni_wireless_terminal");
        setRegistryName("omni_wireless_terminal");
        AEApi.instance().registries().wireless().registerWirelessHandler(this);
    }

    @Override
    public boolean canHandle(ItemStack is) {
        return is.getItem() == this;
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        return Platform.openNbtData(item).getString("encryptionKey");
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        Platform.openNbtData(item).setString("encryptionKey", encKey);
    }

    @Override
    public boolean usePower(EntityPlayer player, double amt, ItemStack is) {
        return this.extractAEPower(is, amt, appeng.api.config.Actionable.MODULATE) >= amt - 0.5;
    }

    @Override
    public boolean hasPower(EntityPlayer player, double amt, ItemStack is) {
        return this.getAECurrentPower(is) >= amt;
    }

    @Override
    public IConfigManager getConfigManager(ItemStack target) {
        ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });
        out.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        out.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        out.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        out.readFromNBT(Platform.openNbtData(target).copy());
        return out;
    }

    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        return new IGuiHandler() {
            @Override
            public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
                return null;
            }
            @Override
            public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
                return null;
            }
        };
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            ItemStack held = player.getHeldItem(hand);
            if (!this.hasPower(player, 0.5, held)) {
                return new ActionResult<>(EnumActionResult.FAIL, held);
            }
            player.openGui(AE2Enhanced.instance, GuiHandler.GUI_OMNI_TERMINAL, world, 0, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean isFull3D() {
        return false;
    }
}

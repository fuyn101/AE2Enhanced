package com.github.aeddddd.ae2enhanced.central;

import ae2.api.crafting.PatternDetailsHelper;
import ae2.api.inventories.InternalInventory;
import ae2.helpers.InterfaceLogic;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.filter.IAEItemFilter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 中枢 ME 接口逻辑.
 *
 * <p>基于 AE2S 的 {@link InterfaceLogic},扩展远程目标绑定与本地样板物品栏.
 * 未来远程处理处理器会读取 {@link #getBindings()} 以决定将产物推送到哪里.</p>
 */
public class CentralInterfaceLogic extends InterfaceLogic {

    private final CentralInterfaceHost centralHost;
    private final AppEngInternalInventory patternInventory;
    private final List<TargetBinding> bindings = new ArrayList<>();

    public CentralInterfaceLogic(@Nonnull ae2.api.networking.IManagedGridNode gridNode,
                                 @Nonnull CentralInterfaceHost host,
                                 @Nonnull Item machineType) {
        this(gridNode, host, machineType, 36);
    }

    public CentralInterfaceLogic(@Nonnull ae2.api.networking.IManagedGridNode gridNode,
                                 @Nonnull CentralInterfaceHost host,
                                 @Nonnull Item machineType,
                                 int patternSlots) {
        super(gridNode, host, machineType);
        this.centralHost = host;
        this.patternInventory = new AppEngInternalInventory(new PatternInventoryHost(), patternSlots, 1,
                new PatternInventoryFilter());
    }

    @Nonnull
    public CentralInterfaceHost getCentralHost() {
        return centralHost;
    }

    @Nonnull
    public InternalInventory getPatternInventory() {
        return patternInventory;
    }

    @Nonnull
    public List<TargetBinding> getBindings() {
        return Collections.unmodifiableList(bindings);
    }

    public void addBinding(@Nonnull TargetBinding binding) {
        bindings.remove(binding);
        bindings.add(binding);
        centralHost.saveChanges();
    }

    public void removeBinding(@Nonnull TargetBinding binding) {
        bindings.remove(binding);
        centralHost.saveChanges();
    }

    public void clearBindings() {
        bindings.clear();
        centralHost.saveChanges();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        patternInventory.writeToNBT(tag, "centralPatterns");

        NBTTagList list = new NBTTagList();
        for (TargetBinding binding : bindings) {
            list.appendTag(binding.writeToNBT());
        }
        tag.setTag("centralBindings", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("centralPatterns", Constants.NBT.TAG_LIST)) {
            patternInventory.readFromNBT(tag, "centralPatterns");
        }
        bindings.clear();
        if (tag.hasKey("centralBindings", Constants.NBT.TAG_LIST)) {
            NBTTagList list = tag.getTagList("centralBindings", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                bindings.add(TargetBinding.readFromNBT(list.getCompoundTagAt(i)));
            }
        }
    }

    /**
     * 用于绑定线渲染等场景的描述文本.
     */
    public ITextComponent getDescription() {
        return new TextComponentTranslation("gui.ae2enhanced.central_interface");
    }

    private class PatternInventoryHost implements InternalInventoryHost {
        @Override
        public void saveChangedInventory(AppEngInternalInventory inv) {
            centralHost.saveChanges();
        }

        @Override
        public boolean isClientSide() {
            return centralHost.getTileEntity().getWorld() != null
                    && centralHost.getTileEntity().getWorld().isRemote;
        }
    }

    private class PatternInventoryFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                return true;
            }
            return PatternDetailsHelper.decodePattern(stack, centralHost.getTileEntity().getWorld()) != null;
        }
    }
}

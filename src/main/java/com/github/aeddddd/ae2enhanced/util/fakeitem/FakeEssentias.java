package com.github.aeddddd.ae2enhanced.util.fakeitem;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import net.minecraft.item.ItemStack;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

/**
 * 源质假物品的安全工具类与 FakeItemHandler 注册。
 *
 * 安全方法（isEssentiaFakeItem / tryGetAspectTag / tryConvertContainerToFake）
 * 使用字符串比较或反射，避免在 Thaumcraft 不存在时触发 NoClassDefFoundError。
 *
 * init() 方法在 Thaumcraft 存在时由 AE2Enhanced.preInit() 调用，
 * 注册 FakeItemHandler 使 Essentia 接入 FakeItemRegister 统一路径。
 */
public class FakeEssentias {

    private static final String ESSENTIA_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop";

    /**
     * 在模组初始化时注册源质 FakeItemHandler。
     * 仅在 Thaumcraft 存在时调用（由 AE2Enhanced.preInit() 控制）。
     */
    public static void init() {
        FakeItemRegister.registerHandler(ItemEssentiaDrop.class, new FakeItemHandler<IAEEssentiaStack, IAEEssentiaStack>() {

            @Override
            public IAEEssentiaStack getStack(ItemStack stack) {
                if (stack.isEmpty()) return null;
                String aspectTag = ItemEssentiaDrop.getAspectTag(stack);
                if (aspectTag == null) return null;
                EssentiaStack essStack = new EssentiaStack(aspectTag, 1);
                IAEEssentiaStack result = AEEssentiaStack.fromEssentiaStack(essStack);
                if (result != null) {
                    result.setStackSize(stack.getCount());
                }
                return result;
            }

            @Override
            public IAEEssentiaStack getStack(IAEItemStack stack) {
                return stack == null ? null : getStack(stack.createItemStack());
            }

            @Override
            public IAEEssentiaStack getAEStack(ItemStack stack) {
                return getStack(stack);
            }

            @Override
            public IAEEssentiaStack getAEStack(IAEItemStack stack) {
                return getStack(stack);
            }

            @Override
            public ItemStack packStack(IAEEssentiaStack essentia) {
                if (essentia == null || essentia.getAspect() == null) return null;
                return ItemEssentiaDrop.createStack(essentia.getAspect().getTag(), (int) essentia.getStackSize());
            }

            @Override
            public IAEItemStack packAEStack(IAEEssentiaStack essentia) {
                if (essentia == null || essentia.getAspect() == null) return null;
                ItemStack fakeItem = ItemEssentiaDrop.createStack(essentia.getAspect().getTag(), 1);
                IAEItemStack result = AEItemStack.fromItemStack(fakeItem);
                if (result != null) {
                    result.setStackSize(essentia.getStackSize());
                }
                return result;
            }

            @Override
            public IAEItemStack packAEStackLong(IAEEssentiaStack essentia) {
                return packAEStack(essentia);
            }
        });
    }

    /**
     * 判断 ItemStack 是否是源质假物品。
     * 使用字符串比较而非直接引用 ItemEssentiaDrop 类，避免 Thaumcraft 不存在时
     * 触发 NoClassDefFoundError。
     */
    /**
     * 从 IAEItemStack 中解析 IAEEssentiaStack。
     * 支持 ItemEssentiaDrop 和 Thaumic Energistics 的 ItemDummyAspect。
     */
    public static IAEEssentiaStack unpackEssentia(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack stack = itemStack.createItemStack();
        if (isEssentiaFakeItem(stack)) {
            return FakeItemRegister.getAEStack(itemStack);
        }
        // 兼容 Thaumic Energistics 的 ItemDummyAspect
        if ("thaumicenergistics.item.ItemDummyAspect".equals(stack.getItem().getClass().getName())) {
            try {
                if (stack.hasTagCompound()) {
                    net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
                    if (tag.hasKey("aspect", 8)) {
                        String aspectTag = tag.getString("aspect");
                        EssentiaStack essStack = new EssentiaStack(aspectTag, (int) itemStack.getStackSize());
                        IAEEssentiaStack result = AEEssentiaStack.fromEssentiaStack(essStack);
                        return result;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean isEssentiaFakeItem(ItemStack stack) {
        return !stack.isEmpty() && ESSENTIA_DROP_CLASS.equals(stack.getItem().getClass().getName());
    }

    /**
     * 安全获取源质假物品的 aspect 标签。
     * 使用反射调用 ItemEssentiaDrop.getAspectTag，仅在确认是源质假物品后调用。
     * 由于方法延迟链接，Thaumcraft 不存在时此方法不会被调用（isEssentiaFakeItem 先返回 false）。
     */
    public static String tryGetAspectTag(ItemStack stack) {
        if (!isEssentiaFakeItem(stack)) return null;
        try {
            Class<?> clazz = Class.forName(ESSENTIA_DROP_CLASS);
            return (String) clazz.getMethod("getAspectTag", ItemStack.class).invoke(null, stack);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射方法：从源质容器（IEssentiaContainerItem）转换为 ItemEssentiaDrop。
     * 供 Container / GhostIngredientTarget 调用，避免硬引用 Thaumcraft API。
     */
    public static ItemStack tryConvertContainerToFake(ItemStack held) {
        if (held == null || held.isEmpty()) return null;
        try {
            Class<?> containerItemClass = Class.forName("thaumcraft.api.aspects.IEssentiaContainerItem");
            if (!containerItemClass.isInstance(held.getItem())) return null;
            Object containerItem = held.getItem();
            Object aspectList = containerItemClass.getMethod("getAspects", ItemStack.class).invoke(containerItem, held);
            if (aspectList == null) return null;
            Object[] aspects = (Object[]) aspectList.getClass().getMethod("getAspects").invoke(aspectList);
            if (aspects == null || aspects.length == 0) return null;
            Object aspect = aspects[0];
            String aspectTag = (String) aspect.getClass().getMethod("getTag").invoke(aspect);
            // 安全调用 ItemEssentiaDrop.createStack（仅在 Thaumcraft 存在时执行到此处）
            Class<?> essentiaDropClass = Class.forName(ESSENTIA_DROP_CLASS);
            return (ItemStack) essentiaDropClass.getMethod("createStack", String.class, int.class)
                    .invoke(null, aspectTag, 1);
        } catch (Exception e) {
            return null;
        }
    }
}

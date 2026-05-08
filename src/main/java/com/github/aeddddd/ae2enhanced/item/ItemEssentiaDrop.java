package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.client.render.EssentiaItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.api.aspects.Aspect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 源质假物品（Essentia Drop）。
 * 用于在标准 AE2 物品终端中显示源质存储。
 *
 * 关键设计：使用 ItemStack 的 metadata（itemDamage）来编码 aspect 类型，而非 NBT。
 * 原因：AEItemStackRegistry 的 equals/hashCode 不包含 NBT，导致 NBT 在 NetworkMonitor
 * 的序列化/反序列化中丢失。metadata 会被 AEItemStack 完整保留并通过网络同步。
 */
public class ItemEssentiaDrop extends Item {

    private static final Map<String, Integer> ASPECT_TO_META = new HashMap<>();
    private static final Map<Integer, String> META_TO_ASPECT = new HashMap<>();
    private static boolean mapsInitialized = false;

    public ItemEssentiaDrop() {
        setRegistryName(AE2Enhanced.MOD_ID, "essentia_drop");
        setTranslationKey(AE2Enhanced.MOD_ID + ".essentia_drop");
        setCreativeTab(null);
        setHasSubtypes(true); // 使用 metadata 区分 aspect，避免显示为损坏状态
    }

    private static synchronized void initAspectMaps() {
        if (mapsInitialized) return;
        mapsInitialized = true;

        int aspectCount = Aspect.aspects != null ? Aspect.aspects.size() : -1;
        AE2Enhanced.LOGGER.info("[AE2E] initAspectMaps: Aspect.aspects.size={}", aspectCount);

        List<String> tags = new ArrayList<>();
        if (Aspect.aspects != null) {
            for (Aspect aspect : Aspect.aspects.values()) {
                if (aspect != null && aspect.getTag() != null) {
                    tags.add(aspect.getTag());
                }
            }
        }
        Collections.sort(tags); // 确定性排序，确保客户端/服务器映射一致

        AE2Enhanced.LOGGER.info("[AE2E] initAspectMaps: registered {} aspects", tags.size());
        int meta = 0;
        for (String tag : tags) {
            ASPECT_TO_META.put(tag, meta);
            META_TO_ASPECT.put(meta, tag);
            meta++;
        }
    }

    public static int getAspectMeta(String aspectTag) {
        initAspectMaps();
        return ASPECT_TO_META.getOrDefault(aspectTag, 0);
    }

    public static String getAspectTagFromMeta(int meta) {
        initAspectMaps();
        return META_TO_ASPECT.get(meta);
    }

    /**
     * 创建指定源质类型的假物品堆叠。
     */
    public static ItemStack createStack(String aspectTag, int amount) {
        initAspectMaps();
        int meta = getAspectMeta(aspectTag);
        return new ItemStack(ModItems.ESSENTIA_DROP, amount, meta);
    }

    /**
     * 从 ItemStack 中提取源质类型标签。
     */
    public static String getAspectTag(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemEssentiaDrop)) return null;
        return getAspectTagFromMeta(stack.getItemDamage());
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String aspectTag = getAspectTag(stack);
        if (aspectTag != null) {
            Aspect aspect = Aspect.getAspect(aspectTag);
            return aspect != null ? aspect.getName() : aspectTag;
        }
        return super.getItemStackDisplayName(stack);
    }

    /**
     * 为 JEI / 创造模式标签页提供所有子类型，使 JEI 能枚举所有 aspect 变体。
     */
    @Override
    public void getSubItems(net.minecraft.creativetab.CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            initAspectMaps();
            for (String tag : ASPECT_TO_META.keySet()) {
                items.add(createStack(tag, 1));
            }
        }
    }

    /**
     * 判断 ItemStack 是否是源质假物品。
     */
    public static boolean isEssentiaDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemEssentiaDrop;
    }

    /**
     * 客户端初始化：注册自定义 TileEntityItemStackRenderer。
     * RenderItem 在 isBuiltInRenderer=true 时会优先使用 Item 自己的 renderer。
     */
    @SideOnly(Side.CLIENT)
    public void initModel() {
        this.setTileEntityItemStackRenderer(EssentiaItemRenderer.INSTANCE);
    }
}

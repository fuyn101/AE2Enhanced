package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 物品存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 BigInteger 维护数量,突破 long 上限.
 *
 * <p>同时维护服务端搜索索引（nameIndex / modIndex），支持基于关键词的快速筛选。
 * 索引在物品存入/取出时增量更新，避免遍历时的 O(N) 分词开销。
 */
public class ItemStorageAdapter extends AbstractStorageAdapter<IAEItemStack, ItemDescriptor> {

    // 服务端搜索索引：keyword -> Set<ItemDescriptor>
    private final Object2ObjectOpenHashMap<String, ObjectOpenHashSet<ItemDescriptor>> nameIndex =
            new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, ObjectOpenHashSet<ItemDescriptor>> modIndex =
            new Object2ObjectOpenHashMap<>();

    public ItemStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        file.load(storage);
        recalcTotal(); // 从文件加载后必须重新计算总数
        rebuildIndex(); // 从文件加载后重建索引
    }

    @Override
    protected ItemDescriptor createDescriptor(IAEItemStack input) {
        return new ItemDescriptor(input.createItemStack());
    }

    @Override
    protected IAEItemStack createResult(IAEItemStack request, BigInteger amount) {
        IAEItemStack result = ((IItemStorageChannel) channel).createStack(request.createItemStack());
        if (result == null) return null;
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            result.setStackSize(Long.MAX_VALUE);
        } else {
            result.setStackSize(amount.longValueExact());
        }
        return result;
    }

    @Override
    protected IAEItemStack getAETemplate(ItemDescriptor descriptor) {
        return descriptor.getAETemplate((IItemStorageChannel) channel);
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return (IStorageChannel<IAEItemStack>) channel;
    }

    // ---- 索引维护 ----

    @Override
    protected void onDescriptorAdded(ItemDescriptor descriptor) {
        String name = descriptor.getDisplayName((IItemStorageChannel) channel).toLowerCase();
        String modId = descriptor.getModId((IItemStorageChannel) channel).toLowerCase();

        for (String word : splitWords(name)) {
            ObjectOpenHashSet<ItemDescriptor> set = this.nameIndex.get(word);
            if (set == null) {
                set = new ObjectOpenHashSet<>();
                this.nameIndex.put(word, set);
            }
            set.add(descriptor);
        }
        ObjectOpenHashSet<ItemDescriptor> modSet = this.modIndex.get(modId);
        if (modSet == null) {
            modSet = new ObjectOpenHashSet<>();
            this.modIndex.put(modId, modSet);
        }
        modSet.add(descriptor);
    }

    @Override
    protected void onDescriptorRemoved(ItemDescriptor descriptor) {
        String name = descriptor.getDisplayName((IItemStorageChannel) channel).toLowerCase();
        String modId = descriptor.getModId((IItemStorageChannel) channel).toLowerCase();

        for (String word : splitWords(name)) {
            ObjectOpenHashSet<ItemDescriptor> set = this.nameIndex.get(word);
            if (set != null) {
                set.remove(descriptor);
                if (set.isEmpty()) {
                    this.nameIndex.remove(word);
                }
            }
        }
        ObjectOpenHashSet<ItemDescriptor> set = this.modIndex.get(modId);
        if (set != null) {
            set.remove(descriptor);
            if (set.isEmpty()) {
                this.modIndex.remove(modId);
            }
        }
    }

    /**
     * 从现有 storage 重建索引（用于初始化或异常恢复）.
     */
    private void rebuildIndex() {
        this.nameIndex.clear();
        this.modIndex.clear();
        for (ItemDescriptor desc : storage.keySet()) {
            onDescriptorAdded(desc);
        }
    }

    /**
     * 根据搜索词快速筛选物品.
     *
     * @param query 搜索词（小写）
     * @param isModSearch 是否为 @mod 搜索
     * @param limit 最大返回数量
     * @return 匹配的 IAEItemStack 列表（已设置数量）
     */
    public List<IAEItemStack> search(String query, boolean isModSearch, int limit) {
        if (query == null || query.isEmpty()) {
            return getAllItems(limit);
        }

        ObjectOpenHashSet<ItemDescriptor> candidates;
        if (isModSearch) {
            candidates = this.modIndex.get(query);
            if (candidates == null) {
                return Collections.emptyList();
            }
        } else {
            String[] terms = query.split(" ");
            candidates = null;
            for (String term : terms) {
                ObjectOpenHashSet<ItemDescriptor> set = this.nameIndex.get(term);
                if (set == null) {
                    return Collections.emptyList();
                }
                if (candidates == null) {
                    candidates = new ObjectOpenHashSet<>(set);
                } else {
                    candidates.retainAll(set);
                    if (candidates.isEmpty()) {
                        return Collections.emptyList();
                    }
                }
            }
            if (candidates == null) {
                return Collections.emptyList();
            }
        }

        List<IAEItemStack> results = new ArrayList<>(Math.min(candidates.size(), limit));
        for (ItemDescriptor desc : candidates) {
            BigInteger count = storage.get(desc);
            if (count == null || count.signum() <= 0) continue;

            IAEItemStack stack = getAETemplate(desc);
            stack = stack.copy();
            if (count.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                stack.setStackSize(Long.MAX_VALUE);
            } else {
                stack.setStackSize(count.longValue());
            }
            results.add(stack);
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    /**
     * 获取所有物品（带数量），用于无搜索词时的回退.
     */
    public List<IAEItemStack> getAllItems(int limit) {
        List<IAEItemStack> results = new ArrayList<>(Math.min(storage.size(), limit));
        for (java.util.Map.Entry<ItemDescriptor, BigInteger> entry : storage.entrySet()) {
            BigInteger count = entry.getValue();
            if (count.signum() <= 0) continue;

            IAEItemStack stack = getAETemplate(entry.getKey());
            stack = stack.copy();
            if (count.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                stack.setStackSize(Long.MAX_VALUE);
            } else {
                stack.setStackSize(count.longValue());
            }
            results.add(stack);
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    public Object2ObjectOpenHashMap<String, ObjectOpenHashSet<ItemDescriptor>> getNameIndex() {
        return nameIndex;
    }

    public Object2ObjectOpenHashMap<String, ObjectOpenHashSet<ItemDescriptor>> getModIndex() {
        return modIndex;
    }

    // ---- 分词工具（与 OmniItemRegistry 保持逻辑一致） ----

    private static List<String> splitWords(String input) {
        List<String> words = new ArrayList<>();
        if (input == null || input.isEmpty()) return words;

        StringBuilder asciiPart = new StringBuilder();
        StringBuilder cnPart = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isChinese(c)) {
                if (asciiPart.length() > 0) {
                    processAsciiPart(asciiPart.toString(), words);
                    asciiPart.setLength(0);
                }
                cnPart.append(c);
            } else {
                if (cnPart.length() > 0) {
                    processCnPart(cnPart.toString(), words);
                    cnPart.setLength(0);
                }
                asciiPart.append(c);
            }
        }

        if (asciiPart.length() > 0) {
            processAsciiPart(asciiPart.toString(), words);
        }
        if (cnPart.length() > 0) {
            processCnPart(cnPart.toString(), words);
        }

        return words;
    }

    private static boolean isChinese(char c) {
        return (c >= '\u4e00' && c <= '\u9fa5')
            || (c >= '\u3400' && c <= '\u4dbf')
            || (c >= '\u2e80' && c <= '\u2eff');
    }

    private static void processAsciiPart(String input, List<String> words) {
        String[] tokens = input.split("[^a-zA-Z0-9]+");
        for (String token : tokens) {
            if (token.length() <= 1) continue;
            String lower = token.toLowerCase();
            words.add(lower);

            if (token.length() > 2) {
                StringBuilder current = new StringBuilder();
                current.append(token.charAt(0));
                for (int i = 1; i < token.length(); i++) {
                    char c = token.charAt(i);
                    if (Character.isUpperCase(c)) {
                        String word = current.toString().toLowerCase();
                        if (word.length() > 1) {
                            words.add(word);
                        }
                        current = new StringBuilder();
                    }
                    current.append(Character.toLowerCase(c));
                }
                String last = current.toString().toLowerCase();
                if (last.length() > 1 && !last.equals(lower)) {
                    words.add(last);
                }
            }
        }
    }

    private static void processCnPart(String input, List<String> words) {
        if (input.length() <= 1) return;
        words.add(input);
        for (int i = 0; i < input.length(); i++) {
            words.add(String.valueOf(input.charAt(i)));
        }
    }
}

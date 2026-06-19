package com.github.aeddddd.ae2enhanced.central;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link GenericStack} 列表的快照序列化工具.
 *
 * <p>Omni Terminal、Recycler 与 Platform 都需要在客户端/服务端之间或在 NBT 中
 * 保存一组 AE 物品快照.此类提供与 {@link GenericStack#writeList}/{@link GenericStack#readList}
 * 兼容的读写封装,并添加了一些常用的集合操作.</p>
 */
public final class StackSnapshot {

    private StackSnapshot() {
    }

    /**
     * 将一组 GenericStack(允许含 null)写入 NBTTagList.
     */
    public static NBTTagList write(List<@Nullable GenericStack> stacks) {
        return GenericStack.writeList(stacks);
    }

    /**
     * 从 NBTTagList 读取一组 GenericStack(可能含 null).
     */
    public static List<@Nullable GenericStack> read(NBTTagList tag) {
        if (tag == null || tag.hasNoTags()) {
            return Collections.emptyList();
        }
        return GenericStack.readList(tag);
    }

    /**
     * 从单个 NBTTagCompound 读取一个 GenericStack,若为空则返回 null.
     */
    @Nullable
    public static GenericStack readSingle(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) {
            return null;
        }
        return GenericStack.readTag(tag);
    }

    /**
     * 将单个 GenericStack 写入 NBTTagCompound.
     */
    public static NBTTagCompound writeSingle(@Nullable GenericStack stack) {
        return GenericStack.writeTag(stack);
    }

    /**
     * 深拷贝快照列表.
     */
    public static List<GenericStack> copy(List<GenericStack> stacks) {
        List<GenericStack> result = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack == null) {
                result.add(null);
            } else {
                result.add(new GenericStack(stack.what(), stack.amount()));
            }
        }
        return result;
    }

    /**
     * 统计快照中指定 key 的总数量.
     */
    public static long count(AEKey key, List<GenericStack> stacks) {
        long total = 0;
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what().equals(key)) {
                total += stack.amount();
            }
        }
        return total;
    }

    /**
     * 将快照合并为一个 key→amount 的计数表示(以 KeyCounter 形式返回,但此处只用 List).
     */
    public static List<GenericStack> merge(List<GenericStack> stacks) {
        List<GenericStack> result = new ArrayList<>();
        for (GenericStack stack : stacks) {
            if (stack == null) continue;
            boolean merged = false;
            for (GenericStack existing : result) {
                if (existing.what().equals(stack.what())) {
                    // 新建对象避免修改入参
                    result.set(result.indexOf(existing),
                            new GenericStack(existing.what(), Math.addExact(existing.amount(), stack.amount())));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(new GenericStack(stack.what(), stack.amount()));
            }
        }
        return result;
    }
}

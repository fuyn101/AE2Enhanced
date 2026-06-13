package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 单个远程绑定目标的运行时状态封装.
 *
 * <p>替代原本分散在 {@link DualityCentralInterface} 中的多个 map，
 * 把状态、输入快照、预期产物、启动时间、已推流体等全部收敛到一个对象里。</p>
 */
public class TargetSession {

    private final TargetBinding binding;
    private final DualityCentralInterface owner;

    private TargetState state;
    private long startTime;
    private IAEItemStack[] expectedOutputs;
    private List<ItemStack> inputs;
    private List<FluidStack> pushedFluids;

    public TargetSession(TargetBinding binding, DualityCentralInterface owner) {
        this.binding = binding;
        this.owner = owner;
        this.state = TargetState.IDLE;
    }

    public TargetBinding getBinding() {
        return binding;
    }

    public TargetState getState() {
        return state;
    }

    public boolean isIdle() {
        return state == TargetState.IDLE;
    }

    public boolean isProcessing() {
        return state == TargetState.PROCESSING;
    }

    public boolean isCollecting() {
        return state == TargetState.COLLECTING;
    }

    public boolean isUnavailable() {
        return state == TargetState.UNAVAILABLE;
    }

    public long getStartTime() {
        return startTime;
    }

    public IAEItemStack[] getExpectedOutputs() {
        return expectedOutputs;
    }

    public List<ItemStack> getInputs() {
        return inputs != null ? inputs : Collections.emptyList();
    }

    public List<FluidStack> getPushedFluids() {
        return pushedFluids != null ? pushedFluids : Collections.emptyList();
    }

    /**
     * 开始一次物理推送。
     *
     * @return 成功获取坐标所有权时返回 true
     */
    public boolean beginPush(List<FluidStack> pushedFluids) {
        if (!TargetOwnershipTracker.instance().tryAcquire(binding, owner)) {
            return false;
        }
        this.state = TargetState.PUSHING;
        this.pushedFluids = pushedFluids != null ? new ArrayList<>(pushedFluids) : new ArrayList<>();
        return true;
    }

    /**
     * 推送成功，进入处理中状态。
     */
    public void commitPush(IAEItemStack[] expectedOutputs, List<ItemStack> inputs, long startTime) {
        if (this.state != TargetState.PUSHING) {
            throw new IllegalStateException("Cannot commit push from state " + this.state);
        }
        this.state = TargetState.PROCESSING;
        this.expectedOutputs = expectedOutputs != null ? expectedOutputs.clone() : null;
        this.inputs = inputs != null ? new ArrayList<>(inputs) : null;
        this.startTime = startTime;
        this.pushedFluids = null;
    }

    /**
     * 开始收集产物。
     */
    public void beginCollect() {
        if (this.state != TargetState.PROCESSING) {
            throw new IllegalStateException("Cannot begin collect from state " + this.state);
        }
        this.state = TargetState.COLLECTING;
    }

    /**
     * 收集完成，根据是否还有后续产物决定回到空闲还是继续处理。
     *
     * @param finished true 表示该次发配已完全结束
     */
    public void finishCollect(boolean finished) {
        if (this.state != TargetState.COLLECTING) {
            throw new IllegalStateException("Cannot finish collect from state " + this.state);
        }
        if (finished) {
            reset();
        } else {
            this.state = TargetState.PROCESSING;
        }
    }

    /**
     * 重置为 IDLE 并释放所有权、清空运行时数据。
     */
    public void reset() {
        TargetOwnershipTracker.instance().release(binding, owner);
        this.state = TargetState.IDLE;
        this.startTime = 0;
        this.expectedOutputs = null;
        this.inputs = null;
        this.pushedFluids = null;
    }

    /**
     * 标记目标不可用，释放所有权。
     */
    public void setUnavailable() {
        TargetOwnershipTracker.instance().release(binding, owner);
        this.state = TargetState.UNAVAILABLE;
    }

    /**
     * 从不可用恢复为空闲。
     */
    public void recoverFromUnavailable() {
        if (this.state != TargetState.UNAVAILABLE) {
            return;
        }
        this.state = TargetState.IDLE;
    }

    /**
     * 检查当前处理是否已超时。
     */
    public boolean isTimedOut(long currentWorldTime, int timeoutTicks) {
        if (this.state != TargetState.PROCESSING && this.state != TargetState.COLLECTING) {
            return false;
        }
        long elapsed = currentWorldTime - this.startTime;
        return elapsed > timeoutTicks;
    }

    /**
     * 序列化到 NBT（仅保存 PROCESSING 状态）。
     */
    public NBTTagCompound serializeProcessing() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("binding", binding.writeToNBT());
        tag.setLong("startTime", this.startTime);

        if (expectedOutputs != null && expectedOutputs.length > 0) {
            NBTTagList outList = new NBTTagList();
            for (IAEItemStack output : expectedOutputs) {
                if (output == null) continue;
                outList.appendTag(output.createItemStack().serializeNBT());
            }
            tag.setTag("outputs", outList);
        }

        if (inputs != null && !inputs.isEmpty()) {
            NBTTagList inList = new NBTTagList();
            for (ItemStack input : inputs) {
                if (input.isEmpty()) continue;
                inList.appendTag(input.serializeNBT());
            }
            tag.setTag("inputs", inList);
        }
        return tag;
    }

    /**
     * 从 NBT 恢复为 PROCESSING 状态。
     */
    public static TargetSession deserializeProcessing(NBTTagCompound tag, DualityCentralInterface owner) {
        TargetBinding binding = TargetBinding.readFromNBT(tag.getCompoundTag("binding"));
        TargetSession session = new TargetSession(binding, owner);
        session.state = TargetState.PROCESSING;
        session.startTime = tag.getLong("startTime");

        if (tag.hasKey("outputs")) {
            NBTTagList outList = tag.getTagList("outputs", 10);
            IAEItemStack[] outputs = new IAEItemStack[outList.tagCount()];
            for (int i = 0; i < outList.tagCount(); i++) {
                ItemStack stack = new ItemStack(outList.getCompoundTagAt(i));
                if (!stack.isEmpty()) {
                    outputs[i] = AEItemStack.fromItemStack(stack);
                }
            }
            session.expectedOutputs = outputs;
        }

        if (tag.hasKey("inputs")) {
            NBTTagList inList = tag.getTagList("inputs", 10);
            List<ItemStack> inputs = new ArrayList<>();
            for (int i = 0; i < inList.tagCount(); i++) {
                ItemStack stack = new ItemStack(inList.getCompoundTagAt(i));
                if (!stack.isEmpty()) {
                    inputs.add(stack);
                }
            }
            session.inputs = inputs;
        }
        return session;
    }

    @Override
    public String toString() {
        return "TargetSession{" +
                "binding=" + binding +
                ", state=" + state +
                ", startTime=" + startTime +
                '}';
    }
}

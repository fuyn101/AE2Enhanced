package com.github.aeddddd.ae2enhanced.tile;

import ae2.api.AECapabilities;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.util.AECableType;
import ae2.api.util.AEPartLocation;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 委托控制器节点的网络接口 TileEntity 基类.
 *
 * ME 接口方块本身不创建独立 AE 节点，而是将网络接入委托给对应的控制器。
 * 在 AE2S 下通过 {@link IInWorldGridNodeHost} 暴露控制器的 {@link IManagedGridNode#getNode()}。
 *
 * @param <C> 控制器类型，必须继承 {@link TileAENetworkBase}
 */
public abstract class TileDelegatedProxyBase<C extends TileAENetworkBase> extends TileEntity implements IInWorldGridNodeHost {

    private BlockPos controllerPos;

    // ---- 子类必须实现 ----

    protected abstract Class<C> getControllerClass();

    protected abstract boolean isControllerFormed(C controller);

    // ---- Controller 访问 ----

    public void setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
        markDirty();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public C getController() {
        if (controllerPos == null || world == null) return null;
        TileEntity te = world.getTileEntity(controllerPos);
        return getControllerClass().isInstance(te) ? getControllerClass().cast(te) : null;
    }

    // ---- IInWorldGridNodeHost 实现 ----

    @Nullable
    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        C controller = getController();
        if (controller != null && isControllerFormed(controller)) {
            return controller.getMainNode().getNode();
        }
        return null;
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        C controller = getController();
        return (controller != null && isControllerFormed(controller)) ? AECableType.SMART : AECableType.NONE;
    }

    public void securityBreak() {
        C controller = getController();
        if (controller != null) {
            controller.disassemble();
        }
    }

    // ---- Capability ----

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.IN_WORLD_GRID_NODE_HOST) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.IN_WORLD_GRID_NODE_HOST) {
            return AECapabilities.IN_WORLD_GRID_NODE_HOST.cast(this);
        }
        return super.getCapability(capability, facing);
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("controllerX")) {
            controllerPos = new BlockPos(
                compound.getInteger("controllerX"),
                compound.getInteger("controllerY"),
                compound.getInteger("controllerZ")
            );
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (controllerPos != null) {
            compound.setInteger("controllerX", controllerPos.getX());
            compound.setInteger("controllerY", controllerPos.getY());
            compound.setInteger("controllerZ", controllerPos.getZ());
        }
        return compound;
    }
}

package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;

/**
 * 仓储中枢 ME 接口，仅作为 AE 网络物理接入点。
 * 所有网络逻辑（包括存储暴露）委托给控制器。
 */
public class TileHyperdimensionalMeInterface extends TileEntity implements IGridProxyable {

    private BlockPos controllerPos = null;

    public void setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
        markDirty();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public TileHyperdimensionalController getController() {
        if (controllerPos == null || world == null) return null;
        TileEntity te = world.getTileEntity(controllerPos);
        return te instanceof TileHyperdimensionalController ? (TileHyperdimensionalController) te : null;
    }

    // ---- IGridProxyable / IGridHost ----

    @Override
    public AENetworkProxy getProxy() {
        TileHyperdimensionalController controller = getController();
        if (controller != null) {
            return controller.getProxy();
        }
        return null;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        TileHyperdimensionalController controller = getController();
        if (controller != null && controller.isFormed()) {
            AENetworkProxy proxy = controller.getProxy();
            if (proxy != null) {
                return proxy.getNode();
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        TileHyperdimensionalController controller = getController();
        return (controller != null && controller.isFormed()) ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public void securityBreak() {
        if (controllerPos != null && world != null) {
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileHyperdimensionalController) {
                ((TileHyperdimensionalController) te).disassemble();
            }
        }
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

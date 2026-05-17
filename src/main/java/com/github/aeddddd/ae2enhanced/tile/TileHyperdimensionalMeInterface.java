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
public class TileHyperdimensionalMeInterface extends TileDelegatedProxyBase<TileHyperdimensionalController> {

    @Override
    protected Class<TileHyperdimensionalController> getControllerClass() {
        return TileHyperdimensionalController.class;
    }

    @Override
    protected boolean isControllerFormed(TileHyperdimensionalController controller) {
        return controller.isFormed();
    }
}

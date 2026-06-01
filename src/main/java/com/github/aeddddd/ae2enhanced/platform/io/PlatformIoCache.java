package com.github.aeddddd.ae2enhanced.platform.io;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class PlatformIoCache {
    private final World world;
    private final Map<Long, TileEntity> tileCache = new HashMap<>();
    private final Map<Long, IItemHandler> handlerCache = new HashMap<>();

    public PlatformIoCache(World world) {
        this.world = world;
    }

    @Nullable
    public TileEntity getTile(BlockPos pos) {
        long key = pos.toLong();
        TileEntity te = this.tileCache.get(key);
        if (te == null || te.isInvalid()) {
            te = this.world.getTileEntity(pos);
            if (te != null) {
                this.tileCache.put(key, te);
            } else {
                this.tileCache.remove(key);
            }
        }
        return te;
    }

    @Nullable
    public IItemHandler getHandler(BlockPos pos, EnumFacing face) {
        long key = pos.toLong() + ((long) face.ordinal() << 56);
        IItemHandler handler = this.handlerCache.get(key);
        if (handler == null) {
            TileEntity te = getTile(pos);
            if (te != null && !te.isInvalid()) {
                handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
                if (handler != null) {
                    this.handlerCache.put(key, handler);
                }
            }
            if (handler == null) {
                this.handlerCache.remove(key);
            }
        }
        return handler;
    }

    public void refresh(BlockPos pos) {
        long key = pos.toLong();
        this.tileCache.remove(key);
        for (EnumFacing face : EnumFacing.values()) {
            long handlerKey = pos.toLong() + ((long) face.ordinal() << 56);
            this.handlerCache.remove(handlerKey);
        }
    }

    public void remove(BlockPos pos) {
        refresh(pos);
    }
}

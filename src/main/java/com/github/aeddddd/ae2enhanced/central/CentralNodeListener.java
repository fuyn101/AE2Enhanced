package com.github.aeddddd.ae2enhanced.central;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;

/**
 * 中枢网络节点的通用监听器.
 *
 * <p>在网格变化时通知 {@link ae2.helpers.InterfaceLogic#gridChanged()} 并在
 * AE2S 要求保存时调用宿主 {@link CentralInterfaceHost#saveChanges()}.</p>
 */
public class CentralNodeListener<T extends CentralInterfaceHost> implements IGridNodeListener<T> {

    public static final CentralNodeListener<CentralInterfaceHost> INSTANCE = new CentralNodeListener<>();

    @Override
    public void onSaveChanges(T nodeOwner, IGridNode node) {
        nodeOwner.saveChanges();
    }

    @Override
    public void onGridChanged(T nodeOwner, IGridNode node) {
        nodeOwner.getInterfaceLogic().gridChanged();
    }
}

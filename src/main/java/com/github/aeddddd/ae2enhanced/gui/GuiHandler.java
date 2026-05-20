package com.github.aeddddd.ae2enhanced.gui;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyFormed;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyPattern;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyUnformed;
import com.github.aeddddd.ae2enhanced.container.ContainerComputationUnformed;
import com.github.aeddddd.ae2enhanced.container.ContainerHyperdimensionalNexus;
import com.github.aeddddd.ae2enhanced.container.ContainerHyperdimensionalUnformed;
import com.github.aeddddd.ae2enhanced.container.ContainerStockingBus;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.container.ContainerWirelessChannelTransmitter;
import com.github.aeddddd.ae2enhanced.container.ContainerUniversalExportBus;
import com.github.aeddddd.ae2enhanced.container.ContainerUniversalImportBus;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalExportBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int GUI_ASSEMBLY_CONTROLLER = 0;
    public static final int GUI_ASSEMBLY_PATTERN = 1;
    public static final int GUI_HYPERDIMENSIONAL_NEXUS = 2;
    public static final int GUI_HYPERDIMENSIONAL_UNFORMED = 3;
    public static final int GUI_COMPUTATION_FORMED = 4;
    public static final int GUI_COMPUTATION_UNFORMED = 5;
    public static final int GUI_UNIVERSAL_IMPORT_BUS = 6;
    public static final int GUI_UNIVERSAL_EXPORT_BUS = 7;
    public static final int GUI_STOCKING_BUS = 8;
    public static final int GUI_WIRELESS_CHANNEL_TRANSMITTER = 9;
    public static final int GUI_UNIVERSAL_MEMORY_CARD = 10;
    public static final int GUI_OMNI_TERMINAL = 11;

    /** 编码页码到 GUI ID：低4位为 base ID，bit8-15为页码，bit16-20为 patternPages */
    public static int encodePatternId(int page, int patternPages) {
        return GUI_ASSEMBLY_PATTERN | (page << 8) | (patternPages << 16);
    }

    /** 从 GUI ID 解码页码 */
    public static int decodePatternPage(int ID) {
        return (ID >> 8) & 0xFF;
    }

    /** 从 GUI ID 解码 patternPages */
    public static int decodePatternPages(int ID) {
        return (ID >> 16) & 0x1F;
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (te instanceof TileAssemblyController) {
            TileAssemblyController tile = (TileAssemblyController) te;
            int baseId = ID & 0xF;
            if (baseId == GUI_ASSEMBLY_CONTROLLER) {
                if (tile.isFormed()) {
                    return new ContainerAssemblyFormed(player.inventory, tile);
                } else {
                    return new ContainerAssemblyUnformed(player.inventory, tile);
                }
            } else if (baseId == GUI_ASSEMBLY_PATTERN) {
                int page = decodePatternPage(ID);
                int patternPages = decodePatternPages(ID);
                return new ContainerAssemblyPattern(player.inventory, tile, page, patternPages);
            }
        } else if (te instanceof TileHyperdimensionalController) {
            TileHyperdimensionalController tile = (TileHyperdimensionalController) te;
            if (ID == GUI_HYPERDIMENSIONAL_UNFORMED && !tile.isFormed()) {
                return new ContainerHyperdimensionalUnformed(player.inventory, tile);
            } else if (ID == GUI_HYPERDIMENSIONAL_NEXUS && tile.isFormed()) {
                return new ContainerHyperdimensionalNexus();
            }
            return null;
        } else if (te instanceof TileComputationCore) {
            TileComputationCore tile = (TileComputationCore) te;
            if (ID == GUI_COMPUTATION_UNFORMED && !tile.isFormed()) {
                return new ContainerComputationUnformed(player.inventory, tile);
            } else if (ID == GUI_COMPUTATION_FORMED && tile.isFormed()) {
                return new ContainerHyperdimensionalNexus(); // Dummy container for pure-display GUI
            }
            return null;
        }

        // Part GUI：解码 side ordinal
        int baseId = ID & 0xFF;
        if (baseId == GUI_UNIVERSAL_IMPORT_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartUniversalImportBus) {
                    return new ContainerUniversalImportBus(player.inventory, (PartUniversalImportBus) part);
                }
            }
        }
        if (baseId == GUI_UNIVERSAL_EXPORT_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartUniversalExportBus) {
                    return new ContainerUniversalExportBus(player.inventory, (PartUniversalExportBus) part);
                }
            }
        }
        if (baseId == GUI_STOCKING_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartStockingBus) {
                    return new ContainerStockingBus(player.inventory, (PartStockingBus) part);
                }
            }
        }
        if (baseId == GUI_WIRELESS_CHANNEL_TRANSMITTER) {
            if (te instanceof TileWirelessChannelTransmitter) {
                return new ContainerWirelessChannelTransmitter(player.inventory, (TileWirelessChannelTransmitter) te);
            }
        }
        if (ID == GUI_UNIVERSAL_MEMORY_CARD) {
            return new com.github.aeddddd.ae2enhanced.container.ContainerUniversalMemoryCard(player);
        }
        if (ID == GUI_OMNI_TERMINAL) {
            ItemStack held = player.getHeldItemMainhand();
            if (!(held.getItem() instanceof ItemOmniWirelessTerminal)) {
                held = player.getHeldItemOffhand();
            }
            if (held.getItem() instanceof ItemOmniWirelessTerminal) {
                appeng.api.features.IWirelessTermHandler handler = appeng.api.AEApi.instance().registries().wireless().getWirelessTerminalHandler(held);
                appeng.helpers.WirelessTerminalGuiObject host = new appeng.helpers.WirelessTerminalGuiObject(handler, held, player, world, 0, 0, 0);
                return new ContainerOmniTerm(player.inventory, host);
            }
        }

        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (te instanceof TileAssemblyController) {
            TileAssemblyController tile = (TileAssemblyController) te;
            int baseId = ID & 0xF;
            if (baseId == GUI_ASSEMBLY_CONTROLLER) {
                if (tile.isFormed()) {
                    return new GuiAssemblyFormed(player.inventory, tile);
                } else {
                    return new GuiAssemblyUnformed(player.inventory, tile);
                }
            } else if (baseId == GUI_ASSEMBLY_PATTERN) {
                int page = decodePatternPage(ID);
                int patternPages = decodePatternPages(ID);
                return new GuiAssemblyPattern(player.inventory, tile, page, patternPages);
            }
        } else if (te instanceof TileHyperdimensionalController) {
            TileHyperdimensionalController tile = (TileHyperdimensionalController) te;
            if (ID == GUI_HYPERDIMENSIONAL_NEXUS) {
                return new GuiHyperdimensionalNexus(tile);
            } else if (ID == GUI_HYPERDIMENSIONAL_UNFORMED) {
                return new GuiHyperdimensionalUnformed(player.inventory, tile);
            }
        } else if (te instanceof TileComputationCore) {
            TileComputationCore tile = (TileComputationCore) te;
            if (ID == GUI_COMPUTATION_FORMED) {
                return new GuiComputationFormed(tile);
            } else if (ID == GUI_COMPUTATION_UNFORMED) {
                return new GuiComputationUnformed(player.inventory, tile);
            }
        }

        // Part GUI：解码 side ordinal
        int baseId = ID & 0xFF;
        if (baseId == GUI_UNIVERSAL_IMPORT_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartUniversalImportBus) {
                    return new GuiUniversalImportBus(player.inventory, (PartUniversalImportBus) part);
                }
            }
        }
        if (baseId == GUI_UNIVERSAL_EXPORT_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartUniversalExportBus) {
                    return new GuiUniversalExportBus(player.inventory, (PartUniversalExportBus) part);
                }
            }
        }
        if (baseId == GUI_STOCKING_BUS) {
            int sideOrdinal = (ID >> 8) & 0xFF;
            AEPartLocation side = AEPartLocation.fromOrdinal(sideOrdinal);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartStockingBus) {
                    return new GuiStockingBus(player.inventory, (PartStockingBus) part);
                }
            }
        }
        if (baseId == GUI_WIRELESS_CHANNEL_TRANSMITTER) {
            if (te instanceof TileWirelessChannelTransmitter) {
                return new GuiWirelessChannelTransmitter(player.inventory, (TileWirelessChannelTransmitter) te);
            }
        }
        if (ID == GUI_UNIVERSAL_MEMORY_CARD) {
            return new com.github.aeddddd.ae2enhanced.gui.GuiUniversalMemoryCard(player);
        }
        if (ID == GUI_OMNI_TERMINAL) {
            ItemStack held = player.getHeldItemMainhand();
            if (!(held.getItem() instanceof ItemOmniWirelessTerminal)) {
                held = player.getHeldItemOffhand();
            }
            if (held.getItem() instanceof ItemOmniWirelessTerminal) {
                appeng.api.features.IWirelessTermHandler handler = appeng.api.AEApi.instance().registries().wireless().getWirelessTerminalHandler(held);
                appeng.helpers.WirelessTerminalGuiObject host = new appeng.helpers.WirelessTerminalGuiObject(handler, held, player, world, 0, 0, 0);
                return new com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm(player.inventory, host);
            }
        }

        return null;
    }
}

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
import com.github.aeddddd.ae2enhanced.container.ContainerUniversalImportBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.entity.player.EntityPlayer;
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

        return null;
    }
}

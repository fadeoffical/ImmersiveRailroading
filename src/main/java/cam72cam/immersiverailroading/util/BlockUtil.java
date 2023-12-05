package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.IRBlocks;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;

public final class BlockUtil {

    private BlockUtil() {}

    public static boolean canBeReplaced(World world, Vec3i position, boolean allowFlex) {
        if (world.isReplaceable(position)) return true;
        if (world.isBlock(position, IRBlocks.BLOCK_RAIL_PREVIEW)) return true;

        if (allowFlex && isIRRail(world, position)) {
            TileRailBase tileRailBase = world.getBlockEntity(position, TileRailBase.class);
            return tileRailBase != null && tileRailBase.isFlexible();
        }

        return false;
    }

    public static boolean isIRRail(World world, Vec3i position) {
        return world.isBlock(position, IRBlocks.BLOCK_RAIL_GAG) || world.isBlock(position, IRBlocks.BLOCK_RAIL);
    }
}

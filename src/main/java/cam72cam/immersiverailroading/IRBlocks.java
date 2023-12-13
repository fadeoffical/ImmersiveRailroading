package cam72cam.immersiverailroading;

import cam72cam.immersiverailroading.blocks.BlockMultiblock;
import cam72cam.immersiverailroading.blocks.BlockRail;
import cam72cam.immersiverailroading.blocks.BlockRailGag;
import cam72cam.immersiverailroading.blocks.BlockRailPreview;

public final class IRBlocks {
    public static final BlockRailPreview BLOCK_RAIL_PREVIEW = new BlockRailPreview();
    public static final BlockRailGag BLOCK_RAIL_GAG = new BlockRailGag();
    public static final BlockRail BLOCK_RAIL = new BlockRail();
    public static final BlockMultiblock BLOCK_MULTIBLOCK = new BlockMultiblock();

    public static void register() {
        // loads static classes and ctrs
    }
}

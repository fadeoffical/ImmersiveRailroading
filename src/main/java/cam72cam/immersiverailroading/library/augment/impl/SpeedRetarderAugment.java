package cam72cam.immersiverailroading.library.augment.impl;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.TrackType;
import cam72cam.immersiverailroading.library.augment.Augment;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import org.jetbrains.annotations.NotNull;

public class SpeedRetarderAugment extends Augment {

    public static final @NotNull Identifier ID = new Identifier(ImmersiveRailroading.MOD_ID, "speed_retarder");

    public SpeedRetarderAugment() {
        super(ID, Color.LIME);
    }

    @Override
    public boolean doesNotFilter() {
        return true;
    }

    @Override
    public int getRedstoneOutput(@NotNull TileRailBase rail) {
        return 0;
    }

    @Override
    public boolean canApplyAugment(@NotNull TileRailBase rail) {
        TrackType trackType = rail.getParentTile().info.settings.type;
        return trackType != TrackType.SWITCH && trackType != TrackType.TURN;
    }

    @Override
    public void update(@NotNull TileRailBase rail) {

    }
}

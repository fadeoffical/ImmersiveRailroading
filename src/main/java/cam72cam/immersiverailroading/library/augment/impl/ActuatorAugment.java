package cam72cam.immersiverailroading.library.augment.impl;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.augment.Augment;
import cam72cam.immersiverailroading.model.part.Door;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActuatorAugment extends Augment {

    public static final @NotNull Identifier ID = new Identifier(ImmersiveRailroading.MOD_ID, "actuator");

    private static final int MAX_REDSTONE = 15;

    private @Nullable String filterDefinitionId;

    public ActuatorAugment() {
        super(ID, Color.LIME);
    }

    @Override
    public boolean doesNotFilter() {
        return true;
    }

    @Override
    public boolean reactsToRedstone() {
        return false;
    }

    @Override
    public int getRedstoneOutput(@NotNull TileRailBase rail) {
        return 0;
    }

    @Override
    public boolean canApplyAugment(@NotNull TileRailBase rail) {
        return true;
    }

    @Override
    public void update(@NotNull TileRailBase rail) {
        EntityRollingStock stock = rail.getStockOverhead(EntityRollingStock.class);
        if (stock == null) return;

        float redstone = rail.getWorld().getRedstone(rail.getPos()) / (float) MAX_REDSTONE;
        stock.getDefinition()
                .getModel()
                .getDoors()
                .stream()
                .filter(door -> door.type == Door.Types.EXTERNAL)
                .forEach(door -> stock.setControlPosition(door, redstone));
    }
}

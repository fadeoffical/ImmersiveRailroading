package cam72cam.immersiverailroading.library.augment.impl;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.library.augment.PushPullAugment;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.util.Facing;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class FluidLoaderAugment extends PushPullAugment {

    public static final @NotNull Identifier ID = new Identifier(ImmersiveRailroading.MOD_ID, "fluid_loader");

    public FluidLoaderAugment() {
        super(ID, Color.BLUE);
    }

    @Override
    public void update(@NotNull TileRailBase rail) {
        super.update(rail);

        if (!this.isPushPull()) return;

        FreightTank stock = rail.getStockOverhead(FreightTank.class);
        if (stock == null) return;

        Arrays.stream(Facing.values())
                .flatMap(side -> rail.getWorld().getTank(rail.getPos().offset(side)).stream())
                .filter(Objects::nonNull)
                .forEach(tank -> stock.tank.fill(tank, 100, false));
    }
}

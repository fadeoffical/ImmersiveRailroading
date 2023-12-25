package cam72cam.immersiverailroading.library.augment.impl;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.library.augment.PushPullAugment;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.util.Facing;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class ItemLoaderAugment extends PushPullAugment {

    public static final @NotNull Identifier ID = new Identifier(ImmersiveRailroading.MOD_ID, "item_loader");

    public ItemLoaderAugment() {
        super(ID, Color.GREEN);
    }

    @Override
    public void update(@NotNull TileRailBase rail) {
        super.update(rail);

        if (!this.isPushPull()) return;

        Freight stock = rail.getStockOverhead(Freight.class);
        if (stock == null) return;

        Arrays.stream(Facing.values())
                .map(side -> rail.getWorld().getInventory(rail.getPos().offset(side)))
                .filter(Objects::nonNull)
                .forEach(inventory -> inventory.transferAllTo(stock.cargoItems));
    }

    @Override
    public boolean hasInventory(@NotNull TileRailBase rail) {
        return true;
    }
}

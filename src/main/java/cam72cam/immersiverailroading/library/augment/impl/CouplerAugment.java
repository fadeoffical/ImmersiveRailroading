package cam72cam.immersiverailroading.library.augment.impl;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.library.augment.Augment;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.util.Facing;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class CouplerAugment extends Augment {

    public static final @NotNull Identifier ID = new Identifier(ImmersiveRailroading.MOD_ID, "coupler");

    private @NotNull Mode mode;

    public CouplerAugment() {
        super(ID, Color.LIGHT_BLUE);
        this.mode = Mode.ENGAGE;
    }

    public @NotNull Mode getMode() {
        return this.mode;
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
        return true;
    }

    @Override
    public void update(@NotNull TileRailBase rail) {
        EntityCoupleableRollingStock stock = rail.getStockOverhead(EntityCoupleableRollingStock.class);
        if (stock == null) return;

        boolean engage = this.getMode() == Mode.ENGAGE;
        stock.setCouplerEngaged(EntityCoupleableRollingStock.CouplerType.FRONT, engage);
        stock.setCouplerEngaged(EntityCoupleableRollingStock.CouplerType.BACK, engage);
    }

    @Override
    public boolean onClick(@NotNull TileRailBase rail, @NotNull Player player, Player.@NotNull Hand hand, @NotNull Facing facing, @NotNull Vec3d hit) {
        if (super.onClick(rail, player, hand, facing, hit)) return true;

        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.is(Fuzzy.PISTON)) {
            this.mode = this.mode.next();
            PlayerMessage message = PlayerMessage.translate(this.mode.toString());
            if (rail.getWorld().isServer) player.sendMessage(message);
            return true;
        }

        return false;
    }

    public enum Mode {
        ENGAGE("engage"),
        DISENGAGE("disengage");

        private final @NotNull String name;

        Mode(@NotNull String name) {
            this.name = name;
        }

        public @NotNull String getName() {
            return this.name;
        }

        @Override
        public String toString() {
            return "immersiverailroading:coupler_augment_mode." + super.toString().toLowerCase(Locale.ROOT);
        }

        private @NotNull Mode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }
}

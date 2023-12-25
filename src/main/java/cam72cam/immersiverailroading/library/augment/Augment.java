package cam72cam.immersiverailroading.library.augment;

import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.util.Facing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class Augment {

    @TagField("id")
    private final @NotNull Identifier id;

    @TagField("color")
    private final @NotNull Color color;

    @TagField("redstoneMode")
    private @NotNull RedstoneMode redstoneMode;

    @TagField("filterDefinitionId")
    private @Nullable String filterDefinitionId;

    public Augment(@NotNull Identifier id, @NotNull Color color) {
        this.id = id;
        this.color = color;

        this.redstoneMode = RedstoneMode.ON;
    }

    public final @NotNull Identifier getId() {
        return this.id;
    }

    public final @NotNull Color getColor() {
        return this.color;
    }

    public boolean reactsToRedstone() {
        return true;
    }

    public boolean doesNotFilter() {
        return false;
    }

    public void updateFilter(@Nullable String definitionId, Consumer<FilterUpdateResult> result) {
        if (this.doesNotFilter()) {
            result.accept(FilterUpdateResult.UNSUPPORTED);
        } else if (definitionId == null || Objects.equals(this.filterDefinitionId, definitionId)) {
            this.filterDefinitionId = null;
            result.accept(FilterUpdateResult.RESET);
        } else {
            this.filterDefinitionId = definitionId;
            result.accept(FilterUpdateResult.SET);
        }
    }

    public boolean filter(@NotNull String definitionId) {
        return this.doesNotFilter() || definitionId.equals(this.filterDefinitionId);
    }

    public boolean canOperate(@NotNull TileRailBase rail) {
        int redstone = rail.getWorld().getRedstone(rail.getPos());
        return !this.reactsToRedstone() || this.redstoneMode.canOperate(redstone);
    }

    // todo: this is a temporary hack
    public boolean hasInventory(@NotNull TileRailBase rail) {
        return false;
    }

    // todo: this is a temporary hack
    public boolean hasTank(@NotNull TileRailBase rail) {
        return false;
    }

    public boolean onClick(@NotNull TileRailBase rail, @NotNull Player player, @NotNull Player.Hand hand, @NotNull  Facing facing, @NotNull Vec3d hit) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (this.reactsToRedstone() && (heldItem.is(Fuzzy.REDSTONE_DUST) || heldItem.is(Fuzzy.REDSTONE_TORCH))) {
            this.redstoneMode = this.redstoneMode.next();
            return true;
        }
        return false;
    }

    public abstract int getRedstoneOutput(@NotNull TileRailBase rail);

    public abstract boolean canApplyAugment(@NotNull TileRailBase rail);

    public abstract void update(@NotNull TileRailBase rail);

    public enum FilterUpdateResult {
        SET,
        RESET,
        UNSUPPORTED;
    }

    public enum RedstoneMode {
        ON(redstone -> true),
        HIGH(redstone -> redstone > 0),
        LOW(redstone -> redstone == 0),
        OFF(redstone -> false);

        private final @NotNull Predicate<Integer> redstonePredicate;

        RedstoneMode(@NotNull Predicate<Integer> redstonePredicate) {
            this.redstonePredicate = redstonePredicate;
        }

        private boolean canOperate(int redstone) {
            return this.redstonePredicate.test(redstone);
        }

        public @NotNull RedstoneMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }
}

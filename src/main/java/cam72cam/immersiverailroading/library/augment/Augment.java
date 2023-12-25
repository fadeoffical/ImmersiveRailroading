package cam72cam.immersiverailroading.library.augment;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.serialization.*;
import cam72cam.mod.util.Facing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class Augment implements TagMapper<Augment> {

    private final @NotNull Identifier id;
    private final @NotNull Color color;

    private @NotNull RedstoneMode redstoneMode;
    private @Nullable String filterDefinitionId;

    public Augment() {
        this(new Identifier(ImmersiveRailroading.MOD_ID, "null"), Color.WHITE);
        this.redstoneMode = RedstoneMode.ON;
    }

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

    public void updateFilter(@Nullable String definitionId, Consumer<FilterUpdateResult> result) {
        if (this.doesNotFilter()) {
            result.accept(FilterUpdateResult.UNSUPPORTED);
        } else if (definitionId == null || Objects.equals(this.getFilterDefinitionId(), definitionId)) {
            this.setFilterDefinitionId(null);
            result.accept(FilterUpdateResult.RESET);
        } else {
            this.setFilterDefinitionId(definitionId);
            result.accept(FilterUpdateResult.SET);
        }
    }

    // todo: "invert" method
    public boolean doesNotFilter() {
        return false;
    }

    public boolean filter(@NotNull String definitionId) {
        return this.doesNotFilter() || definitionId.equals(this.getFilterDefinitionId());
    }

    public boolean canOperate(@NotNull TileRailBase rail) {
        int redstone = rail.getWorld().getRedstone(rail.getPos());
        return !this.reactsToRedstone() || this.getRedstoneMode().canOperate(redstone);
    }

    public boolean reactsToRedstone() {
        return true;
    }

    // todo: this is a temporary hack
    public boolean hasInventory(@NotNull TileRailBase rail) {
        return false;
    }

    // todo: this is a temporary hack
    public boolean hasTank(@NotNull TileRailBase rail) {
        return false;
    }

    public boolean onClick(@NotNull TileRailBase rail, @NotNull Player player, @NotNull Player.Hand hand, @NotNull Facing facing, @NotNull Vec3d hit) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (this.reactsToRedstone() && (heldItem.is(Fuzzy.REDSTONE_DUST) || heldItem.is(Fuzzy.REDSTONE_TORCH))) {
            this.setRedstoneMode(this.getRedstoneMode().next());
            return true;
        }
        return false;
    }

    public abstract int getRedstoneOutput(@NotNull TileRailBase rail);

    public abstract boolean canApplyAugment(@NotNull TileRailBase rail);

    public abstract void update(@NotNull TileRailBase rail);

    @Override
    public TagAccessor<Augment> apply(Class<Augment> type, String fieldName, TagField tag) throws SerializationException {
        Serializer<Augment> serializer = (nbt, augment) -> {
            if (augment == null) return;

            TagCompound augmentData = new TagCompound();
            augmentData.setString("Id", augment.getId().toString());

            if (augment.reactsToRedstone())
                augmentData.setEnum("RedstoneMode", augment.getRedstoneMode());

            String filterDefinitionId = augment.getFilterDefinitionId();
            if (!augment.doesNotFilter() && filterDefinitionId != null)
                augmentData.setString("FilterDefinitionId", filterDefinitionId);

            TagSerializer.serialize(augmentData, augment, TagField.class);

            nbt.set(fieldName, augmentData);
        };

        Deserializer<Augment> deserializer = (nbt, world) -> {
            TagCompound augmentData = nbt.get(fieldName);
            if (augmentData == null) return null;
            if (!augmentData.hasKey("Id")) return null;

            String id = augmentData.getString("Id");
            Identifier identifier = new Identifier(id);

            Augment augment = AugmentRegistry.getNewAugment(identifier);
            if (augment == null) return null;

            if (augment.reactsToRedstone() && augmentData.hasKey("RedstoneMode")) {
                RedstoneMode redstoneMode = augmentData.getEnum("RedstoneMode", RedstoneMode.class);
                augment.setRedstoneMode(redstoneMode);
            }

            if (!augment.doesNotFilter() && augmentData.hasKey("FilterDefinitionId")) {
                String filterDefinitionId = augmentData.getString("FilterDefinitionId");
                augment.setFilterDefinitionId(filterDefinitionId);
            }

            TagSerializer.deserialize(augmentData, augment, world, TagField.class);

            return null;
        };

        return new TagAccessor<Augment>(serializer, deserializer) {
            @Override
            public boolean applyIfMissing() {
                return true;
            }
        };
    }

    private @NotNull RedstoneMode getRedstoneMode() {
        return this.redstoneMode;
    }

    private void setRedstoneMode(@NotNull RedstoneMode redstoneMode) {
        this.redstoneMode = redstoneMode;
    }

    private @Nullable String getFilterDefinitionId() {
        return this.filterDefinitionId;
    }

    private void setFilterDefinitionId(@Nullable String filterDefinitionId) {
        this.filterDefinitionId = filterDefinitionId;
    }

    public enum FilterUpdateResult {
        SET, RESET, UNSUPPORTED
    }

    public enum RedstoneMode {
        ON(redstone -> true), HIGH(redstone -> redstone > 0), LOW(redstone -> redstone == 0), OFF(redstone -> false);

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

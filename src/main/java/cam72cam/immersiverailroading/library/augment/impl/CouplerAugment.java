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
import cam72cam.mod.serialization.SerializationException;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapper;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.util.Facing;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

public final class CouplerAugment extends Augment {

    public static final @NotNull Identifier ID = new Identifier(ImmersiveRailroading.MOD_ID, "coupler");

    @TagField("Mode")
    private @NotNull Mode mode;

    public CouplerAugment() {
        super(ID, Color.LIGHT_BLUE);
        this.mode = Mode.ENGAGE;
    }

    @Override
    public boolean doesNotFilter() {
        return true;
    }

    @Override
    public boolean onClick(@NotNull TileRailBase rail, @NotNull Player player, Player.@NotNull Hand hand, @NotNull Facing facing, @NotNull Vec3d hit) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.is(Fuzzy.PISTON)) {
            this.mode = this.mode.next();
            PlayerMessage message = PlayerMessage.translate(this.mode.getTranslationString());
            if (rail.getWorld().isServer) player.sendMessage(message);
            return true;
        }
        return super.onClick(rail, player, hand, facing, hit);
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

    public @NotNull Mode getMode() {
        return this.mode;
    }

    public enum Mode implements TagMapper<Mode> {
        ENGAGE("engage"),
        DISENGAGE("disengage");

        private final @NotNull String name;

        Mode(@NotNull String name) {
            this.name = name;
        }

        private String getTranslationString() {
            return "immersiverailroading:coupler_augment_mode." + super.toString().toLowerCase(Locale.ROOT);
        }

        private @NotNull Mode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }

        @Override
        public TagAccessor<Mode> apply(Class<Mode> type, String fieldName, TagField tag) throws SerializationException {
            Serializer<Mode> serializer = (nbt, mode) -> {
                if (mode == null) return;
                nbt.setString(fieldName, mode.getName());
            };
            Deserializer<Mode> deserializer = (nbt, world) -> {
                String name = nbt.getString(fieldName);
                return fromName(name);
            };
            return new TagAccessor<>(serializer, deserializer);
        }

        public @NotNull String getName() {
            return this.name;
        }

        private static @NotNull Mode fromName(@NotNull String name) {
            return Arrays.stream(values())
                    .filter(mode -> mode.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mode: " + name));
        }
    }
}

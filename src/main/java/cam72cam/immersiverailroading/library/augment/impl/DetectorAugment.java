package cam72cam.immersiverailroading.library.augment.impl;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.augment.Augment;
import cam72cam.immersiverailroading.library.unit.Speed;
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

public final class DetectorAugment extends Augment {

    public static final @NotNull Identifier ID = new Identifier(ImmersiveRailroading.MOD_ID, "detector");

    private static final int MAX_REDSTONE = 15;

    private Mode mode;

    public DetectorAugment() {
        super(ID, Color.RED);
        this.mode = Config.ConfigDebug.defaultAugmentComputer ? Mode.COMPUTER : Mode.SIMPLE;
    }

    public @NotNull Mode getMode() {
        return this.mode;
    }

    public void setMode(@NotNull Mode mode) {
        this.mode = mode;
    }

    @Override
    public boolean reactsToRedstone() {
        return false;
    }

    @Override
    public int getRedstoneOutput(@NotNull TileRailBase rail) {
        EntityMovableRollingStock stock = rail.getStockOverhead(EntityMovableRollingStock.class);
        if (stock == null) return 0;

        // todo: replace this with something proper... this has no business being a switch (probably)
        //       this.mode.getRedstoneLevel(stock, rail);
        switch (this.mode) {
            case SIMPLE:
                return MAX_REDSTONE;

            case SPEED:
                // todo: originally, this has been 'speed / 10'
                //       new implementation below; it just makes much more sense
                //       at least for stock with a max speed

                double currentSpeed = stock.getCurrentSpeed().as(Speed.SpeedUnit.METERS_PER_TICK).absolute().value();

                if (stock instanceof Locomotive) {
                    Locomotive locomotive = (Locomotive) stock;
                    double maxSpeed = locomotive.getDefinition()
                            .getMaxSpeed(Gauge.getClosestGauge(rail.getTrackGauge()))
                            .as(Speed.SpeedUnit.METERS_PER_TICK)
                            .absolute()
                            .value();

                    if (maxSpeed > 0) {
                        return (int) (currentSpeed / maxSpeed * MAX_REDSTONE);
                    }
                }

                // todo: this is such an unsatisfying implementation
                //       maybe use stock.train.locomotives.sort(max_speed).highest.maxSpeed?
                //       and if no loco, well then fuck
                return (int) Math.floor(currentSpeed / 10);

            case PASSENGERS:
                return Math.min(stock.getPassengerCount(), MAX_REDSTONE);

            case CARGO:
                if (stock instanceof Freight) {
                    Freight freight = (Freight) stock;
                    return freight.getPercentCargoFull() / 100 * MAX_REDSTONE;
                }

            case LIQUID:
                if (stock instanceof FreightTank) {
                    FreightTank freightTank = (FreightTank) stock;
                    return freightTank.getPercentLiquidFull() / 100 * MAX_REDSTONE;
                }

            default:
                return 0;
        }
    }

    @Override
    public boolean canApplyAugment(@NotNull TileRailBase rail) {
        return false;
    }

    @Override
    public void update(@NotNull TileRailBase rail) {}

    @Override
    public boolean onClick(@NotNull TileRailBase rail, @NotNull Player player, Player.@NotNull Hand hand, @NotNull Facing facing, @NotNull Vec3d hit) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.is(Fuzzy.REDSTONE_DUST) || heldItem.is(Fuzzy.REDSTONE_TORCH)) {
            this.mode = this.mode.next();
            PlayerMessage message = PlayerMessage.translate(this.mode.getTranslationString());
            if (rail.getWorld().isServer) player.sendMessage(message);
            return true;
        }
        return super.onClick(rail, player, hand, facing, hit);
    }

    public enum Mode implements TagMapper<Mode> {
        SIMPLE("simple"),
        SPEED("speed"),
        PASSENGERS("passengers"),
        CARGO("cargo"),
        LIQUID("liquid"),
        COMPUTER("computer");

        private final @NotNull String name;

        Mode(@NotNull String name) {
            this.name = name;
        }

        private String getTranslationString() {
            return String.format("%s:detector_mode.%s", ImmersiveRailroading.MOD_ID, this.getName());
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

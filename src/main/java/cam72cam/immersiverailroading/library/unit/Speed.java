package cam72cam.immersiverailroading.library.unit;

import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapped;
import cam72cam.mod.serialization.TagMapper;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

@TagMapped(Speed.class)
public final class Speed implements TagMapper<Speed> {

    public static final @NotNull Speed ZERO = fromUnit(0, SpeedUnit.METERS_PER_TICK);

    private final double speed;
    private final @NotNull SpeedUnit unit;

    public static Speed fromUnit(double speed, @NotNull SpeedUnit unit) {
        return new Speed(speed, unit);
    }

    private Speed(double speed, @NotNull SpeedUnit unit) {
        this.speed = speed;
        this.unit = unit;
    }

    public @NotNull Speed as(@NotNull SpeedUnit unit) {
        return new Speed(this.unit.convertTo(this.speed, unit), unit);
    }

    public @NotNull Speed absolute() {
        return new Speed(Math.abs(this.speed), this.unit);
    }

    public boolean isZero() {
        return this.speed == 0;
    }

    public double value() {
        return this.speed;
    }

    public @NotNull String format() {
        return String.format("%.0f", this.value());
    }

    public @NotNull String formatPrecise() {
        return String.format("%.2f", this.value());
    }

    @Override
    public TagAccessor<Speed> apply(Class<Speed> type, String fieldName, TagField tag) {
        Serializer<Speed> serializer = (nbt, speed) -> {
            double value = speed.as(SpeedUnit.METERS_PER_TICK).value();
            nbt.setDouble(fieldName, value);
        };
        Deserializer<Speed> deserializer = (nbt, world) -> Speed.fromUnit(nbt.getDouble(fieldName), SpeedUnit.METERS_PER_TICK);
        return new TagAccessor<>(serializer, deserializer);
    }

    public enum SpeedUnit implements Unit {
        METERS_PER_TICK("m/t"),
        METERS_PER_SECOND("m/s", metersPerSecond -> metersPerSecond / 20, metersPerTick -> metersPerTick * 20),
        KILOMETERS_PER_HOUR("km/h", kilometersPerHour -> kilometersPerHour / 3.6 / 20, kilometersPerHour -> kilometersPerHour * 3.6 * 20),
        MILES_PER_HOUR("mph", milesPerHour -> milesPerHour / 20 / 2.23694, milesPerHour -> milesPerHour * 20 * 2.23694);

        private final @NotNull String unit;
        private final @NotNull Function<Double, Double> toMetersPerTick;
        private final @NotNull Function<Double, Double> fromMetersPerTick;

        SpeedUnit(@NotNull String unit, @NotNull Function<Double, Double> toMetersPerTick, @NotNull Function<Double, Double> fromMetersPerTick) {
            this.unit = unit;
            this.toMetersPerTick = toMetersPerTick;
            this.fromMetersPerTick = fromMetersPerTick;
        }

        SpeedUnit(@NotNull String unit) {
            this(unit, speed -> speed, speed -> speed);
        }

        private double convertToMetersPerTick(double speed) {
            return this.toMetersPerTick.apply(speed);
        }

        private double convertFromMetersPerTick(double speed) {
            return this.fromMetersPerTick.apply(speed);
        }

        private double convertTo(double speed, @NotNull SpeedUnit unit) {
            return unit.convertFromMetersPerTick(this.convertToMetersPerTick(speed));
        }

        @Override
        public @NotNull String getDisplayString() {
            return this.unit;
        }

    }
}

package cam72cam.immersiverailroading.library;

import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapped;
import cam72cam.mod.serialization.TagMapper;
import cam72cam.mod.text.TextUtil;
import trackapi.lib.Gauges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

@TagMapped(Gauge.GaugeTagMapper.class)
public final class Gauge {

    private static final List<Gauge> gauges = new ArrayList<>();

    public static List<Gauge> getRegisteredGauges() {
        return Collections.unmodifiableList(gauges);
    }

    public static void resetRegisteredGauges() {
        // clearing should be fine, as creating a new list could(?) leak memory
        // and this method is not even used
        gauges.clear();
    }

    public static void registerGauge(double railDistance, String name) {
        removeGauge(railDistance);
        gauges.add(new Gauge(railDistance, name));

        // todo: do we have to sort?
        gauges.sort((first, second) -> Double.compare(second.railDistance, first.railDistance));
    }

    public static void removeGauge(double railDistance) {
        gauges.removeIf(gauge -> gauge.getRailDistance() == railDistance);
    }

    /**
     * The distance between the rails in meters
     */
    private final double railDistance;

    /**
     * The name of the gauge
     */
    private final String name;

    private Gauge(double railDistance, String name) {
        this.railDistance = railDistance;
        this.name = name;
    }

    public double getRailDistance() {
        return this.railDistance;
    }

    /**
     * Returns the closest gauge to the given rail distance
     */
    public static Gauge getClosestGauge(double railDistance) {
        return gauges.stream()
                .filter(gauge -> gauge.getRailDistance() == railDistance)
                .findFirst()
                .orElseGet(() -> {
                    Gauge closest = gauges.get(0);
                    for (Gauge gauge : gauges) {
                        if (Math.abs(gauge.getRailDistance() - railDistance) < Math.abs(closest.getRailDistance() - railDistance)) {
                            closest = gauge;
                        }
                    }
                    return closest;
                });
    }

    public double scale() {
        return this.railDistance / Gauges.STANDARD;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Gauge)) return false;
        Gauge gauge = (Gauge) object;

        return gauge.getRailDistance() == this.railDistance;
    }


    @Override
    public String toString() {
        return TextUtil.translate("immersiverailroading:gauge." + this.name.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether the gauge is a model gauge
     * <p>
     * A model gauge is a gauge that is smaller than 0.3 meters
     *
     * @return true if the gauge is a model gauge
     */
    // todo: again, this should probably be a field of the gauge
    //       and while this value seems reasonable, someone might want to have
    //       a model gauge with a gauge of 0.31 meters and then this would be wrong
    public boolean isModelGauge() {
        return this.railDistance < 0.3d;
    }

    /**
     * Whether a player should sit while inside a rolling stock with this gauge
     * <p>
     * A passenger should sit if the gauge is smaller than 1.5 meters
     *
     * @return true if the gauge is a minecraft gauge
     */
    // todo: this should be a field of the model. having this be dependent
    //       on the gauge is weird since you could have a waggon
    //       on which you stand on
    public boolean shouldSit() {
        return this.railDistance <= Gauges.MINECRAFT;
    }

    // this has to be protected, as this class is referenced in an annotation
    // java 8 moment :sob:
    protected static final class GaugeTagMapper implements TagMapper<Gauge> {

        @Override
        public TagAccessor<Gauge> apply(Class<Gauge> type, String fieldName, TagField tag) {
            BiConsumer<TagCompound, Gauge> serializer = (nbt, gauge) -> nbt.setDouble(fieldName, gauge == null ? null : gauge.getRailDistance());
            Function<TagCompound, Gauge> deserializer = nbt -> nbt.hasKey(fieldName) ? Gauge.getClosestGauge(nbt.getDouble(fieldName)) : null;
            return new TagAccessor<>(serializer, deserializer);
        }
    }
}

package cam72cam.immersiverailroading.items.nbt;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.*;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.serialization.*;
import trackapi.lib.Gauges;

import java.util.function.Consumer;

@TagMapped(RailSettings.Mapper.class)
public class RailSettings {
    public final Gauge gauge;
    public final TrackItems type;
    public final int length;
    public final float degrees;
    public final float curvosity;
    public final TrackPositionType posType;
    public final TrackSmoothing smoothing;
    public final TrackDirection direction;
    public final ItemStack railBed;
    public final ItemStack railBedFill;
    public final boolean isPreview;
    public final boolean isGradeCrossing;
    public final String track;

    public RailSettings(Gauge gauge, String track, TrackItems type, int length, float degrees, float curvosity, TrackPositionType posType, TrackSmoothing smoothing, TrackDirection direction, ItemStack railBed, ItemStack railBedFill, boolean isPreview, boolean isGradeCrossing) {
        this.gauge = gauge;
        this.track = track;
        this.type = type;
        this.length = length;
        this.degrees = degrees;
        this.posType = posType;
        this.smoothing = smoothing;
        this.direction = direction;
        this.railBed = railBed;
        this.railBedFill = railBedFill;
        this.isPreview = isPreview;
        this.isGradeCrossing = isGradeCrossing;
        this.curvosity = curvosity;
    }

    public static RailSettings from(ItemStack stack) {
        try {
            return new Mutable(stack.getTagCompound()).immutable();
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(ItemStack stack) {
        TagCompound data = new TagCompound();
        try {
            TagSerializer.serialize(data, this.mutable());
        } catch (SerializationException e) {
            ImmersiveRailroading.catching(e);
        }
        stack.setTagCompound(data);
    }

    public Mutable mutable() {
        return new Mutable(this);
    }

    public RailSettings with(Consumer<Mutable> mod) {
        Mutable mutable = this.mutable();
        mod.accept(mutable);
        return mutable.immutable();
    }

    private static class DegreesMapper implements TagMapper<Float> {
        @Override
        public TagAccessor<Float> apply(Class<Float> type, String fieldName, TagField tag) {
            return new TagAccessor<Float>(
                    (d, o) -> d.setFloat(fieldName, o),
                    d -> d.hasKey(fieldName) ? d.getFloat(fieldName) :
                            d.hasKey("quarters") ? d.getInteger("quarters") / 4F * 90 : 90
            ) {
                @Override
                public boolean applyIfMissing() {
                    return true;
                }
            };
        }
    }

    private static class SmoothingMapper implements TagMapper<TrackSmoothing> {
        @Override
        public TagAccessor<TrackSmoothing> apply(Class<TrackSmoothing> type, String fieldName, TagField tag) {
            return new TagAccessor<TrackSmoothing>(
                    (d, o) -> d.setEnum(fieldName, o),
                    nbt -> {
                        if (nbt.hasKey(fieldName)) {
                            return nbt.getEnum(fieldName, type);
                        }
                        return nbt.getEnum("type", TrackItems.class) == TrackItems.SLOPE ?
                                TrackSmoothing.NEITHER : TrackSmoothing.BOTH;
                    }
            ) {
                @Override
                public boolean applyIfMissing() {
                    return true;
                }
            };
        }
    }

    public static final class Mutable {

        @TagField(value = "gauge")
        private Gauge gauge;

        @TagField("type")
        private TrackItems type;

        @TagField("length")
        private int length;

        @TagField(value = "degrees", mapper = DegreesMapper.class)
        private float degrees;

        @TagField("curvosity")
        private float curvosity;

        @TagField("pos_type")
        private TrackPositionType posType;

        @TagField(value = "smoothing", mapper = SmoothingMapper.class)
        private TrackSmoothing smoothing;

        @TagField("direction")
        private TrackDirection direction;

        @TagField("bedItem")
        private ItemStack railBed;

        @TagField("bedFill")
        private ItemStack railBedFill;

        @TagField("isPreview")
        private boolean isPreview;

        @TagField("isGradeCrossing")
        private boolean isGradeCrossing;

        @TagField("track")
        private String track;

        private Mutable(RailSettings settings) {
            this.setGauge(settings.gauge);
            this.setTrack(settings.track);
            this.setType(settings.type);
            this.setLength(settings.length);
            this.setDegrees(settings.degrees);
            this.setCurvosity(settings.curvosity);
            this.setPosType(settings.posType);
            this.setSmoothing(settings.smoothing);
            this.setDirection(settings.direction);
            this.setRailBed(settings.railBed);
            this.setRailBedFill(settings.railBedFill);
            this.setPreview(settings.isPreview);
            this.setGradeCrossing(settings.isGradeCrossing);
        }

        private Mutable(TagCompound data) throws SerializationException {
            // Defaults
            this.setGauge(Gauge.getClosestGauge(Gauges.STANDARD));
            this.setType(TrackItems.STRAIGHT);
            this.setTrack("default");
            this.setLength(10);
            this.setDegrees(90);
            this.setPosType(TrackPositionType.FIXED);
            this.setSmoothing(TrackSmoothing.BOTH);
            this.setDirection(TrackDirection.NONE);
            this.setRailBed(ItemStack.EMPTY);
            this.setRailBedFill(ItemStack.EMPTY);
            this.setPreview(false);
            this.setGradeCrossing(false);
            this.setCurvosity(1);

            TagSerializer.deserialize(data, this);
        }

        public RailSettings immutable() {
            return new RailSettings(
                    this.getGauge(),
                    this.getTrack(),
                    this.getType(),
                    this.getLength(),
                    this.getDegrees(),
                    this.getCurvosity(),
                    this.getPosType(),
                    this.getSmoothing(),
                    this.getDirection(),
                    this.getRailBed(),
                    this.getRailBedFill(),
                    this.isPreview(),
                    this.isGradeCrossing()
            );
        }

        public Gauge getGauge() {
            return this.gauge;
        }

        public void setGauge(Gauge gauge) {
            this.gauge = gauge;
        }

        public TrackItems getType() {
            return this.type;
        }

        public void setType(TrackItems type) {
            this.type = type;
        }

        public int getLength() {
            return this.length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public float getDegrees() {
            return this.degrees;
        }

        public void setDegrees(float degrees) {
            this.degrees = degrees;
        }

        public float getCurvosity() {
            return this.curvosity;
        }

        public void setCurvosity(float curvosity) {
            this.curvosity = curvosity;
        }

        public TrackPositionType getPosType() {
            return this.posType;
        }

        public void setPosType(TrackPositionType posType) {
            this.posType = posType;
        }

        public TrackSmoothing getSmoothing() {
            return this.smoothing;
        }

        public void setSmoothing(TrackSmoothing smoothing) {
            this.smoothing = smoothing;
        }

        public TrackDirection getDirection() {
            return this.direction;
        }

        public void setDirection(TrackDirection direction) {
            this.direction = direction;
        }

        public ItemStack getRailBed() {
            return this.railBed;
        }

        public void setRailBed(ItemStack railBed) {
            this.railBed = railBed;
        }

        public ItemStack getRailBedFill() {
            return this.railBedFill;
        }

        public void setRailBedFill(ItemStack railBedFill) {
            this.railBedFill = railBedFill;
        }

        public boolean isPreview() {
            return this.isPreview;
        }

        public void setPreview(boolean preview) {
            this.isPreview = preview;
        }

        public boolean isGradeCrossing() {
            return this.isGradeCrossing;
        }

        public void setGradeCrossing(boolean gradeCrossing) {
            this.isGradeCrossing = gradeCrossing;
        }

        public String getTrack() {
            return this.track;
        }

        public void setTrack(String track) {
            this.track = track;
        }
    }

    // This assumes that a null RailSettings is serialized.
    public static class Mapper implements TagMapper<RailSettings> {
        @Override
        public TagAccessor<RailSettings> apply(Class<RailSettings> type, String fieldName, TagField tag) throws SerializationException {
            return new TagAccessor<>(
                    (nbt, railSettings) -> {
                        TagCompound target = new TagCompound();
                        try {
                            TagSerializer.serialize(target, railSettings.mutable());
                        } catch (SerializationException e) {
                            // This is messy
                            throw new RuntimeException(e);
                        }
                        nbt.set(fieldName, target);
                    },
                    nbt -> {
                        try {
                            return new Mutable(nbt.get(fieldName)).immutable();
                        } catch (SerializationException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        }
    }
}

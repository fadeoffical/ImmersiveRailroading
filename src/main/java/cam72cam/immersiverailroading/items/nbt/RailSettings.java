package cam72cam.immersiverailroading.items.nbt;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.*;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.serialization.*;

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

    public static class Mutable {
        @TagField(value = "gauge")
        public Gauge gauge;
        @TagField("type")
        public TrackItems type;
        @TagField("length")
        public int length;
        @TagField(value = "degrees", mapper = DegreesMapper.class)
        public float degrees;
        @TagField("curvosity")
        public float curvosity;
        @TagField("pos_type")
        public TrackPositionType posType;
        @TagField(value = "smoothing", mapper = SmoothingMapper.class)
        public TrackSmoothing smoothing;
        @TagField("direction")
        public TrackDirection direction;
        @TagField("bedItem")
        public ItemStack railBed;
        @TagField("bedFill")
        public ItemStack railBedFill;
        @TagField("isPreview")
        public boolean isPreview;
        @TagField("isGradeCrossing")
        public boolean isGradeCrossing;
        @TagField("track")
        public String track;

        private Mutable(RailSettings settings) {
            this.gauge = settings.gauge;
            this.track = settings.track;
            this.type = settings.type;
            this.length = settings.length;
            this.degrees = settings.degrees;
            this.curvosity = settings.curvosity;
            this.posType = settings.posType;
            this.smoothing = settings.smoothing;
            this.direction = settings.direction;
            this.railBed = settings.railBed;
            this.railBedFill = settings.railBedFill;
            this.isPreview = settings.isPreview;
            this.isGradeCrossing = settings.isGradeCrossing;
        }

        private Mutable(TagCompound data) throws SerializationException {
            // Defaults
            this.gauge = Gauge.getClosestGauge(Gauge.STANDARD);
            this.type = TrackItems.STRAIGHT;
            this.track = "default";
            this.length = 10;
            this.degrees = 90;
            this.posType = TrackPositionType.FIXED;
            this.smoothing = TrackSmoothing.BOTH;
            this.direction = TrackDirection.NONE;
            this.railBed = ItemStack.EMPTY;
            this.railBedFill = ItemStack.EMPTY;
            this.isPreview = false;
            this.isGradeCrossing = false;
            this.curvosity = 1;

            TagSerializer.deserialize(data, this);
        }

        public RailSettings immutable() {
            return new RailSettings(
                    this.gauge,
                    this.track,
                    this.type,
                    this.length,
                    this.degrees,
                    this.curvosity,
                    this.posType,
                    this.smoothing,
                    this.direction,
                    this.railBed,
                    this.railBedFill,
                    this.isPreview,
                    this.isGradeCrossing
            );
        }
    }

    // This assumes that a null RailSettings is serialized.
    public static class Mapper implements TagMapper<RailSettings> {
        @Override
        public TagAccessor<RailSettings> apply(Class<RailSettings> type, String fieldName, TagField tag) throws SerializationException {
            return new TagAccessor<>(
                    (d, o) -> {
                        TagCompound target = new TagCompound();
                        try {
                            TagSerializer.serialize(target, o.mutable());
                        } catch (SerializationException e) {
                            // This is messy
                            throw new RuntimeException(e);
                        }
                        d.set(fieldName, target);
                    },
                    d -> {
                        try {
                            return new Mutable(d.get(fieldName)).immutable();
                        } catch (SerializationException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        }
    }
}

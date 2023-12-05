package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.Config.ConfigBalance;

// todo: do we really have to have 'liters' as another unit?
//       why not just use buckets and millibuckets?
//       LiquidQuantity.asLiters() is unused
public final class FluidQuantity {

    /**
     * The number of millibuckets in a bucket
     */
    private static final int BUCKET_VOLUME = 1000;

    /**
     * Represents zero millibuckets of fluid
     */
    public static final FluidQuantity ZERO = fromBuckets(0);

    private final int millibuckets;

    private FluidQuantity(int millibuckets) {
        this.millibuckets = millibuckets;
    }

    public static FluidQuantity fromMillibuckets(int mb) {
        return new FluidQuantity(mb);
    }

    public static FluidQuantity fromBuckets(int buckets) {
        return new FluidQuantity(buckets * BUCKET_VOLUME);
    }

    public static FluidQuantity fromLiters(int liters) {
        return new FluidQuantity(liters * ConfigBalance.MB_PER_LITER);
    }

    public int asMillibuckets() {
        return this.millibuckets;
    }

    public int asBuckets() {
        return this.millibuckets / BUCKET_VOLUME;
    }

    public int asLiters() {
        return this.millibuckets / ConfigBalance.MB_PER_LITER;
    }

    public FluidQuantity scale(double scale) {
        // todo: (why) do we have to ceil this?
        return fromMillibuckets((int) Math.ceil(this.millibuckets * scale));
    }

    public FluidQuantity min(FluidQuantity min) {
        return min.asMillibuckets() > this.millibuckets ? min : this;
    }

    public FluidQuantity roundBuckets() {
        // todo: (why) do we have to ceil this?
        return fromBuckets((int) Math.ceil(this.millibuckets / (double) BUCKET_VOLUME));
    }
}

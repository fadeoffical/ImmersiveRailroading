package cam72cam.immersiverailroading.library;

import cam72cam.mod.text.TextUtil;

// todo: rename to smth better like TrackType or smthn
public enum TrackType {
    STRAIGHT("straight", false, false, false, false),
    CROSSING("crossing", false, false, false, false),
    SLOPE("slope", false, false, true, false),
    TURN("turn", true, false, true, true),
    SWITCH("switch", true, true, true, true),
    TURNTABLE("turntable", false, false, false, false),
    CUSTOM("custom", false, true, true, false);

    private final String name;
    private final boolean hasQuarters;
    private final boolean hasCurvosity;
    private final boolean hasSmoothing;
    private final boolean hasDirection;

    TrackType(String name, boolean hasQuarters, boolean hasCurvosity, boolean hasSmoothing, boolean hasDirection) {
        this.name = name;
        this.hasQuarters = hasQuarters;
        this.hasCurvosity = hasCurvosity;
        this.hasSmoothing = hasSmoothing;
        this.hasDirection = hasDirection;
    }

    public boolean hasQuarters() {
        return this.hasQuarters;
    }

    public boolean hasCurvosity() {
        return this.hasCurvosity;
    }

    public boolean hasSmoothing() {
        return this.hasSmoothing;
    }

    public boolean hasDirection() {
        return this.hasDirection;
    }

    @Override
    public String toString() {
        return TextUtil.translate("track.immersiverailroading:class." + this.getName());
    }

    public String getName() {
        return this.name;
    }
}

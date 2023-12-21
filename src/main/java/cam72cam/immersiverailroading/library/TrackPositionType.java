package cam72cam.immersiverailroading.library;

import cam72cam.mod.text.TextUtil;

public enum TrackPositionType {
    FIXED("fixed"),
    PIXELS("pixels"),
    PIXELS_LOCKED("pixels_locked"),
    SMOOTH("smooth"),
    SMOOTH_LOCKED("smooth_locked");

    private final String name;

    TrackPositionType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return TextUtil.translate("track.immersiverailroading:position." + this.getName());
    }
}

package cam72cam.immersiverailroading.library;

import cam72cam.mod.text.TextUtil;

public enum TrackSmoothing {
    BOTH("both"),
    NEAR("near"),
    FAR("far"),
    NEITHER("neither");

    private final String name;

    TrackSmoothing(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return TextUtil.translate("track.immersiverailroading:smoothing." + this.getName());
    }
}

package cam72cam.immersiverailroading.library;

import cam72cam.mod.text.TextUtil;

public enum TrackDirection {
    NONE("none", 0),
    RIGHT("right", 0),
    LEFT("left", 180);

    private final String name;
    private final float yaw;

    TrackDirection(String name, float yaw) {
        this.name = name;
        this.yaw = yaw;
    }

    public float getYaw() {
        return this.yaw;
    }

    @Override
    public String toString() {
        return TextUtil.translate("track.immersiverailroading:direction." + this.getName());
    }

    public String getName() {
        return this.name;
    }

}

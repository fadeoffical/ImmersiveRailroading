package cam72cam.immersiverailroading.library;

public enum AssemblyStep {
    FRAME,
    WHEELS,
    VALVE_GEAR,
    BOILER,
    SHELL,
    REMAINING,
    ;

    public static AssemblyStep[] reverse() {
        // todo: replace with ArrayUtils.reverse?
        AssemblyStep[] value = new AssemblyStep[values().length];
        for (int i = 0; i < values().length; i++) {
            value[i] = values()[values().length - 1 - i];
        }
        return value;
    }
}

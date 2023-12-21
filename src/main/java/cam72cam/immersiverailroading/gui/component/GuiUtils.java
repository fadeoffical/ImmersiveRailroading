package cam72cam.immersiverailroading.gui.component;

public final class GuiUtils {

    private GuiUtils() {}

    public static String fitString(String stringToFit, int length) {
        if (stringToFit.length() < length) return stringToFit;

        int newLength = (length - 3) / 2;
        return stringToFit.substring(0, newLength) + "..." + stringToFit.substring(stringToFit.length() - newLength);
    }
}

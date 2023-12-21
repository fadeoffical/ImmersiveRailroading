package cam72cam.immersiverailroading.gui;

import cam72cam.mod.entity.Player;

import java.util.List;

public final class ClickListHelper {

    public static <E> E next(List<E> values, E value, Player.Hand hand) {
        return values.get((values.indexOf(value) + values.size() + clickIndex(hand)) % values.size());
    }

    @SuppressWarnings("unchecked") // cast will work
    public static <E extends Enum<?>> E next(E value, Player.Hand hand) {
        E[] values = (E[]) value.getClass().getEnumConstants();
        return values[(value.ordinal() + values.length + clickIndex(hand)) % values.length];
    }

    /**
     * This method  is intended to be used to differentiate between left and right clicks.
     * It returns 1 for left clicks and -1 for right clicks.
     *
     * @param hand the hand that was used to click.
     * @return 1 for left clicks and -1 for right clicks.
     */
    // todo: find a better name
    public static int clickIndex(Player.Hand hand) {
        return hand == Player.Hand.PRIMARY ? 1 : -1;
    }

    private ClickListHelper() {}
}

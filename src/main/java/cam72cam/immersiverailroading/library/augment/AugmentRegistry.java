package cam72cam.immersiverailroading.library.augment;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.resource.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class AugmentRegistry {

    private static final Map<Identifier, Class<? extends Augment>> AUGMENTS = new HashMap<>();

    public static void register(@NotNull Identifier id, @NotNull Class<? extends Augment> augment) {
        AUGMENTS.put(id, augment);
    }

    public static @NotNull Map<Identifier, Class<? extends Augment>> getAugments() {
        return Collections.unmodifiableMap(AUGMENTS);
    }

    public static @Nullable Augment getNewAugment(@NotNull Identifier id) {
        Class<? extends Augment> augmentClass = AUGMENTS.entrySet().stream()
                .filter(entry -> entry.getKey().equals(id))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (augmentClass == null) return null;

        try {
            return augmentClass.newInstance();
        } catch (InstantiationException | IllegalAccessException exception) {
            ImmersiveRailroading.catching(exception);
            return null;
        }
    }

    private AugmentRegistry() {}
}

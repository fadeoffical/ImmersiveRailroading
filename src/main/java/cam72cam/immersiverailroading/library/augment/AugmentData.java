package cam72cam.immersiverailroading.library.augment;

import org.jetbrains.annotations.NotNull;

public final class AugmentData<Impl extends Augment> {

    private final @NotNull String id;
    private final @NotNull Class<Impl> implementationClass;

    public AugmentData(@NotNull String id, @NotNull Class<Impl> implementationClass) {
        this.id = id;
        this.implementationClass = implementationClass;
    }


    public @NotNull String getId() {
        return this.id;
    }

    public @NotNull Class<Impl> getImplementationClass() {
        return this.implementationClass;
    }
}

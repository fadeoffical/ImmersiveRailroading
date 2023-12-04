package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;

public class Horn extends PartSound {

    public Horn(ModelComponent component, ModelState state, EntityRollingStockDefinition.SoundDefinition soundFile, boolean repeats) {
        super(soundFile, repeats, 100, ConfigSound.SoundCategories.Locomotive.Diesel::horn);
        state.include(component);
    }

    public static Horn get(ComponentProvider provider, ModelState state, EntityRollingStockDefinition.SoundDefinition soundFile, boolean repeats) {
        return new Horn(provider.parse(ModelComponentType.HORN), state, soundFile, repeats);
    }
}

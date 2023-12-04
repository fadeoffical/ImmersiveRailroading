package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.library.ValveGearConfig;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;

public class Frame {
    public final WheelSet wheels;
    private final ModelComponent frame;
    private final ValveGear valveGearRight;
    private final ValveGear valveGearLeft;

    public Frame(ComponentProvider provider, ModelState state, ModelState rocking, String blame, ValveGearConfig type) {
        this.wheels = WheelSet.get(provider, state, ModelComponentType.FRAME_WHEEL_X, 0);
        this.frame = provider.parse(ModelComponentType.FRAME);
        if (this.frame == null) {
            ImmersiveRailroading.warn("Invalid model: Missing FRAME for %s!  (this will fail in future versions of IR)", blame);
        } else {
            rocking.include(this.frame);
        }
        this.valveGearRight = this.wheels != null ? ValveGear.get(this.wheels, type, provider, state, ModelPosition.RIGHT, -90) : null;
        this.valveGearLeft = this.wheels != null ? ValveGear.get(this.wheels, type, provider, state, ModelPosition.LEFT, 0) : null;
    }
}

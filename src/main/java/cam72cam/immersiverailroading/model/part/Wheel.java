package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.mod.math.Vec3d;
import util.Matrix4;

import java.util.function.Function;

public class Wheel {

    private final ModelComponent wheel;

    protected Wheel(ModelComponent wheel, ModelState state, Function<EntityMovableRollingStock, Float> angle) {
        this.wheel = wheel;

        Vec3d wheelPosition = wheel.center;
        state.push(settings -> settings.animator((ModelState.Animator) stock ->
                new Matrix4()
                        .translate(wheelPosition.x, wheelPosition.y, wheelPosition.z)
                        .rotate(Math.toRadians(angle != null ?
                                        angle.apply(stock) :
                                        this.angle(stock.distanceTraveled)),
                                0, 0, 1)
                        .translate(-wheelPosition.x, -wheelPosition.y, -wheelPosition.z))
        ).include(wheel);
    }

    public float angle(double distance) {
        double circumference = this.getWheelDiameter() * (float) Math.PI;
        double relativeDistance = distance % circumference;
        return (float) (360 * relativeDistance / circumference);
    }

    public double getWheelDiameter() {
        return this.getWheel().height();
    }

    public ModelComponent getWheel() {
        return this.wheel;
    }
}

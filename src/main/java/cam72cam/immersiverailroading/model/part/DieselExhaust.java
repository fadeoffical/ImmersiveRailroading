package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.Particles;
import cam72cam.immersiverailroading.library.unit.Speed;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.render.SmokeParticle;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;

import java.util.List;

public final class DieselExhaust {

    private final List<ModelComponent> components;

    public static DieselExhaust create(ComponentProvider provider) {
        return new DieselExhaust(provider.parseAll(ModelComponentType.DIESEL_EXHAUST_X));
    }

    private DieselExhaust(List<ModelComponent> components) {
        this.components = components;
    }

    public void effects(LocomotiveDiesel stock) {
        if (!ConfigGraphics.particlesEnabled) return;
        if (!stock.isRunning()) return;

        float throttle = Math.abs(stock.getThrottle()) + 0.05f;
        Vec3d fakeMotion = stock.getVelocity();

        for (ModelComponent exhaust : this.components) {
            Vec3d particlePos = stock.getPosition().add(VecUtil.rotateWrongYaw(exhaust.center.scale(stock.getGauge().scale()), stock.getRotationYaw() + 180));
            particlePos = particlePos.subtract(fakeMotion);

            double smokeMod = (1 + Math.min(1, Math.max(0.2, stock.getCurrentSpeed().as(Speed.SpeedUnit.METERS_PER_TICK).absolute().value()) * 2)) / 2;
            Particles.SMOKE.accept(new SmokeParticle.SmokeParticleData(stock.getWorld(), particlePos, new Vec3d(fakeMotion.x, fakeMotion.y + 0.4 * stock.getGauge().scale(), fakeMotion.z), (int) (40 * (1 + throttle) * smokeMod), throttle, throttle, exhaust.width() * stock.getGauge().scale(), stock.getDefinition().smokeParticleTexture));
        }
    }
}

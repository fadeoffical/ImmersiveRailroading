package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.entity.SteamLocomotive;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.Particles;
import cam72cam.immersiverailroading.library.unit.Speed;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.render.SmokeParticle;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.resource.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SteamChimney {

    private final List<ModelComponent> emitter;
    private boolean isSmokeParticle;

    public static SteamChimney get(ComponentProvider provider) {
        List<ModelComponent> chimneyEmitters = provider.parseAll(ModelComponentType.PARTICLE_CHIMNEY_X);
        return chimneyEmitters.isEmpty() ? null : new SteamChimney(chimneyEmitters);
    }

    private SteamChimney(List<ModelComponent> emitter) {
        this.emitter = emitter;
    }

    public void effects(@NotNull SteamLocomotive stock, boolean isEndStroke) {
        if (!ConfigGraphics.particlesEnabled) return;
        if (this.emitter == null) return;

        Vec3d fakeMotion = stock.getVelocity();
        float exhaust = stock.getThrottle() * Math.abs(stock.getReverser());


        float darken = 0;
        for (int i : stock.getBurnTime().values()) darken += i >= 1 ? 1 : 0;

        if (Config.isFuelRequired(stock.getGauge()) && darken == 0) return;

        darken /= stock.getInventorySize() - 2.0;
        darken *= 0.75;

        float thickness = exhaust / 2;
        for (ModelComponent smoke : this.emitter) {
            Vec3d particlePos = stock.getPosition()
                    .add(VecUtil.rotateWrongYaw(smoke.center.scale(stock.getGauge().scale()), stock.getRotationYaw() + 180));

            double smokeMod = Math.min(1, Math.max(0.2, stock.getCurrentSpeed().as(Speed.SpeedUnit.METERS_PER_TICK).absolute().value() * 2));

            int lifespan = (int) (200 * (1 + exhaust) * smokeMod * stock.getGauge().scale());

            double verticalSpeed = 0.5 * stock.getGauge().scale();
            double size = smoke.width() * stock.getGauge().scale() * (0.8 + smokeMod);

            if (isEndStroke) {
                double phaseSpike = 1.75;
                size *= phaseSpike;
                verticalSpeed *= phaseSpike;
            }
            particlePos = particlePos.subtract(fakeMotion);
            this.isSmokeParticle = !this.isSmokeParticle;
            Identifier particleTex = this.isSmokeParticle ? stock.getDefinition().smokeParticleTexture : stock.getDefinition().steamParticleTexture;
            Particles.SMOKE.accept(new SmokeParticle.SmokeParticleData(stock.getWorld(), particlePos, new Vec3d(fakeMotion.x, fakeMotion.y + verticalSpeed, fakeMotion.z), lifespan, darken, thickness, size, particleTex));
        }
    }
}

package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.Particles;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.render.ExpireableMap;
import cam72cam.immersiverailroading.render.SmokeParticle;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.sound.ISound;

import java.util.List;
import java.util.UUID;

public class PressureValve {
    private final List<ModelComponent> valves;
    private final Identifier sndFile;
    private final ExpireableMap<UUID, ISound> sounds = new ExpireableMap<UUID, ISound>() {
        @Override
        public void onRemove(UUID key, ISound value) {
            value.stop();
        }
    };

    public PressureValve(List<ModelComponent> valves, Identifier sndFile) {
        this.valves = valves;
        this.sndFile = sndFile;
    }

    public static PressureValve get(ComponentProvider provider, Identifier sndFile) {
        List<ModelComponent> valves = provider.parseAll(ModelComponentType.PRESSURE_VALVE_X);
        return new PressureValve(valves, sndFile); // allow empty for sound only
    }

    public void effects(EntityMovableRollingStock stock, boolean isBlowingOff) {
        ISound sound = this.sounds.get(stock.getUUID());
        if (sound == null) {
            sound = stock.createSound(this.sndFile, true, 40, ConfigSound.SoundCategories.Locomotive.Steam::pressureValve);
            sound.setVolume(0.3f);
            this.sounds.put(stock.getUUID(), sound);
        }

        if (isBlowingOff) {
            if (!sound.isPlaying()) {
                sound.play(stock.getPosition());
            }

            sound.setPosition(stock.getPosition());
            sound.setVelocity(stock.getVelocity());
        } else {
            sound.stop();
        }

        if (ConfigGraphics.particlesEnabled && isBlowingOff) {
            Vec3d fakeMotion = stock.getVelocity();
            for (ModelComponent valve : this.valves) {
                Vec3d particlePos = stock.getPosition()
                        .add(VecUtil.rotateWrongYaw(valve.center.scale(stock.getGauge().scale()), stock.getRotationYaw() + 180));
                particlePos = particlePos.subtract(fakeMotion);
                Particles.SMOKE.accept(new SmokeParticle.SmokeParticleData(stock.getWorld(), particlePos, new Vec3d(fakeMotion.x, fakeMotion.y + 0.2 * stock.getGauge()
                        .scale(), fakeMotion.z), 40, 0, 0.2f, valve.width() * stock.getGauge().scale(), stock.getDefinition().steamParticleTexture));
            }
        }
    }

    public void removed(EntityMovableRollingStock stock) {
        ISound sound = this.sounds.get(stock.getUUID());
        if (sound != null) {
            sound.stop();
        }
    }
}

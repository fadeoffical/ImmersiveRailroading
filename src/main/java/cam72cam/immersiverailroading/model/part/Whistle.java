package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.Particles;
import cam72cam.immersiverailroading.library.unit.Speed;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.Quilling;
import cam72cam.immersiverailroading.render.ExpireableMap;
import cam72cam.immersiverailroading.render.SmokeParticle;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.sound.ISound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Whistle {

    private final ModelComponent component;
    private final Quilling quilling;
    private final PartSound whistle;
    private final ExpireableMap<UUID, SoundEffects> sounds = new ExpireableMap<UUID, SoundEffects>() {
        @Override
        public void onRemove(UUID key, SoundEffects value) {
            value.terminate();
        }
    };

    public static Whistle get(ComponentProvider provider, ModelState state, Quilling quilling, EntityRollingStockDefinition.SoundDefinition fallback) {
        return new Whistle(provider.parse(ModelComponentType.WHISTLE), state, quilling, fallback);
    }

    private Whistle(ModelComponent whistle, ModelState state, Quilling quilling, EntityRollingStockDefinition.SoundDefinition fallback) {
        this.component = whistle;
        this.quilling = quilling;
        this.whistle = new PartSound(fallback, true, 150, ConfigSound.SoundCategories.Locomotive.Steam::whistle);
        state.include(whistle);
    }

    public void effects(EntityMovableRollingStock stock, int hornTime, float hornPull) {
        // Particles and Sound

        if (this.quilling != null) {
            SoundEffects sound = this.sounds.get(stock.getUUID());

            if (sound == null) {
                sound = new SoundEffects(stock);
                this.sounds.put(stock.getUUID(), sound);
            }

            sound.update(stock, hornTime, hornPull);
        } else {
            this.whistle.effects(stock, hornTime > 0);
        }

        Vec3d fakeMotion = stock.getVelocity();
        if (this.component != null && hornTime > 0) {
            Vec3d particlePos = stock.getPosition()
                    .add(VecUtil.rotateWrongYaw(this.component.center.scale(stock.getGauge().scale()), stock.getRotationYaw() + 180));
            particlePos = particlePos.subtract(fakeMotion);

            float darken = 0;
            float thickness = 1;
            double smokeMod = Math.min(1, Math.max(0.2, stock.getCurrentSpeed().as(Speed.SpeedUnit.METERS_PER_TICK).value() * 2));
            int lifespan = (int) (40 * (1 + smokeMod * stock.getGauge().scale()));
            double verticalSpeed = 0.8f * stock.getGauge().scale();
            double size = 0.3 * (0.8 + smokeMod) * stock.getGauge().scale();

            Particles.SMOKE.accept(new SmokeParticle.SmokeParticleData(stock.getWorld(), particlePos, new Vec3d(fakeMotion.x, fakeMotion.y + verticalSpeed, fakeMotion.z), lifespan, darken, thickness, size, stock.getDefinition().steamParticleTexture));
        }
    }

    public void removed(EntityMovableRollingStock stock) {
        SoundEffects sound = this.sounds.get(stock.getUUID());
        if (sound != null) sound.terminate();
        this.whistle.removed(stock);
    }

    private final class SoundEffects {
        private final List<ISound> chimes;
        private float pullString = 0;
        private float soundDampener = 0;

        private SoundEffects(EntityMovableRollingStock stock) {
            this.chimes = new ArrayList<>();
            for (Quilling.Chime chime : Whistle.this.quilling.chimes) {
                this.chimes.add(stock.createSound(chime.sample, true, 150, ConfigSound.SoundCategories.Locomotive.Steam::whistle));
            }
        }

        public void update(EntityMovableRollingStock stock, int hornTime, float hornPull) {
            if (hornTime < 1) {
                this.pullString = 0;
                this.soundDampener = 0;
                for (ISound chime : this.chimes) {
                    if (chime.isPlaying()) {
                        chime.stop();
                    }
                }
            } else {
                float maxDelta = 1 / 20f;
                float delta;
                if (hornTime > 5) {
                    if (this.soundDampener < 0.4) {
                        this.soundDampener = 0.4f;
                    }
                    if (this.soundDampener < 1) {
                        this.soundDampener += 0.1;
                    }
                    delta = hornPull - this.pullString;
                } else {
                    if (this.soundDampener > 0) {
                        this.soundDampener -= 0.07;
                    }
                    // Player probably released key or has net lag
                    delta = -this.pullString;
                }

                if (this.pullString == 0) {
                    this.pullString += delta * 0.55;
                } else {
                    this.pullString += Math.max(Math.min(delta, maxDelta), -maxDelta);
                }
                this.pullString = Math.min(this.pullString, (float) Whistle.this.quilling.maxPull);

                for (int i = 0; i < Whistle.this.quilling.chimes.size(); i++) {
                    ISound sound = this.chimes.get(i);
                    Quilling.Chime chime = Whistle.this.quilling.chimes.get(i);

                    double perc = this.pullString;
                    // Clamp to start/end
                    perc = Math.min(perc, chime.pull_end);
                    perc -= chime.pull_start;

                    //Scale to clamped range
                    perc /= chime.pull_end - chime.pull_start;

                    if (perc > 0) {
                        double pitch = (chime.pitch_end - chime.pitch_start) * perc + chime.pitch_start;

                        sound.setPitch((float) pitch);
                        sound.setVolume((float) (perc * this.soundDampener));
                        sound.setPosition(stock.getPosition());
                        sound.setVelocity(stock.getVelocity());

                        if (!sound.isPlaying()) sound.play(stock.getPosition());
                    } else {
                        if (sound.isPlaying()) sound.stop();
                    }
                }
            }
        }

        public void terminate() {
            if (this.chimes != null) this.chimes.forEach(ISound::stop);
        }
    }
}

package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.SteamLocomotive;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.library.Particles;
import cam72cam.immersiverailroading.library.ValveGearConfig;
import cam72cam.immersiverailroading.library.unit.Speed;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.render.ExpireableMap;
import cam72cam.immersiverailroading.render.SmokeParticle;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.sound.ISound;
import org.apache.commons.lang3.tuple.Pair;
import util.Matrix4;

import java.util.ArrayList;
import java.util.List;

public abstract class ValveGear {

    private static final ExpireableMap<String, ChuffSound> CHUFF_SOUNDS = new ExpireableMap<String, ChuffSound>() {
        @Override
        public void onRemove(String key, ChuffSound value) {
            value.free();
        }
    };

    protected final WheelSet wheels;
    private final ModelState state;
    protected float angleOffset;
    protected Exhaust frontExhaust;
    protected Exhaust rearExhaust;

    protected ValveGear(WheelSet wheels, ModelState state, float angleOffset) {
        this.wheels = wheels;
        this.state = state;
        this.angleOffset = angleOffset;
    }

    static ValveGear get(WheelSet wheels, ValveGearConfig config, ComponentProvider provider, ModelState state, ModelPosition pos, float angleOffset) {
        if (config == null) {
            return null;
        }
        switch (config.type) {
            case WALSCHAERTS:
                return WalschaertsValveGear.get(wheels, provider, state, pos, angleOffset);
            case STEPHENSON:
                return StephensonValveGear.get(wheels, provider, state, pos, angleOffset);
            case CONNECTING:
                return ConnectingRodValveGear.get(wheels, provider, state, pos, angleOffset);
            case CUSTOM:
                return CustomValveGear.get(config, wheels, provider, state, pos);
            case SHAY:
            case CLIMAX:
            case HIDDEN:
            default:
                return null;
        }
    }

    private static Vec3d findDirection(String name) {
        Vec3d result = Vec3d.ZERO;
        for (Direction value : Direction.values()) {
            if (name.contains("__" + value.name())) {
                result = result.add(value.vec);
            }
        }
        return result;
    }

    void effects(EntityMovableRollingStock stock) {
        if (this.frontExhaust != null) {
            this.frontExhaust.effects(stock);
        }
        if (this.rearExhaust != null) {
            this.rearExhaust.effects(stock);
        }
    }

    public boolean isEndStroke(EntityMovableRollingStock stock) {
        return (this.frontExhaust != null && this.frontExhaust.isEndStroke(stock)) || (this.rearExhaust != null && this.rearExhaust.isEndStroke(stock));
    }

    float angle(double distance) {
        return this.wheels.angle(distance) + this.angleOffset;
    }

    public void removed(EntityMovableRollingStock stock) {
        if (this.frontExhaust != null) {
            this.frontExhaust.removed(stock);
        }
        if (this.rearExhaust != null) {
            this.rearExhaust.removed(stock);
        }
    }

    private enum Direction {
        FRONT(new Vec3d(-1, 0, 0)), BACK(new Vec3d(1, 0, 0)), UP(new Vec3d(0, 1, 0)), DOWN(new Vec3d(0, -1, 0)), LEFT(new Vec3d(0, 0, 1)), RIGHT(new Vec3d(0, 0, -1));

        private final Vec3d vec;

        Direction(Vec3d vec) {
            this.vec = vec;
        }
    }

    private static class ChuffSound {
        private final SteamLocomotive stock;
        private final float pitchOffset;
        private final List<ISound> chuffs;
        private final ISound cylinder_drain;
        private boolean pitchStroke;
        private boolean chuffOn;
        private int chuffId;
        private float drain_volume = 0;

        public ChuffSound(SteamLocomotive stock) {
            this.chuffOn = false;
            this.chuffId = 0;
            this.chuffs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                this.chuffs.add(stock.createSound(stock.getDefinition().chuff, false, 40, ConfigSound.SoundCategories.Locomotive.Steam::chuff));
            }
            this.cylinder_drain = stock.createSound(stock.getDefinition().cylinder_drain, true, 40, ConfigSound.SoundCategories.Locomotive.Steam::cylinder_drain);
            this.stock = stock;
            this.pitchOffset = (float) (Math.random() / 50);
            this.pitchStroke = false;
        }

        public void update(Vec3d particlePos, boolean enteredStroke, boolean drain_enabled) {
            if (!this.chuffOn) {
                if (enteredStroke && Math.abs(this.stock.getThrottle() * this.stock.getReverser()) > 0) {
                    this.chuffOn = true;
                    this.pitchStroke = !this.pitchStroke;

                    double speed = this.stock.getCurrentSpeed()
                            .as(Speed.SpeedUnit.METERS_PER_TICK)
                            .absolute()
                            .value();

                    double maxSpeed = this.stock.getDefinition()
                            .getMaxSpeed(this.stock.getGauge())
                            .as(Speed.SpeedUnit.METERS_PER_TICK)
                            .absolute()
                            .value();

                    float volume = (float) Math.max(1 - speed / maxSpeed, 0.3) * Math.abs(this.stock.getThrottle() * this.stock.getReverser());
                    volume = (float) Math.sqrt(volume);
                    double fraction = 3;
                    float pitch = 0.8f + (float) (speed / maxSpeed / fraction * 0.2);
                    float delta = this.pitchOffset - (this.pitchStroke ? -0.02f : 0);
                    ISound chuff = this.chuffs.get(this.chuffId);

                    chuff.setPitch(pitch + delta);
                    chuff.setVolume(volume + delta);
                    chuff.play(particlePos);

                    this.chuffId = (this.chuffId + 1) % this.chuffs.size();
                }
            } else {
                if (!enteredStroke) {
                    // Reset for next stroke
                    this.chuffOn = false;
                }
            }

            this.chuffs.stream().filter(ISound::isPlaying).forEach(chuff -> {
                chuff.setPosition(particlePos);
                chuff.setVelocity(this.stock.getVelocity());
            });

            if (drain_enabled) {
                this.drain_volume += 0.5f;
                this.drain_volume = Math.min(1, this.drain_volume);
            }
            if (!drain_enabled && this.drain_volume > 0) {
                this.drain_volume -= 0.2f;
            }

            if (this.drain_volume > 0 && !this.cylinder_drain.isPlaying()) {
                this.cylinder_drain.setPitch(1 - this.pitchOffset * 5);
                this.cylinder_drain.setVolume(this.drain_volume * this.stock.getThrottle());
                this.cylinder_drain.play(particlePos);
            }
            if (this.drain_volume <= 0 && this.cylinder_drain.isPlaying()) {
                this.cylinder_drain.stop();
            }
            if (this.cylinder_drain.isPlaying()) {
                this.cylinder_drain.setVolume(this.drain_volume * this.stock.getThrottle());
                this.cylinder_drain.setPosition(particlePos);
                this.cylinder_drain.setVelocity(this.stock.getVelocity());
            }
        }

        void free() {
            this.chuffs.forEach(ISound::stop);
            this.cylinder_drain.stop();
        }
    }

    public class Exhaust {
        public final Vec3d position;
        public final Vec3d direction;
        public final float angle;

        public Exhaust(Vec3d position, ModelPosition direction, float angle) {
            this(position, new Vec3d(0, 0, direction.contains(ModelPosition.RIGHT) ? -1 : direction.contains(ModelPosition.CENTER) ? 0 : 1), angle);
        }

        public Exhaust(Vec3d position, Vec3d direction, float angle) {
            this.position = position;
            this.direction = direction;
            this.angle = angle;
        }


        public Exhaust(ModelComponent component, float angle) {
            this(component.center, findDirection(component.key), angle);
        }

        public void effects(EntityMovableRollingStock stock) {
            boolean drains_enabled = this.isEndStroke(stock) && stock instanceof SteamLocomotive && ((SteamLocomotive) stock).cylinderDrainsEnabled();

            if (stock instanceof Locomotive && (((SteamLocomotive) stock).getBoilerPressure() <= 0 && Config.ConfigBalance.FuelRequired)) {
                return;
            }

            Pair<Matrix4, Vec3d> particlePos = null; //Lazy eval
            if (ConfigGraphics.particlesEnabled && drains_enabled) {
                particlePos = this.particlePos(stock);
                double accell = 0.3 * stock.getGauge().scale();
                Vec3d sideMotion = stock.getVelocity()
                        .add(VecUtil.rotateWrongYaw(particlePos.getLeft()
                                .apply(this.direction)
                                .scale(accell), stock.getRotationYaw() + 180));
                Particles.SMOKE.accept(new SmokeParticle.SmokeParticleData(stock.getWorld(), particlePos.getRight(), new Vec3d(sideMotion.x, sideMotion.y + 0.01 * stock.getGauge()
                        .scale(), sideMotion.z), 80, 0, 0.6f, 0.2 * stock.getGauge().scale(), stock.getDefinition().steamParticleTexture));
            }

            if (stock instanceof SteamLocomotive) {
                if (particlePos == null) {
                    particlePos = this.particlePos(stock);
                }
                String key = this.generateKey(stock);
                ChuffSound sound = CHUFF_SOUNDS.get(key);
                if (sound == null) {
                    sound = new ChuffSound((SteamLocomotive) stock);
                    CHUFF_SOUNDS.put(key, sound);
                }
                sound.update(particlePos.getRight(), this.isEndStroke(stock, 0.125f), drains_enabled);
            }
        }

        public boolean isEndStroke(EntityMovableRollingStock stock) {
            float delta = 0.03f;
            if (stock instanceof SteamLocomotive) {
                SteamLocomotive loco = (SteamLocomotive) stock;
                if (Math.abs(loco.getThrottle() * loco.getReverser()) == 0) {
                    return false;
                }

                delta = Math.abs(loco.getReverser()) / 4;
            }
            return this.isEndStroke(stock, delta);
        }

        private Pair<Matrix4, Vec3d> particlePos(EntityMovableRollingStock stock) {
            Matrix4 m = ValveGear.this.state.getMatrix(stock);
            if (m == null) {
                // Just in case...
                m = new Matrix4();
            }
            return Pair.of(m, stock.getPosition()
                    .add(VecUtil.rotateWrongYaw(m.apply(this.position)
                            .scale(stock.getGauge().scale()), stock.getRotationYaw() + 180)));

        }

        public boolean isEndStroke(EntityMovableRollingStock stock, float delta) {
            double percent = ValveGear.this.angle(stock.distanceTraveled / stock.getGauge().scale()) / 360;
            double pistonPos = this.angle / 360;

            // There's probably a much better way of doing this...
            double difference = percent - pistonPos;
            return Math.abs(difference) < delta || Math.abs(difference - 1) < delta || Math.abs(difference + 1) < delta;
        }

        public void removed(EntityMovableRollingStock stock) {
            String key = this.generateKey(stock);
            CHUFF_SOUNDS.remove(key);
        }

        // todo: make static on java 16+
        private /* static */ String generateKey(EntityMovableRollingStock stock) {
            return String.format("%s-%s", stock.getUUID(), this.hashCode());
        }
    }
}

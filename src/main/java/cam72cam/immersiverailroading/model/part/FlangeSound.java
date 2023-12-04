package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.sound.ISound;
import cam72cam.mod.util.DegreeFuncs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlangeSound {
    private final Identifier def;
    private final boolean canLoop;
    private final float attenuationDistance;
    private final Map<UUID, Sound> sounds = new HashMap<>();

    public FlangeSound(Identifier def, boolean canLoop, float attenuationDistance) {
        this.def = def;
        this.canLoop = canLoop;
        this.attenuationDistance = attenuationDistance;
    }

    public void effects(EntityMoveableRollingStock stock) {
        this.sounds.computeIfAbsent(stock.getUUID(), uuid -> new Sound(stock)).effects();
    }

    public void removed(EntityMoveableRollingStock stock) {
        Sound sound = this.sounds.remove(stock.getUUID());
        if (sound != null) {
            sound.removed();
        }
    }

    private class Sound {
        private final EntityMoveableRollingStock stock;
        private final ISound sound;
        private final float sndRand;
        private float lastFlangeVolume;

        Sound(EntityMoveableRollingStock stock) {
            this.lastFlangeVolume = 0;
            this.sound = stock.createSound(FlangeSound.this.def, FlangeSound.this.canLoop, FlangeSound.this.attenuationDistance, ConfigSound.SoundCategories.RollingStock::flange);
            this.stock = stock;
            this.sndRand = (float) Math.random() / 10;
        }

        void effects() {
            double yawDelta = DegreeFuncs.delta(this.stock.getFrontYaw(), this.stock.getRearYaw()) /
                    Math.abs(this.stock.getDefinition().getBogeyFront(this.stock.gauge) - this.stock.getDefinition()
                            .getBogeyRear(this.stock.gauge));
            double startingFlangeSpeed = 5;
            double kmh = Math.abs(this.stock.getCurrentSpeed().metric());
            double flangeMinYaw = this.stock.getDefinition().flange_min_yaw;
            // https://en.wikipedia.org/wiki/Minimum_railway_curve_radius#Speed_and_cant implies squared speed
            flangeMinYaw = flangeMinYaw / Math.sqrt(kmh) * Math.sqrt(startingFlangeSpeed);
            if (yawDelta > flangeMinYaw && kmh > 5) {
                if (!this.sound.isPlaying()) {
                    this.lastFlangeVolume = 0.1f;
                    this.sound.setVolume(this.lastFlangeVolume);
                    this.sound.play(this.stock.getPosition());
                }
                this.sound.setPitch(0.9f + Math.abs((float) this.stock.getCurrentSpeed().metric()) / 600 + this.sndRand);
                float oscillation = (float) Math.sin((this.stock.getTickCount() / 40f * this.sndRand * 40));
                double flangeFactor = (yawDelta - flangeMinYaw) / (90 - flangeMinYaw);
                float desiredVolume = (float) flangeFactor / 2 * oscillation / 4 + 0.25f;
                this.lastFlangeVolume = (this.lastFlangeVolume * 4 + desiredVolume) / 5;
                this.sound.setVolume(this.lastFlangeVolume);
                this.sound.setPosition(this.stock.getPosition());
                this.sound.setVelocity(this.stock.getVelocity());
            } else {
                if (this.sound.isPlaying()) {
                    if (this.lastFlangeVolume > 0.1) {
                        this.lastFlangeVolume = (this.lastFlangeVolume * 4 + 0) / 5;
                        this.sound.setVolume(this.lastFlangeVolume);
                        this.sound.setPosition(this.stock.getPosition());
                        this.sound.setVelocity(this.stock.getVelocity());
                    } else {
                        this.sound.stop();
                    }
                }
            }
        }

        public void removed() {
            this.sound.stop();
        }
    }
}

package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition.SoundDefinition;
import cam72cam.immersiverailroading.render.ExpireableMap;
import cam72cam.mod.sound.ISound;

import java.util.UUID;
import java.util.function.Supplier;

public class PartSound {
    private final SoundDefinition def;
    private final boolean canLoop;
    private final float attenuationDistance;
    private final Supplier<Float> category;
    private final ExpireableMap<UUID, Sounds> entitySounds = new ExpireableMap<UUID, Sounds>() {
        @Override
        public void onRemove(UUID key, Sounds value) {
            value.terminate();
        }
    };

    public PartSound(SoundDefinition def, boolean canLoop, float attenuationDistance, Supplier<Float> category) {
        this.def = def;
        this.canLoop = canLoop;
        this.attenuationDistance = attenuationDistance;
        this.category = category;
    }

    public void effects(EntityMoveableRollingStock stock, boolean enabled) {
        this.effects(stock, enabled ? 1 : 0, 1);
    }

    public void effects(EntityMoveableRollingStock stock, float volume, float pitch) {
        if (this.def == null) {
            return;
        }

        Sounds sounds = this.entitySounds.get(stock.getUUID());
        if (sounds == null) {
            sounds = new Sounds(stock);
            this.entitySounds.put(stock.getUUID(), sounds);
        }

        ISound toUpdate = null;
        if (volume > 0) {
            // Playing
            switch (sounds.state) {
                case STOPPING:
                    if (sounds.stop != null) {
                        sounds.stop.stop();
                    }
                case STOPPED:
                    // Start from the beginning
                    sounds.state = SoundState.STARTING;
                    if (sounds.start != null) {
                        toUpdate = sounds.start;
                        toUpdate.play(stock.getPosition());
                        break;
                    }
                case STARTING:
                    // Keep starting until start has finished
                    if (sounds.start != null && sounds.start.isPlaying()) {
                        toUpdate = sounds.start;
                        break;
                    }
                    // Start has finished
                    sounds.state = SoundState.PLAYING;
                    if (sounds.main != null) {
                        toUpdate = sounds.main;
                        toUpdate.play(stock.getPosition());
                        break;
                    }
                case PLAYING:
                    // Keep looping until loop is stopped
                    if (sounds.main != null) {
                        if (this.canLoop && !sounds.main.isPlaying()) {
                            // It can go out of attenuation distance and may need to be restarted
                            sounds.main.play(stock.getPosition());
                        }
                        if (sounds.main.isPlaying()) {
                            toUpdate = sounds.main;
                        }
                        break;
                    }
                    // Loop Finished, wait for shutoff to play outro
            }

            // Update all sounds to current volume
            // Does not actually change until update is called below on the sound that is playing
            float currentVolume = volume * this.def.volume;
            if (sounds.start != null) {
                sounds.start.setVolume(currentVolume);
                sounds.start.setPitch(pitch);
            }
            if (sounds.main != null) {
                sounds.main.setVolume(currentVolume);
                sounds.main.setPitch(pitch);
            }
            if (sounds.stop != null) {
                sounds.stop.setVolume(currentVolume);
                sounds.stop.setPitch(pitch);
            }
        } else {
            // Stopping
            switch (sounds.state) {
                case STARTING:
                case PLAYING:
                    if (sounds.start != null) {
                        sounds.start.stop();
                    }
                    if (sounds.main != null) {
                        sounds.main.stop();
                    }
                    // Play the outro
                    sounds.state = SoundState.STOPPING;
                    if (sounds.stop != null) {
                        toUpdate = sounds.stop;
                        toUpdate.play(stock.getPosition());
                        break;
                    }
                case STOPPING:
                    if (sounds.stop != null && sounds.stop.isPlaying()) {
                        toUpdate = sounds.stop;
                        break;
                    }
                    sounds.state = SoundState.STOPPED;
                case STOPPED:
                    // Nothing to do here
                    break;
            }
        }

        if (toUpdate != null) {
            toUpdate.setPosition(stock.getPosition());
            toUpdate.setVelocity(stock.getVelocity());
        }
    }

    public void effects(EntityMoveableRollingStock stock, float volume) {
        this.effects(stock, volume, 1);
    }

    public void removed(EntityMoveableRollingStock stock) {
        this.entitySounds.remove(stock.getUUID());
    }

    private enum SoundState {
        STARTING,
        PLAYING,
        STOPPING,
        STOPPED,
    }

    private class Sounds {
        final ISound start;
        final ISound main;
        final ISound stop;
        SoundState state;

        public Sounds(EntityMoveableRollingStock stock) {
            this.state = SoundState.STOPPED;
            float distance = PartSound.this.def.distance != null ? PartSound.this.def.distance : PartSound.this.attenuationDistance;
            this.start = PartSound.this.def.start != null ? stock.createSound(PartSound.this.def.start, false, distance, PartSound.this.category) : null;
            this.main = PartSound.this.def.main != null ? stock.createSound(PartSound.this.def.main, PartSound.this.canLoop && PartSound.this.def.looping, distance, PartSound.this.category) : null;
            this.stop = PartSound.this.def.stop != null ? stock.createSound(PartSound.this.def.stop, false, distance, PartSound.this.category) : null;
        }

        public void terminate() {
            if (this.start != null) {
                this.start.stop();
            }
            if (this.main != null) {
                this.main.stop();
            }
            if (this.stop != null) {
                this.stop.stop();
            }
        }
    }

}

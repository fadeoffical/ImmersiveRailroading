package cam72cam.immersiverailroading.model.animation;

import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.model.part.PartSound;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition.AnimationDefinition;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition.AnimationDefinition.AnimationMode;
import util.Matrix4;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StockAnimation {
    private final AnimationDefinition def;
    private final Animatrix animatrix;

    private final Map<UUID, Integer> tickStart;
    private final Map<UUID, Integer> tickStop;
    private final boolean looping;
    private final PartSound sound;

    public StockAnimation(AnimationDefinition def, double internal_model_scale) throws IOException {
        this.def = def;
        this.animatrix = new Animatrix(def.animatrix.getResourceStream(), internal_model_scale);
        this.tickStart = new HashMap<>();
        this.tickStop = new HashMap<>();
        switch (def.mode) {
            case VALUE:
            case PLAY_FORWARD:
            case PLAY_REVERSE:
            case PLAY_BOTH:
                this.looping = false;
                break;
            case LOOP:
            case LOOP_SPEED:
            default:
                this.looping = true;
        }
        this.sound = def.sound != null ? new PartSound(def.sound, true, 20, ConfigSound.SoundCategories::animations) : null;
    }

    public Matrix4 getMatrix(EntityRollingStock stock, String group) {
        return this.animatrix.groups().contains(group) ? this.animatrix.getMatrix(group, this.getPercent(stock), this.looping) : null;
    }

    public float getPercent(EntityRollingStock stock) {
        float value = this.getValue(stock);

        float total_ticks_per_loop = this.animatrix.frameCount() / this.def.frames_per_tick;
        if (this.def.mode == AnimationMode.LOOP_SPEED) {
            total_ticks_per_loop /= value;
        }

        switch (this.def.mode) {
            case VALUE:
                return value;
            case PLAY_FORWARD:
            case PLAY_REVERSE:
            case PLAY_BOTH:
                UUID key = stock.getUUID();
                float tickDelta;
                if (value >= 0.95) {
                    // FORWARD
                    if (!this.tickStart.containsKey(key)) {
                        this.tickStart.put(key, stock.getTickCount());
                        this.tickStop.remove(key);
                    }
                    if (this.def.mode == AnimationMode.PLAY_REVERSE) {
                        return 1;
                    }
                    // 0 -> 1+
                    tickDelta = stock.getTickCount() - this.tickStart.get(key);
                } else {
                    // REVERSE
                    if (!this.tickStop.containsKey(key)) {
                        this.tickStop.put(key, stock.getTickCount());
                        this.tickStart.remove(key);
                    }
                    if (this.def.mode == AnimationMode.PLAY_FORWARD) {
                        return 0;
                    }
                    // 0 -> 1+
                    tickDelta = stock.getTickCount() - this.tickStop.get(key);
                    if (this.def.mode == AnimationMode.PLAY_BOTH) {
                        // 1 -> 0-
                        tickDelta = total_ticks_per_loop - tickDelta;
                    }
                }
                // Clipped in getMatrix
                return tickDelta / total_ticks_per_loop;
            case LOOP:
                if (value < 0.95) {
                    return 0;
                }
                break;
            case LOOP_SPEED:
                if (value == 0) {
                    return 0;
                }
                break;
        }

        return (stock.getTickCount() % total_ticks_per_loop) / total_ticks_per_loop;
    }

    public float getValue(EntityRollingStock stock) {
        float value = this.def.control_group != null ? stock.getControlPosition(this.def.control_group) : this.def.readout.getValue(stock);
        value += this.def.offset;
        if (this.def.invert) {
            value = 1 - value;
        }
        return value;
    }

    public <ENTITY extends EntityMoveableRollingStock> void effects(ENTITY stock) {
        if (this.sound != null) {
            float volume = 0;
            float pitch = 1;
            switch (this.def.mode) {
                case VALUE:
                    volume = this.getValue(stock);
                    break;
                case PLAY_FORWARD:
                case PLAY_REVERSE:
                case PLAY_BOTH:
                    volume = this.getPercent(stock) > 0 && this.getPercent(stock) < 1 ? 1 : 0;
                    break;
                case LOOP:
                    volume = this.getValue(stock) > 0.95 ? 1 : 0;
                    break;
                case LOOP_SPEED:
                    volume = this.getValue(stock) > 0 ? 1 : 0;
                    pitch = this.getValue(stock);
                    break;
            }
            this.sound.effects(stock, volume, pitch);
        }
    }

    public <ENTITY extends EntityMoveableRollingStock> void removed(ENTITY stock) {
        if (this.sound != null) {
            this.sound.removed(stock);
        }
    }
}

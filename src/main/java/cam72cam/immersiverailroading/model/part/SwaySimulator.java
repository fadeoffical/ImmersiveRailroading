package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.sound.ISound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SwaySimulator {
    private final Map<UUID, Effect> effects = new HashMap<>();

    public SwaySimulator() {

    }

    public double getRollDegrees(EntityMoveableRollingStock stock) {
        return this.effects.computeIfAbsent(stock.getUUID(), uuid -> new Effect(stock)).getRollDegrees();
    }

    public void effects(EntityMoveableRollingStock stock) {
        this.effects.computeIfAbsent(stock.getUUID(), uuid -> new Effect(stock)).effects();
    }

    public void removed(EntityMoveableRollingStock stock) {
        Effect effect = this.effects.remove(stock.getUUID());
        if (effect != null) {
            effect.removed();
        }
    }

    private static class Effect {
        private final EntityMoveableRollingStock stock;
        private final ISound clackFront;
        private final ISound clackRear;
        private double swayMagnitude;
        private double swayImpulse;
        private Vec3i clackFrontPos;
        private Vec3i clackRearPos;

        Effect(EntityMoveableRollingStock stock) {
            this.stock = stock;
            this.clackFront = stock.getWorld().isServer ? null : stock.createSound(stock.getDefinition().clackFront, false, 30, ConfigSound.SoundCategories.RollingStock::clack);
            this.clackRear = stock.getWorld().isServer ? null : stock.createSound(stock.getDefinition().clackRear, false, 30, ConfigSound.SoundCategories.RollingStock::clack);
            this.swayImpulse = 0;
            this.swayMagnitude = 0;
        }

        public void effects() {

            float adjust = (float) Math.abs(this.stock.getCurrentSpeed().metric()) / 300;
            float pitch = adjust + 0.7f;
            if (this.stock.getDefinition().shouldScalePitch()) {
                // TODO this is probably wrong...
                pitch /= this.stock.gauge.scale();
            }
            float volume = 0.01f + adjust;

            volume = Math.min(1, volume * 2);

            Vec3i posFront = new Vec3i(VecUtil.fromWrongYawPitch(this.stock.getDefinition()
                            .getBogeyFront(this.stock.gauge), this.stock.getRotationYaw(), this.stock.getRotationPitch())
                    .add(this.stock.getPosition()));
            if (BlockUtil.isIRRail(this.stock.getWorld(), posFront)) {
                TileRailBase rb = this.stock.getWorld().getBlockEntity(posFront, TileRailBase.class);
                rb = rb != null ? rb.getParentTile() : null;
                if (rb != null && !rb.getPos().equals(this.clackFrontPos) && rb.clacks()) {
                    if (volume > 0 && this.clackFront != null) {
                        if (!this.clackFront.isPlaying() && !this.clackRear.isPlaying()) {
                            this.clackFront.setPitch(pitch);
                            this.clackFront.setVolume(volume);
                            this.clackFront.play(new Vec3d(posFront));
                        }
                    }
                    this.clackFrontPos = rb.getPos();
                    if (this.stock.getWorld().getTicks() % ConfigGraphics.StockSwayChance == 0) {
                        this.swayImpulse += 7 * rb.getBumpiness();
                        this.swayImpulse = Math.min(this.swayImpulse, 20);
                    }
                }
            }
            Vec3i posRear = new Vec3i(VecUtil.fromWrongYawPitch(this.stock.getDefinition()
                            .getBogeyRear(this.stock.gauge), this.stock.getRotationYaw(), this.stock.getRotationPitch())
                    .add(this.stock.getPosition()));
            if (BlockUtil.isIRRail(this.stock.getWorld(), posRear)) {
                TileRailBase rb = this.stock.getWorld().getBlockEntity(posRear, TileRailBase.class);
                rb = rb != null ? rb.getParentTile() : null;
                if (rb != null && !rb.getPos().equals(this.clackRearPos) && rb.clacks()) {
                    if (volume > 0 && this.clackRear != null) {
                        if (!this.clackFront.isPlaying() && !this.clackRear.isPlaying()) {
                            this.clackRear.setPitch(pitch);
                            this.clackRear.setVolume(volume);
                            this.clackRear.play(new Vec3d(posRear));
                        }
                    }
                    this.clackRearPos = rb.getPos();
                }
            }

            this.swayMagnitude -= 0.07;
            double swayMin = this.stock.getCurrentSpeed().metric() / 300 / 3;
            this.swayMagnitude = Math.max(this.swayMagnitude, swayMin);

            if (this.swayImpulse > 0) {
                this.swayMagnitude += 0.3;
                this.swayImpulse -= 0.7;
            }
            this.swayMagnitude = Math.min(this.swayMagnitude, 3);
        }

        public double getRollDegrees() {
            if (Math.abs(this.stock.getCurrentSpeed().metric() * this.stock.gauge.scale()) < 4) {
                // don't calculate it
                return 0;
            }

            double sway = Math.cos(Math.toRadians(this.stock.getTickCount() * 13)) *
                    this.swayMagnitude / 5 *
                    this.stock.getDefinition().getSwayMultiplier() *
                    ConfigGraphics.StockSwayMultiplier;

            double tilt = this.stock.getDefinition()
                    .getTiltMultiplier() * (this.stock.getPrevRotationYaw() - this.stock.getRotationYaw()) * (this.stock.getCurrentSpeed()
                    .minecraft() > 0 ? 1 : -1);

            return sway + tilt;
        }

        public void removed() {
            // These should never be null, but for some reason they are
            if (this.clackFront != null) {
                this.clackFront.stop();
            }
            if (this.clackRear != null) {
                this.clackRear.stop();
            }
        }
    }
}

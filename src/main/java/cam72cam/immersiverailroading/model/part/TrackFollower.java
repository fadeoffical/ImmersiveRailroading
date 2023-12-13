package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.physics.MovementTrack;
import cam72cam.immersiverailroading.render.ExpireableMap;
import cam72cam.immersiverailroading.thirdparty.trackapi.ITrack;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.world.World;
import util.Matrix4;

import java.util.UUID;
import java.util.function.Function;

public class TrackFollower {
    private final EntityMoveableRollingStock stock;
    private final float max;
    private final float min;


    private final float offset;
    private final boolean front;
    private final Matrix4 matrix;
    private Vec3d pos;
    private float yawReadout;

    public TrackFollower(EntityMoveableRollingStock stock, ModelComponent frame, WheelSet wheels, boolean front) {
        this.stock = stock;
        this.offset = front ? stock.getDefinition().getBogeyFront(stock.gauge) : stock.getDefinition()
                .getBogeyRear(stock.gauge);
        this.front = front;
        this.matrix = new Matrix4();

        if (wheels != null && wheels.wheels.size() > 1) {
            this.max = -(float) (wheels.wheels.stream()
                    .mapToDouble(w -> w.wheel.center.x)
                    .min()
                    .getAsDouble() * stock.gauge.scale());
            this.min = -(float) (wheels.wheels.stream()
                    .mapToDouble(w -> w.wheel.center.x)
                    .max()
                    .getAsDouble() * stock.gauge.scale());
        } else if (wheels != null && wheels.wheels.size() == 1) {
            this.max = -(float) (wheels.wheels.get(0).wheel.min.x * stock.gauge.scale());
            this.min = -(float) (wheels.wheels.get(0).wheel.max.x * stock.gauge.scale());
        } else if (frame != null) {
            this.max = -(float) (frame.min.x * stock.gauge.scale());
            this.min = -(float) (frame.max.x * stock.gauge.scale());
        } else {
            this.max = this.min = this.offset;
        }
    }

    public Matrix4 getMatrix() {
        double recomputeDist = 0.1 * this.stock.gauge.scale();
        if (this.pos == null || this.stock.getPosition().distanceToSquared(this.pos) > recomputeDist * recomputeDist) {
            this.pos = this.stock.getPosition();
            float offsetYaw = (this.front ? this.stock.getFrontYaw() : this.stock.getRearYaw());
            if (this.offset >= this.min && this.offset <= this.max) {
                this.yawReadout = this.stock.getRotationYaw() - offsetYaw;
                this.matrix.setIdentity();
                this.matrix.translate(-this.offset, 0, 0);
                this.matrix.rotate(Math.toRadians(this.yawReadout), 0, 1, 0);
                this.matrix.translate(this.offset, 0, 0);
            } else {
                // Don't need to path to a point that's already on the track.  TODO This can also be used to improve accuracy of the offset rendering
                Vec3d offsetPos = this.pos.add(VecUtil.fromWrongYawPitch(this.offset, this.stock.getRotationYaw(), this.stock.getRotationPitch()));
                double toMinPoint = this.min - this.offset;
                double betweenPoints = this.max - this.min;

                float toPointYaw = 0;
                float atPointYaw = 0;
                float toPointPitch = 0;
                float atPointPitch = 0;

                Vec3d pointPos = this.nextPosition(this.stock.getWorld(), this.stock.gauge, offsetPos, this.stock.getRotationYaw(), offsetYaw, toMinPoint);
                Vec3d pointPosNext = this.nextPosition(this.stock.getWorld(), this.stock.gauge, pointPos, this.stock.getRotationYaw(), offsetYaw, betweenPoints);
                Vec3d delta = this.stock.getPosition().subtract(pointPos).scale(this.max); // Scale copies sign
                if (pointPos.distanceTo(pointPosNext) > 0.1 * this.stock.gauge.scale()) {
                    toPointYaw = VecUtil.toYaw(delta) + this.stock.getRotationYaw() + 180;
                    atPointYaw = VecUtil.toYaw(pointPos.subtract(pointPosNext)) + this.stock.getRotationYaw() + 180 - toPointYaw;

                    toPointPitch = -VecUtil.toPitch(VecUtil.rotateYaw(delta, this.stock.getRotationYaw() + 180)) + 90 + this.stock.getRotationPitch();
                    atPointPitch = -VecUtil.toPitch(VecUtil.rotateYaw(pointPos.subtract(pointPosNext), this.stock.getRotationYaw() + 180)) + 90 + this.stock.getRotationPitch() - toPointPitch;
                } else {
                    this.pos = null; // Force recompute
                }

                this.yawReadout = toPointYaw + atPointYaw;

                this.matrix.setIdentity();
                this.matrix.rotate(Math.toRadians(toPointYaw), 0, 1, 0);
                this.matrix.rotate(Math.toRadians(toPointPitch), 0, 0, 1);
                this.matrix.translate(-this.min / this.stock.gauge.scale(), 0, 0);
                this.matrix.rotate(Math.toRadians(atPointYaw), 0, 1, 0);
                this.matrix.rotate(Math.toRadians(atPointPitch), 0, 0, 1);
                this.matrix.translate(this.min / this.stock.gauge.scale(), 0, 0);
            }
        }
        return this.matrix;
    }

    public Vec3d nextPosition(World world, Gauge gauge, Vec3d currentPosition, float rotationYaw, float bogeyYaw, double distance) {
        ITrack rail = MovementTrack.findTrack(world, currentPosition, rotationYaw, gauge.getRailDistance());
        if (rail == null) {
            return currentPosition;
        }
        Vec3d result = rail.getNextPosition(currentPosition, VecUtil.fromWrongYaw(distance, bogeyYaw));
        if (result == null) {
            return currentPosition;
        }
        return result;
    }

    public float getYawReadout() {
        return this.yawReadout;
    }

    public static class TrackFollowers {
        private final ExpireableMap<UUID, TrackFollower> trackers = new ExpireableMap<>();
        private final Function<EntityMoveableRollingStock, TrackFollower> point;

        public TrackFollowers(Function<EntityMoveableRollingStock, TrackFollower> point) {
            this.point = point;
        }

        public TrackFollower get(EntityMoveableRollingStock stock) {
            TrackFollower tracker = this.trackers.get(stock.getUUID());
            if (tracker == null) {
                tracker = this.point.apply(stock);
                this.trackers.put(stock.getUUID(), tracker);
            }
            return tracker;
        }

        public void remove(EntityMoveableRollingStock stock) {
            this.trackers.put(stock.getUUID(), null);
        }
    }
}

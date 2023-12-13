package cam72cam.immersiverailroading.entity.physics;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.Tender;
import cam72cam.immersiverailroading.entity.physics.chrono.ServerChronoState;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.PhysicalMaterials;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.physics.MovementTrack;
import cam72cam.immersiverailroading.thirdparty.trackapi.ITrack;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.util.DegreeFuncs;
import cam72cam.mod.util.FastMath;
import cam72cam.mod.world.World;

import java.util.*;
import java.util.function.Function;

public class SimulationState {
    public int tickID;

    public Vec3d position;
    public double velocity;
    public float yaw;
    public float pitch;
    public IBoundingBox bounds;

    // Render purposes
    public float yawFront;
    public float yawRear;

    public Vec3d couplerPositionFront;
    public Vec3d couplerPositionRear;
    public UUID interactingFront;
    public UUID interactingRear;

    public float brakePressure;

    public Vec3d recalculatedAt;
    // All positions in the stock bounds
    public List<Vec3i> collidingBlocks;
    // Any track within those bounds
    public List<Vec3i> trackToUpdate;
    // Blocks that the stock would need to break to move
    public List<Vec3i> interferingBlocks;
    // How much force required to break the interfering blocks
    public float interferingResistance;
    // Blocks that were actually broken and need to be removed
    public List<Vec3i> blocksToBreak;

    public double directResistance;

    public Configuration config;
    public boolean dirty = true;
    public boolean atRest = true;
    public double collided;
    public boolean sliding;
    public boolean frontPushing;
    public boolean frontPulling;
    public boolean rearPushing;
    public boolean rearPulling;
    public Consist consist;

    public SimulationState(EntityCoupleableRollingStock stock) {
        this.tickID = ServerChronoState.getState(stock.getWorld()).getServerTickID();
        this.position = stock.getPosition();
        this.velocity = stock.getVelocity().length() *
                (DegreeFuncs.delta(VecUtil.toWrongYaw(stock.getVelocity()), stock.getRotationYaw()) < 90 ? 1 : -1);
        this.yaw = stock.getRotationYaw();
        this.pitch = stock.getRotationPitch();

        this.interactingFront = stock.getCoupledUUID(EntityCoupleableRollingStock.CouplerType.FRONT);
        this.interactingRear = stock.getCoupledUUID(EntityCoupleableRollingStock.CouplerType.BACK);

        this.brakePressure = stock.getBrakePressure();

        this.config = new Configuration(stock);

        this.bounds = this.config.bounds.apply(this);

        this.yawFront = stock.getFrontYaw();
        this.yawRear = stock.getRearYaw();

        this.recalculatedAt = this.position;

        this.calculateCouplerPositions();

        this.calculateBlockCollisions(Collections.emptyList());
        this.blocksToBreak = Collections.emptyList();

        this.consist = stock.consist;

        // If we just placed it, need to adjust it.  Otherwise, it already existed and is just loading in
        this.dirty = stock.newlyPlaced;
    }

    public void calculateCouplerPositions() {
        Vec3d bogeyFront = VecUtil.fromWrongYawPitch(this.config.offsetFront, this.yaw, this.pitch);
        Vec3d bogeyRear = VecUtil.fromWrongYawPitch(this.config.offsetRear, this.yaw, this.pitch);

        Vec3d positionFront = this.couplerPositionFront = this.position.add(bogeyFront);
        Vec3d positionRear = this.couplerPositionRear = this.position.add(bogeyRear);

        ITrack trackFront = MovementTrack.findTrack(this.config.world, positionFront, this.yaw, this.config.gauge.getRailDistance());
        ITrack trackRear = MovementTrack.findTrack(this.config.world, positionRear, this.yaw, this.config.gauge.getRailDistance());

        if (trackFront != null && trackRear != null) {
            Vec3d couplerVecFront = VecUtil.fromWrongYaw(this.config.couplerDistanceFront - this.config.offsetFront, this.yawFront);
            Vec3d couplerVecRear = VecUtil.fromWrongYaw(this.config.couplerDistanceRear - this.config.offsetRear, this.yawRear);

            this.couplerPositionFront = trackFront.getNextPosition(positionFront, couplerVecFront);
            this.couplerPositionRear = trackRear.getNextPosition(positionRear, couplerVecRear);
            //couplerPositionFront = couplerPositionFront.subtract(position).normalize().scale(Math.abs(config.couplerDistanceFront)).add(position);
            //couplerPositionRear = couplerPositionRear.subtract(position).normalize().scale(Math.abs(config.couplerDistanceRear)).add(position);
        }
        if (Objects.equals(this.couplerPositionFront, positionFront)) {
            this.couplerPositionFront = this.position.add(VecUtil.fromWrongYaw(this.config.couplerDistanceFront, this.yaw));
        }
        if (Objects.equals(this.couplerPositionRear, positionRear)) {
            this.couplerPositionRear = this.position.add(VecUtil.fromWrongYaw(this.config.couplerDistanceRear, this.yaw));
        }
    }

    public void calculateBlockCollisions(List<Vec3i> blocksAlreadyBroken) {
        this.collidingBlocks = this.config.world.blocksInBounds(this.bounds);
        this.trackToUpdate = new ArrayList<>();
        this.interferingBlocks = new ArrayList<>();
        this.interferingResistance = 0;

        for (Vec3i bp : this.collidingBlocks) {
            if (blocksAlreadyBroken.contains(bp)) {
                continue;
            }

            if (BlockUtil.isIRRail(this.config.world, bp)) {
                this.trackToUpdate.add(bp);
            } else {
                if (Config.ConfigDamage.TrainsBreakBlocks && !BlockUtil.isIRRail(this.config.world, bp.up())) {
                    this.interferingBlocks.add(bp);
                    this.interferingResistance += this.config.world.getBlockHardness(bp);
                }
            }
        }
    }

    private SimulationState(SimulationState prev) {
        this.tickID = prev.tickID + 1;
        this.position = prev.position;
        this.velocity = prev.velocity;
        this.yaw = prev.yaw;
        this.pitch = prev.pitch;

        this.interactingFront = prev.interactingFront;
        this.interactingRear = prev.interactingRear;

        this.brakePressure = prev.brakePressure;

        this.config = prev.config;

        this.bounds = prev.bounds;

        this.yawFront = prev.yawFront;
        this.yawRear = prev.yawRear;
        this.couplerPositionFront = prev.couplerPositionFront;
        this.couplerPositionRear = prev.couplerPositionRear;

        this.recalculatedAt = prev.recalculatedAt;
        this.collidingBlocks = prev.collidingBlocks;
        this.trackToUpdate = prev.trackToUpdate;
        this.interferingBlocks = prev.interferingBlocks;
        this.interferingResistance = prev.interferingResistance;
        this.blocksToBreak = Collections.emptyList();
        this.directResistance = prev.directResistance;

        this.consist = prev.consist;
    }

    public SimulationState next() {
        SimulationState next = new SimulationState(this);
        next.dirty = false;
        return next;
    }

    public SimulationState next(double distance, List<Vec3i> blocksAlreadyBroken) {
        SimulationState next = new SimulationState(this);
        next.moveAlongTrack(distance);
        if (this.position.equals(next.position)) {
            next.velocity = 0;
        } else {
            next.calculateCouplerPositions();
            next.bounds = next.config.bounds.apply(next);

            // We will actually break the blocks
            this.blocksToBreak = this.interferingBlocks;
            // We can now ignore those positions for the rest of the simulation
            blocksAlreadyBroken.addAll(this.blocksToBreak);

            // Calculate the next states interference
            double minDist = Math.max(0.5, Math.abs(this.velocity * 4));
            if (next.recalculatedAt.distanceToSquared(next.position) > minDist * minDist) {
                next.calculateBlockCollisions(blocksAlreadyBroken);
                next.recalculatedAt = next.position;
                next.directResistance = this.config.directResistanceNewtons.apply(this.trackToUpdate);
            } else {
                // We put off calculating collisions for now
                next.interferingBlocks = Collections.emptyList();
                next.interferingResistance = 0;
            }
        }
        return next;
    }

    private void moveAlongTrack(double distance) {
        Vec3d positionFront = VecUtil.fromWrongYawPitch(this.config.offsetFront, this.yaw, this.pitch).add(this.position);
        Vec3d positionRear = VecUtil.fromWrongYawPitch(this.config.offsetRear, this.yaw, this.pitch).add(this.position);

        // Find tracks
        ITrack trackFront = MovementTrack.findTrack(this.config.world, positionFront, this.yawFront, this.config.gauge.getRailDistance());
        ITrack trackRear = MovementTrack.findTrack(this.config.world, positionRear, this.yawRear, this.config.gauge.getRailDistance());
        if (trackFront == null || trackRear == null) {
            return;
        }

        boolean isTurnTable = false;
        if (Math.abs(distance) < 0.0001) {

            TileRailBase frontBase = trackFront instanceof TileRailBase ? (TileRailBase) trackFront : null;
            TileRailBase rearBase = trackRear instanceof TileRailBase ? (TileRailBase) trackRear : null;
            isTurnTable = frontBase != null &&
                    (
                            //frontBase.getTicksExisted() < 100 ||
                            frontBase.getParentTile() != null &&
                                    frontBase.getParentTile().info.settings.type == TrackItems.TURNTABLE
                    );
            isTurnTable = isTurnTable || rearBase != null &&
                    (
                            //rearBase.getTicksExisted() < 100 ||
                            rearBase.getParentTile() != null &&
                                    rearBase.getParentTile().info.settings.type == TrackItems.TURNTABLE
                    );

            if (!isTurnTable) {
                return;
            }
        }

        boolean isReversed = distance < 0;
        if (isReversed) {
            distance = -distance;
            this.yawFront += 180;
            this.yawRear += 180;
        }

        Vec3d nextFront = trackFront.getNextPosition(positionFront, VecUtil.fromWrongYaw(distance, this.yawFront));
        Vec3d nextRear = trackRear.getNextPosition(positionRear, VecUtil.fromWrongYaw(distance, this.yawRear));

        if (!nextFront.equals(positionFront) && !nextRear.equals(positionRear)) {
            this.yawFront = VecUtil.toWrongYaw(nextFront.subtract(positionFront));
            this.yawRear = VecUtil.toWrongYaw(nextRear.subtract(positionRear));

            // TODO flatten this vector calculation
            Vec3d deltaCenter = nextFront.subtract(this.position).scale(this.config.offsetRear)
                    .subtract(nextRear.subtract(this.position).scale(this.config.offsetFront))
                    .scale(-1 / (this.config.offsetFront - this.config.offsetRear));

            Vec3d bogeyDelta = nextFront.subtract(nextRear);
            this.yaw = VecUtil.toWrongYaw(bogeyDelta);
            this.pitch = (float) Math.toDegrees(FastMath.atan2(bogeyDelta.y, nextRear.distanceTo(nextFront)));
            // TODO Rescale fixes issues with curves losing precision, but breaks when correcting stock positions
            this.position = this.position.add(deltaCenter/*.normalize().scale(distance)*/);
        }

        if (isReversed) {
            this.yawFront += 180;
            this.yawRear += 180;
        }

        if (isTurnTable) {
            this.yawFront = this.yaw;
            this.yawRear = this.yaw;
        }

        // Fix bogeys pointing in opposite directions
        if (DegreeFuncs.delta(this.yawFront, this.yaw) > 90 || DegreeFuncs.delta(this.yawFront, this.yawRear) > 90) {
            this.yawFront = this.yaw;
            this.yawRear = this.yaw;
        }
    }

    public void update(EntityCoupleableRollingStock stock) {
        Configuration oldConfig = this.config;
        this.config = new Configuration(stock);
        this.dirty = this.dirty || !this.config.equals(oldConfig);
    }

    public boolean atRest() {
        return this.velocity == 0 && Math.abs(this.forcesNewtons()) < this.frictionNewtons();
    }

    public double forcesNewtons() {
        double gradeForceNewtons = this.config.massKg * -9.8 * Math.sin(Math.toRadians(this.pitch)) * Config.ConfigBalance.slopeMultiplier;
        return this.config.tractiveEffortNewtons(Speed.fromMinecraft(this.velocity)) + gradeForceNewtons;
    }

    public double frictionNewtons() {
        // https://evilgeniustech.com/idiotsGuideToRailroadPhysics/OtherLocomotiveForces/#rolling-resistance
        double rollingResistanceNewtons = this.config.rollingResistanceCoefficient * (this.config.massKg * 9.8);
        // https://www.arema.org/files/pubs/pgre/PGChapter2.pdf
        // ~15 lb/ton -> 0.01 weight ratio -> 0.001 uS with gravity
        double startingFriction = this.velocity == 0 ? 0.001 * this.config.massKg * 9.8 : 0;
        // TODO This is kinda directional?
        double blockResistanceNewtons = this.interferingResistance * 1000 * Config.ConfigDamage.blockHardness;

        double brakeAdhesionNewtons = this.config.designAdhesionNewtons * Math.min(1, Math.max(this.brakePressure, this.config.independentBrakePosition));

        this.sliding = false;
        if (brakeAdhesionNewtons > this.config.maximumAdhesionNewtons && Math.abs(this.velocity) > 0.01) {
            // WWWWWHHHEEEEE!!! SLIDING!!!!
            double kineticFriction = PhysicalMaterials.STEEL.kineticFriction(PhysicalMaterials.STEEL);
            brakeAdhesionNewtons = this.config.massKg * kineticFriction;
            this.sliding = true;
        }

        brakeAdhesionNewtons *= Config.ConfigBalance.brakeMultiplier;

        return rollingResistanceNewtons + blockResistanceNewtons + brakeAdhesionNewtons + this.directResistance + startingFriction;
    }

    public static class Configuration {
        private final Function<List<Vec3i>, Double> directResistanceNewtons;
        public UUID id;
        public Gauge gauge;
        public World world;
        public double width;
        public double length;
        public double height;
        public Function<SimulationState, IBoundingBox> bounds;
        public float offsetFront;
        public float offsetRear;
        public boolean couplerEngagedFront;
        public boolean couplerEngagedRear;
        public double couplerDistanceFront;
        public double couplerDistanceRear;
        public double couplerSlackFront;
        public double couplerSlackRear;
        public double massKg;
        public double maximumAdhesionNewtons;
        public double designAdhesionNewtons;
        public double rollingResistanceCoefficient;
        public Double desiredBrakePressure;
        public double independentBrakePosition;
        public boolean hasPressureBrake;
        // We don't actually want to use this value, it's only for dirty checking
        private final double tractiveEffortFactors;
        private final Function<Speed, Double> tractiveEffortNewtons;

        public Configuration(EntityCoupleableRollingStock stock) {
            this.id = stock.getUUID();
            this.gauge = stock.gauge;
            this.world = stock.getWorld();

            this.width = stock.getDefinition().getWidth(this.gauge);
            this.length = stock.getDefinition().getLength(this.gauge);
            this.height = stock.getDefinition().getHeight(this.gauge);
            this.bounds = s -> stock.getDefinition().getBounds(s.yaw, this.gauge)
                    .offset(s.position.add(0, -(s.position.y % 1), 0))
                    .contract(new Vec3d(0, 0, 0.5 * this.gauge.scale()));
            //.contract(new Vec3d(0, 0.5 * this.gauge.scale(), 0))
            //.offset(new Vec3d(0, 0.5 * this.gauge.scale(), 0));

            this.offsetFront = stock.getDefinition().getBogeyFront(this.gauge);
            this.offsetRear = stock.getDefinition().getBogeyRear(this.gauge);

            this.couplerEngagedFront = stock.isCouplerEngaged(EntityCoupleableRollingStock.CouplerType.FRONT);
            this.couplerEngagedRear = stock.isCouplerEngaged(EntityCoupleableRollingStock.CouplerType.BACK);
            this.couplerDistanceFront = stock.getDefinition()
                    .getCouplerPosition(EntityCoupleableRollingStock.CouplerType.FRONT, this.gauge);
            this.couplerDistanceRear = -stock.getDefinition()
                    .getCouplerPosition(EntityCoupleableRollingStock.CouplerType.BACK, this.gauge);
            this.couplerSlackFront = stock.getDefinition()
                    .getCouplerSlack(EntityCoupleableRollingStock.CouplerType.FRONT, this.gauge);
            this.couplerSlackRear = stock.getDefinition()
                    .getCouplerSlack(EntityCoupleableRollingStock.CouplerType.BACK, this.gauge);

            this.massKg = stock.getWeight();
            // When FuelRequired is false, most of the time the locos are empty.  Work around that here
            double designMassKg = !Config.ConfigBalance.FuelRequired && (stock instanceof Locomotive || stock instanceof Tender) ? this.massKg : stock.getMaxWeight();

            if (stock instanceof Locomotive) {
                Locomotive locomotive = (Locomotive) stock;
                this.tractiveEffortNewtons = locomotive::getTractiveEffortNewtons;
                this.tractiveEffortFactors = locomotive.getThrottle() + (locomotive.getReverser() * 10);
                this.desiredBrakePressure = (double) locomotive.getTrainBrake();
            } else {
                this.tractiveEffortNewtons = speed -> 0d;
                this.tractiveEffortFactors = 0;
                this.desiredBrakePressure = null;
            }


            double staticFriction = PhysicalMaterials.STEEL.staticFriction(PhysicalMaterials.STEEL);
            this.maximumAdhesionNewtons = this.massKg * staticFriction * 9.8 * stock.getBrakeAdhesionEfficiency();
            this.designAdhesionNewtons = designMassKg * staticFriction * 9.8 * stock.getBrakeSystemEfficiency();
            this.independentBrakePosition = stock.getIndependentBrake();
            this.directResistanceNewtons = stock::getDirectFrictionNewtons;
            this.hasPressureBrake = stock.getDefinition().hasPressureBrake();

            this.rollingResistanceCoefficient = stock.getDefinition().rollingResistanceCoefficient;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Configuration) {
                Configuration other = (Configuration) o;
                return this.couplerEngagedFront == other.couplerEngagedFront &&
                        this.couplerEngagedRear == other.couplerEngagedRear &&
                        Math.abs(this.tractiveEffortFactors - other.tractiveEffortFactors) < 0.01 &&
                        Math.abs(this.massKg - other.massKg) / this.massKg < 0.01 &&
                        (this.desiredBrakePressure == null || Math.abs(this.desiredBrakePressure - other.desiredBrakePressure) < 0.001) &&
                        Math.abs(this.independentBrakePosition - other.independentBrakePosition) < 0.01;
            }
            return false;
        }

        public double tractiveEffortNewtons(Speed speed) {
            return this.tractiveEffortNewtons.apply(speed);
        }
    }
}

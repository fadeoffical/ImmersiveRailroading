package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.physics.SimulationState;
import cam72cam.immersiverailroading.entity.physics.chrono.ChronoState;
import cam72cam.immersiverailroading.entity.physics.chrono.ServerChronoState;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.library.augment.impl.SpeedRetarderAugment;
import cam72cam.immersiverailroading.library.unit.Speed;
import cam72cam.immersiverailroading.model.part.Control;
import cam72cam.immersiverailroading.net.SoundPacket;
import cam72cam.immersiverailroading.physics.TickPos;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.RealBB;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.custom.ICollision;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class EntityMovableRollingStock extends EntityRidableRollingStock implements ICollision {

    private static final String DAMAGE_SOURCE_HIT = "immersiverailroading:hitByTrain";
    private static final String DAMAGE_SOURCE_HIT_IN_DARKNESS = "immersiverailroading:hitByTrainInDarkness";

    @TagField("distanceTraveled")
    public double distanceTraveled;

    @TagField(value = "positions", mapper = TickPos.ListTagMapper.class)
    public List<TickPos> positions = new ArrayList<>();

    public List<SimulationState> states = new ArrayList<>();

    @TagSync
    @TagField("SLIDING")
    public boolean sliding;

    public long lastCollision;

    public boolean newlyPlaced;

    @TagField("frontYaw")
    private Float frontYaw;

    @TagField("rearYaw")
    private Float rearYaw;

    private Speed currentSpeed;

    private RealBB boundingBox;

    private float[][] heightMapCache;

    @TagSync
    @TagField("IND_BRAKE")
    private float independentBrake = 0;

    @TagSync
    @TagField("BRAKE_PRESSURE")
    private float trainBrakePressure = 0;

    @Override
    public void load(TagCompound data) {
        super.load(data);

        if (this.frontYaw == null) {
            this.frontYaw = this.getRotationYaw();
        }
        if (this.rearYaw == null) {
            this.rearYaw = this.getRotationYaw();
        }
    }

    public void initPositions(TickPos tp) {
        this.positions = new ArrayList<>();
        this.positions.add(tp);
    }

    /*
     * Entity Overrides for BB
     */

    public void clearHeightMap() {
        this.heightMapCache = null;
        this.boundingBox = null;
    }

    /**
     * This is where fun network synchronization is handled So normally every 2 seconds we get a new packet with stock
     * positional information for the next 4 seconds
     */
    public void handleTickPosPacket(List<TickPos> newPositions) {

        if (newPositions.size() != 0) {
            this.clearPositionCache();

            if (ChronoState.getState(this.getWorld()) == null) {
                this.positions.clear();
            } else {
                int tickID = (int) Math.floor(ChronoState.getState(this.getWorld()).getTickID());
                List<Integer> newIds = newPositions.stream().map(p -> p.tickId).collect(Collectors.toList());
                this.positions.removeAll(this.positions.stream()
                        // old OR far in the future OR to be replaced
                        .filter(p -> p.tickId < tickID - 30 || p.tickId > tickID + 60 || newIds.contains(p.tickId))
                        .collect(Collectors.toList()));
            }
            // unordered
            this.positions.addAll(newPositions);
        }
    }

    protected void clearPositionCache() {
        this.boundingBox = null;
    }

    /*
     * Speed Info
     */

    @Override
    public void onTick() {
        super.onTick();

        if (this.getWorld().isServer) {
            if (this.getDefinition().hasIndependentBrake()) {
                for (Control<?> control : this.getDefinition().getModel().getControls()) {
                    if (!this.getDefinition()
                            .isLinearBrakeControl() && control.part.type == ModelComponentType.INDEPENDENT_BRAKE_X) {
                        this.setIndependentBrake(Math.max(0, Math.min(1, this.getIndependentBrake() + (this.getControlPosition(control) - 0.5f) / 8)));
                    }
                }
            }

            SimulationState state = this.getCurrentState();
            if (state != null) {
                this.trainBrakePressure = state.brakePressure;
                this.sliding = state.sliding;

                if (state.collided > 0.1 && this.getTickCount() - this.lastCollision > 20) {
                    this.lastCollision = this.getTickCount();
                    new SoundPacket(this.getDefinition().collision_sound, this.getPosition(), this.getVelocity(), (float) Math.min(1.0, state.collided), 1, (int) (100 * this.getGauge()
                            .scale()), this.soundScale(), SoundPacket.PacketSoundCategory.COLLISION).sendToObserving(this);
                }

                state.blocksToBreak.forEach(blockPosition -> this.getWorld()
                        .breakBlock(blockPosition, Config.ConfigDamage.dropSnowBalls || !this.getWorld().isSnow(blockPosition)));
                state.trackToUpdate.stream()
                        .map(blockPosition -> this.getWorld().getBlockEntity(blockPosition, TileRailBase.class))
                        .filter(Objects::nonNull)
                        .forEach(rail -> {
                            rail.cleanSnow();
                            rail.stockOverhead(this);
                        });
            }
        }

        if (this.getWorld().isClient) {
            this.getDefinition().getModel().onClientTick(this);
        }

        // Apply position onTick
        TickPos currentPos = this.getTickPos();
        if (currentPos == null) {
            // Not loaded yet or not moving
            return;
        }

        Vec3d prevPos = this.getPosition();
        double prevPosX = prevPos.x;
        double prevPosY = prevPos.y;
        double prevPosZ = prevPos.z;

        this.setRotationYaw(currentPos.rotationYaw);
        this.setRotationPitch(currentPos.rotationPitch);
        this.frontYaw = currentPos.frontYaw;
        this.rearYaw = currentPos.rearYaw;

        this.currentSpeed = currentPos.speed;

        if (!this.sliding) {
            this.distanceTraveled += (float) this.currentSpeed.as(Speed.SpeedUnit.METERS_PER_TICK)
                    .value() * this.getTickSkew();
            this.distanceTraveled %= 32000;// Wrap around to prevent double float issues
        }

        this.setPosition(currentPos.position);
        this.setVelocity(this.getPosition().subtract(prevPosX, prevPosY, prevPosZ));

        if (this.getVelocity().length() > 0.001) {
            this.clearPositionCache();
        }

        if (this.getCurrentSpeed().as(Speed.SpeedUnit.KILOMETERS_PER_HOUR).absolute().value() > 1) {
            List<Entity> entitiesWithin = this.getWorld()
                    .getEntities((Entity entity) -> (entity.isLiving() || entity.isPlayer()) && this.getCollision()
                            .intersects(entity.getBounds()), Entity.class);
            for (Entity entity : entitiesWithin) {
                if (entity instanceof EntityMovableRollingStock) {
                    // rolling stock collisions handled by looking at the front and
                    // rear coupler offsets
                    continue;
                }

                if (entity.getRiding() instanceof EntityMovableRollingStock) {
                    // Don't apply realBoundingBox to passengers
                    continue;
                }

                if (entity.isPlayer()) {
                    if (entity.getTickCount() < 20 * 5) {
                        // Give the internal a chance to getContents out of the way
                        continue;
                    }
                }


                // Chunk.getEntitiesOfTypeWithinAABB() does a reverse aabb intersect
                // We need to do a forward lookup
                // TODO move this to UMC?
                if (!this.getCollision().intersects(entity.getBounds())) {
                    // miss
                    continue;
                }

                // Move entity

                entity.setVelocity(this.getVelocity().scale(2));
                // Force update
                //TODO entity.onUpdate();

                double speedDamage = this.getCurrentSpeed()
                        .as(Speed.SpeedUnit.KILOMETERS_PER_HOUR)
                        .absolute()
                        .value() / Config.ConfigDamage.entitySpeedDamage;

                if (speedDamage > 1) {
                    Vec3i entityPosition = entity.getBlockPosition();
                    // todo: add World#getLightLevel(BlockPos) to UMC with this logic
                    float skyLightLevel = this.getWorld().getSkyLightLevel(entityPosition);
                    float blockLightLevel = this.getWorld().getBlockLightLevel(entityPosition);
                    boolean isDark = Math.max(skyLightLevel, blockLightLevel) < 8.0F;

                    String damageSource = isDark ? DAMAGE_SOURCE_HIT_IN_DARKNESS : DAMAGE_SOURCE_HIT;
                    entity.directDamage(damageSource, speedDamage);
                }
            }

            // Riding on top of cars
            final RealBB realBoundingBox = this.getCollision().offset(new Vec3d(0, this.getGauge().scale() * 2, 0));
            List<Entity> entitiesAbove = this.getWorld()
                    .getEntities((Entity entity) -> (entity.isLiving() || entity.isPlayer()) && realBoundingBox.intersects(entity.getBounds()), Entity.class);
            for (Entity entity : entitiesAbove) {
                if (entity instanceof EntityMovableRollingStock) {
                    continue;
                }
                if (entity.getRiding() instanceof EntityMovableRollingStock) {
                    continue;
                }

                // Chunk.getEntitiesOfTypeWithinAABB() does a reverse aabb intersect
                // We need to do a forward lookup
                if (!realBoundingBox.intersects(entity.getBounds())) {
                    // miss
                    continue;
                }

                //Vec3d pos = entity.getPositionVector();
                //pos = pos.addVector(this.motionX, this.motionY, this.motionZ);
                //entity.setPosition(pos.x, pos.y, pos.z);

                entity.setVelocity(this.getVelocity().add(0, entity.getVelocity().y, 0));
            }
        }

        if (this.getWorld().isServer) {
            double speed = this.getCurrentSpeed().value();
            this.setControlPosition("MOVINGFORWARD", speed > 0 ? 1 : 0);
            this.setControlPosition("NOTMOVING", speed == 0 ? 1 : 0);
            this.setControlPosition("MOVINGBACKWARD", speed < 0 ? 1 : 0);
        }
    }

    public float getIndependentBrake() {
        return this.getDefinition().hasIndependentBrake() ? this.independentBrake : 0;
    }

    public SimulationState getCurrentState() {
        int tickID = ServerChronoState.getState(this.getWorld()).getServerTickID();
        return this.states.stream().filter(state -> state.tickID == tickID).findFirst().orElse(null);
    }

    public TickPos getTickPos() {
        if (ChronoState.getState(this.getWorld()) == null) return null;

        double tick = ChronoState.getState(this.getWorld()).getTickID();
        int currentTickID = (int) Math.floor(tick);
        int nextTickID = (int) Math.ceil(tick);
        TickPos current = null;
        TickPos next = null;

        for (TickPos position : this.positions) {
            if (position.tickId == currentTickID) {
                current = position;
            }
            if (position.tickId == nextTickID) {
                next = position;
            }
            if (current != null && next != null) {
                break;
            }
        }
        if (current == null) {
            return null;
        }
        if (next == null || current == next || this.getWorld().isServer) {
            return current;
        }
        // Skew
        return TickPos.skew(current, next, tick);
    }

    public float getTickSkew() {
        ChronoState state = ChronoState.getState(this.getWorld());
        return state != null ? (float) state.getTickSkew() : 1;
    }

    public Speed getCurrentSpeed() {
        if (this.currentSpeed == null) {
            //Fallback
            // does not work for curves
            Vec3d motion = this.getVelocity();
            float speed = (float) Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
            if (Float.isNaN(speed)) {
                speed = 0;
            }
            this.currentSpeed = Speed.fromUnit(speed, Speed.SpeedUnit.METERS_PER_TICK);
        }
        return this.currentSpeed;
    }

    @Override
    public RealBB getCollision() {
        if (this.boundingBox == null) {
            this.boundingBox = this.getDefinition()
                    .getBounds(this.getRotationYaw(), this.getGauge())
                    .offset(this.getPosition())
                    .withHeightMap(this.getHeightMap())
                    .contract(new Vec3d(0, 0.5 * this.getGauge().scale(), 0))
                    .offset(new Vec3d(0, 0.5 * this.getGauge().scale(), 0));
        }
        return this.boundingBox;
    }

    private float[][] getHeightMap() {
        if (this.heightMapCache == null) {
            this.heightMapCache = this.getDefinition().createHeightMap(this);
        }
        return this.heightMapCache;
    }

    public void setCurrentSpeed(Speed newSpeed) {
        this.currentSpeed = newSpeed;
    }

    public void setIndependentBrake(float newIndependentBrake) {
        newIndependentBrake = Math.min(1, Math.max(0, newIndependentBrake));
        if (this.getIndependentBrake() != newIndependentBrake && this.getDefinition().hasIndependentBrake()) {
            if (this.getDefinition().isLinearBrakeControl()) {
                this.setControlPositions(ModelComponentType.INDEPENDENT_BRAKE_X, newIndependentBrake);
            }
            this.independentBrake = newIndependentBrake;
        }
    }

    /*
     *
     * Client side render guessing
     */

    public float getFrontYaw() {
        if (this.frontYaw != null) {
            return this.frontYaw;
        }
        return this.getRotationYaw();
    }

    public void setFrontYaw(float frontYaw) {
        this.frontYaw = frontYaw;
    }

    public float getRearYaw() {
        if (this.rearYaw != null) {
            return this.rearYaw;
        }
        return this.getRotationYaw();
    }

    public void setRearYaw(float rearYaw) {
        this.rearYaw = rearYaw;
    }

    @Override
    public void onRemoved() {
        super.onRemoved();

        if (this.getWorld().isClient) {
            this.getDefinition().getModel().onClientRemoved(this);
        }
    }

    @Override
    public void handleKeyPress(Player source, KeyTypes key, boolean disableIndependentThrottle) {
        float independentBrakeNotch = 0.04f;

        if (source.hasPermission(Permissions.BRAKE_CONTROL)) {
            switch (key) {
                case INDEPENDENT_BRAKE_UP:
                    this.setIndependentBrake(this.getIndependentBrake() + independentBrakeNotch);
                    break;
                case INDEPENDENT_BRAKE_ZERO:
                    this.setIndependentBrake(0f);
                    break;
                case INDEPENDENT_BRAKE_DOWN:
                    this.setIndependentBrake(this.getIndependentBrake() - independentBrakeNotch);
                    break;
                default:
                    super.handleKeyPress(source, key, disableIndependentThrottle);
            }
        } else {
            super.handleKeyPress(source, key, disableIndependentThrottle);
        }
    }

    @Override
    protected float defaultControlPosition(Control<?> control) {
        if (Objects.requireNonNull(control.part.type) == ModelComponentType.INDEPENDENT_BRAKE_X) {
            return this.getDefinition().isLinearBrakeControl() ? 0 : 0.5f;
        }
        return super.defaultControlPosition(control);
    }

    @Override
    public void onDrag(Control<?> control, double newValue) {
        super.onDrag(control, newValue);
        if (Objects.requireNonNull(control.part.type) == ModelComponentType.INDEPENDENT_BRAKE_X) {
            if (this.getDefinition().isLinearBrakeControl()) {
                this.setIndependentBrake(this.getControlPosition(control));
            }
        }
    }

    @Override
    public void onDragRelease(Control<?> control) {
        super.onDragRelease(control);
        if (!this.getDefinition()
                .isLinearBrakeControl() && control.part.type == ModelComponentType.INDEPENDENT_BRAKE_X) {
            this.setControlPosition(control, 0.5f);
        }
    }

    @Deprecated
    public TickPos getCurrentTickPosAndPrune() {
        return this.getTickPos();
    }

    /**
     * This assumes that the brake system is designed to apply a percentage of the total adhesion That percentage was
     * improved over time, with the materials used (friction coefficient) helping inform our guess Though, I'm going to
     * limit it to 75% of the total possible adhesion
     */
    public double getBrakeSystemEfficiency() {
        return this.getDefinition().getBrakeShoeFriction();
    }

    public boolean isSliding() {
        return this.sliding;
    }

    public double getDirectFrictionNewtons(List<Vec3i> track) {

        double retarderNewtons = track.stream()
                .map(position -> this.getWorld().getBlockEntity(position, TileRailBase.class))
                .filter(Objects::nonNull)
                .filter(tileRailBase -> tileRailBase.isAugmentOfType(SpeedRetarderAugment.class))
                .map(TileRailBase::getPos)
                .mapToInt(position -> this.getWorld().getRedstone(position))
                .sum();

        retarderNewtons /= 15f / track.size();

        double directFrictionCoefficient = this.getDefinition().directFrictionCoefficient;
        double independentNewtons = this.getIndependentBrake() * directFrictionCoefficient;
        double pressureNewtons = this.getBrakePressure() * directFrictionCoefficient;

        double newtons = this.getWeight() * 9.8;
        return (retarderNewtons + independentNewtons + pressureNewtons) * newtons;
    }

    public float getBrakePressure() {
        return this.trainBrakePressure;
    }

    public double getBrakeAdhesionEfficiency() {
        return 1;
    }
}

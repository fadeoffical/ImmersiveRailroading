package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.entity.physics.SimulationState;
import cam72cam.immersiverailroading.items.ItemRadioCtrlCard;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.model.part.Control;
import cam72cam.immersiverailroading.physics.MovementTrack;
import cam72cam.immersiverailroading.registry.LocomotiveDefinition;
import cam72cam.immersiverailroading.thirdparty.trackapi.ITrack;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.serialization.StrictTagMapper;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;

import java.util.OptionalDouble;
import java.util.UUID;

import static cam72cam.immersiverailroading.library.PhysicalMaterials.STEEL;

public abstract class Locomotive extends FreightTank {
    private static final float throttleDelta = 0.04f;
    private static final float trainBrakeNotch = 0.04f;
    @TagSync
    @TagField(value = "HORN_PULL")
    public float hornPull;
    @TagSync
    @TagField("HORN")
    protected int hornTime = 0;
    @TagSync
    @TagField(value = "HORN_PLAYER", mapper = StrictTagMapper.class)
    protected UUID hornPlayer = null;
    @TagField("deadMansSwitch")
    private boolean deadMansSwitch;
    private int deadManChangeTimeout;
    @TagSync
    @TagField("THROTTLE")
    private float throttle = 0;
    @TagSync
    @TagField("REVERSER")
    private float reverser = 0;
    @TagSync
    @TagField("AIR_BRAKE")
    private float trainBrake = 0;
    @TagSync
    @TagField("BELL")
    private int bellTime = 0;
    private boolean bellControl = false;

    private int bellKeyTimeout;

    @TagSync
    @TagField("cogging")
    private boolean cogging = false;

    private boolean slipping = false;

    /*
     *
     * Stock Definitions
     *
     */

    @Override
    public boolean openGui(Player player) {
        return false;
    }

    /*
     *
     * EntityRollingStock Overrides
     */

    @Override
    public void onTick() {
        super.onTick();

        if (this.getWorld().isServer) {
            this.sync.setInterval(5);
            for (Control<?> control : this.getDefinition().getModel().getControls()) {
                // Logic duplicated in Readouts#setValue
                if (!this.getDefinition().isLinearBrakeControl() && control.part.type == ModelComponentType.TRAIN_BRAKE_X) {
                    this.setTrainBrake(Math.max(0, Math.min(1, this.getTrainBrake() + (this.getControlPosition(control) - 0.5f) / 8)));
                }
            }

            if (this.deadManChangeTimeout > 0) {
                this.deadManChangeTimeout -= 1;
            }
            if (this.bellKeyTimeout > 0) {
                this.bellKeyTimeout--;
            }

            if (this.deadMansSwitch && !this.getCurrentSpeed().isZero()) {
                boolean hasDriver = this.getPassengers().stream().anyMatch(Entity::isPlayer);
                if (!hasDriver) {
                    this.setThrottle(0);
                    this.setTrainBrake(1);
                }
            }
            if (this.hornTime > 0) {
                this.hornTime--;
            } else if (this.hornPlayer != null) {
                this.hornPlayer = null;
            }
            if (this.hornTime == 0) {
                this.hornPull = 0;
            }
            OptionalDouble control = this.getDefinition().getModel().getControls().stream()
                    .filter(x -> x.part.type == ModelComponentType.BELL_CONTROL_X)
                    .mapToDouble(this::getControlPosition)
                    .max();
            if (control.isPresent() && control.getAsDouble() > 0) {
                this.bellTime = 10;
                this.bellControl = true;
            }
            if (this.bellTime > 0 && (!this.getDefinition().toggleBell || this.bellControl)) {
                this.bellTime--;
                if (this.bellTime == 0) {
                    this.bellControl = false;
                }
            }
        }

        this.distanceTraveled += this.simulateWheelSlip();

        if (this.getWorld().isServer) {
            this.setControlPosition("REVERSERFORWARD", this.getReverser() > 0 ? 1 : 0);
            this.setControlPosition("REVERSERNEUTRAL", this.getReverser() == 0 ? 1 : 0);
            this.setControlPosition("REVERSERBACKWARD", this.getReverser() < 0 ? 1 : 0);
        }

        if (this.getWorld().isServer) {
            if (this.getDefinition().isCog() && this.getTickCount() % 20 == 0) {
                SimulationState state = this.getCurrentState();
                if (state != null) {
                    ITrack found = MovementTrack.findTrack(this.getWorld(), state.couplerPositionFront, state.yaw, this.gauge.value());
                    if (found instanceof TileRailBase) {
                        TileRailBase onTrack = (TileRailBase) found;
                        this.cogging = onTrack.isCog();
                    }
                }
            }
        }
    }

    protected boolean forceLinkThrottleReverser() {
        return false;
    }

    protected float getReverserDelta() {
        return throttleDelta;
    }

    public void onDrag(Control<?> component, double newValue) {
        super.onDrag(component, newValue);
        //System.out.println("DRAG " + component + ": "+ getControlPosition(component));
        switch (component.part.type) {
            case THROTTLE_X:
                this.setThrottle(this.getControlPosition(component));
                break;
            case TRAIN_BRAKE_X:
                if (this.getDefinition().isLinearBrakeControl()) {
                    this.setTrainBrake(this.getControlPosition(component));
                }
                break;
            case REVERSER_X:
                this.setReverser((0.5f - this.getControlPosition(component)) * 2);
                break;
            case THROTTLE_BRAKE_X:
                // value 0     0.5     1
                // throt 0      0      1
                // brake 1      0      0
                this.setTrainBrake(1 - this.getControlPosition(component) * 2);
                this.setThrottle(this.getControlPosition(component) * 2 - 1);
                break;
        }
    }

    @Override
    public void onDragRelease(Control<?> control) {
        super.onDragRelease(control);
        if (!this.getDefinition().isLinearBrakeControl() && control.part.type == ModelComponentType.TRAIN_BRAKE_X) {
            this.setControlPosition(control, 0.5f);
        }
    }

    @Override
    protected float defaultControlPosition(Control<?> control) {
        switch (control.part.type) {
            case THROTTLE_BRAKE_X:
            case REVERSER_X:
                return 0.5f;
            case TRAIN_BRAKE_X:
                return this.getDefinition().isLinearBrakeControl() ? 0 : 0.5f;
            default:
                return super.defaultControlPosition(control);
        }
    }

    @Override
    public void handleKeyPress(Player source, KeyTypes key, boolean disableIndependentThrottle) {

        if (disableIndependentThrottle) {
            switch (key) {
                case THROTTLE_UP:
                    key = KeyTypes.REVERSER_UP;
                    break;
                case THROTTLE_ZERO:
                    key = KeyTypes.REVERSER_ZERO;
                    break;
                case THROTTLE_DOWN:
                    key = KeyTypes.REVERSER_DOWN;
                    break;
                case REVERSER_UP:
                case REVERSER_ZERO:
                case REVERSER_DOWN:
                    return;
            }
        } else if (this.getDefinition().isLinkedBrakeThrottle()) {
            switch (key) {
                case THROTTLE_UP:
                    if (this.getTrainBrake() > 0) {
                        key = KeyTypes.TRAIN_BRAKE_DOWN;
                    }
                    break;
                case THROTTLE_ZERO:
                    this.setTrainBrake(0);
                    break;
                case THROTTLE_DOWN:
                    if (this.getThrottle() == 0) {
                        key = KeyTypes.TRAIN_BRAKE_UP;
                    }
                    break;
                case TRAIN_BRAKE_UP:
                case TRAIN_BRAKE_ZERO:
                case TRAIN_BRAKE_DOWN:
                    return;
            }
        }

        boolean linkThrottleReverser = this.forceLinkThrottleReverser() || disableIndependentThrottle;

        switch (key) {
            case HORN:
                this.setHorn(10, source.getUUID());
                break;
            case BELL:
                if (this.getDefinition().toggleBell) {
                    if (this.bellKeyTimeout == 0) {
                        this.bellTime = this.bellTime != 0 ? 0 : 10;
                        this.bellKeyTimeout = 10;
                    }
                } else {
                    this.setBell(10);
                }
                break;
            case THROTTLE_UP:
                this.setThrottle(this.getThrottle() + throttleDelta);
                break;
            case THROTTLE_ZERO:
                this.setThrottle(0f);
                break;
            case THROTTLE_DOWN:
                this.setThrottle(this.getThrottle() - throttleDelta);
                break;
            case REVERSER_UP:
                if (linkThrottleReverser) {
                    float mixed = this.getThrottle() * (this.getReverser() >= 0 ? 1 : -1);
                    if (mixed < 0) {
                        this.setRealThrottle(-mixed - throttleDelta);
                        this.setReverser(-1);
                    } else {
                        this.setRealThrottle(mixed + throttleDelta);
                        this.setReverser(1);
                    }
                } else {
                    this.setReverser(this.getReverser() + this.getReverserDelta());
                }
                break;
            case REVERSER_ZERO:
                if (linkThrottleReverser) {
                    this.setRealThrottle(0);
                }
                this.setReverser(0f);
                break;
            case REVERSER_DOWN:
                if (linkThrottleReverser) {
                    float mixed = this.getThrottle() * (this.getReverser() >= 0 ? 1 : -1);
                    if (mixed > 0) {
                        this.setRealThrottle(mixed - throttleDelta);
                        this.setReverser(1);
                    } else {
                        this.setRealThrottle(-mixed + throttleDelta);
                        this.setReverser(-1);
                    }
                } else {
                    this.setReverser(this.getReverser() - this.getReverserDelta());
                }
                break;
            case TRAIN_BRAKE_UP:
                this.setTrainBrake(this.getTrainBrake() + trainBrakeNotch);
                break;
            case TRAIN_BRAKE_ZERO:
                this.setTrainBrake(0f);
                break;
            case TRAIN_BRAKE_DOWN:
                this.setTrainBrake(this.getTrainBrake() - trainBrakeNotch);
                break;
            case DEAD_MANS_SWITCH:
                if (this.deadManChangeTimeout == 0) {
                    this.deadMansSwitch = !this.deadMansSwitch;
                    if (this.deadMansSwitch) {
                        source.sendMessage(ChatText.DEADMANS_SWITCH_ENABLED.getMessage());
                    } else {
                        source.sendMessage(ChatText.DEADMANS_SWITCH_DISABLED.getMessage());
                    }
                    this.deadManChangeTimeout = 5;
                }
                break;
            default:
                super.handleKeyPress(source, key, disableIndependentThrottle);
        }
    }

    @Override
    public void setIndependentBrake(float newIndependentBrake) {
        this.setRealIndependentBrake(newIndependentBrake);
        if (this.getDefinition().muliUnitCapable) {
            this.mapTrain(this, true, false, this::copySettings);
        }
    }

    @Override
    public double getBrakeSystemEfficiency() {
        if (this.cogging) {
            return 10;
        }
        return super.getBrakeSystemEfficiency();
    }

    @Override
    public double getBrakeAdhesionEfficiency() {
        if (this.cogging) {
            return 10;
        }
        return super.getBrakeAdhesionEfficiency();
    }

    @Override
    public boolean playerCanDrag(Player player, Control<?> control) {
        if (!super.playerCanDrag(player, control)) {
            return false;
        }
        switch (control.part.type) {
            case THROTTLE_X:
            case REVERSER_X:
            case TRAIN_BRAKE_X:
            case INDEPENDENT_BRAKE_X:
            case THROTTLE_BRAKE_X:
            case BELL_CONTROL_X:
            case WHISTLE_CONTROL_X:
            case HORN_CONTROL_X:
            case ENGINE_START_X:
                return player.hasPermission(Permissions.LOCOMOTIVE_CONTROL);
            default:
                return true;
        }
    }

    public ClickResult onClick(Player player, Player.Hand hand) {
        if (player.getHeldItem(hand)
                .is(IRItems.ITEM_RADIO_CONTROL_CARD) && player.hasPermission(Permissions.LOCOMOTIVE_CONTROL)) {
            if (this.getWorld().isClient) {
                return ClickResult.ACCEPTED;
            }
            if (this.gauge.isModel() || this.getDefinition()
                    .getRadioCapability() || !Config.ConfigBalance.RadioEquipmentRequired) {
                ItemRadioCtrlCard.Data data = new ItemRadioCtrlCard.Data(player.getHeldItem(hand));
                if (player.isCrouching()) {
                    player.sendMessage(data.linked == null ? ChatText.RADIO_NOLINK.getMessage() : ChatText.RADIO_UNLINK.getMessage());
                    data.linked = null;
                } else {
                    player.sendMessage(data.linked == null ? ChatText.RADIO_LINK.getMessage() : ChatText.RADIO_RELINK.getMessage());
                    data.linked = this.getUUID();
                }
                data.write();
            } else {
                player.sendMessage(ChatText.RADIO_CANT_LINK.getMessage(this.getDefinition().name()));
            }
            return ClickResult.ACCEPTED;
        }
        return super.onClick(player, hand);
    }

    @Override
    public LocomotiveDefinition getDefinition() {
        return super.getDefinition(LocomotiveDefinition.class);
    }

    @Override
    public boolean canFitPassenger(Entity passenger) {
        if (passenger instanceof Player && !((Player) passenger).hasPermission(Permissions.BOARD_LOCOMOTIVE)) {
            return false;
        }
        return super.canFitPassenger(passenger);
    }

    protected double simulateWheelSlip() {
        if (this.cogging) {
            return 0;
        }

        double adhesionFactor = Math.abs(this.getAppliedTractiveEffort(this.getCurrentSpeed())) /
                this.getStaticTractiveEffort(this.getCurrentSpeed());
        this.slipping = adhesionFactor > 1;
        if (this.slipping) {
            return Math.copySign((adhesionFactor - 1) / 5, this.getReverser());
        }
        return 0;
    }

    public double getTractiveEffortNewtons(Speed speed) {
        if (!this.isBuilt()) {
            return 0;
        }

        if (Math.abs(speed.minecraft()) > this.getDefinition().getMaxSpeed(this.gauge).minecraft()) {
            return 0;
        }

        double appliedTractiveEffort = this.getAppliedTractiveEffort(speed);

        if (!this.cogging && Math.abs(appliedTractiveEffort) > 0) {
            double staticTractiveEffort = this.getStaticTractiveEffort(speed);

            if (Math.abs(appliedTractiveEffort) > staticTractiveEffort) {
                // This is a guess, but seems to be fairly accurate

                // Reduce tractive effort to max static translated into kinetic
                double tractiveEffortNewtons = staticTractiveEffort /
                        STEEL.staticFriction(STEEL) *
                        STEEL.kineticFriction(STEEL);

                // How badly tractive effort is overwhelming static effort
                tractiveEffortNewtons *= staticTractiveEffort / tractiveEffortNewtons;

                return Math.copySign(tractiveEffortNewtons, appliedTractiveEffort);
            }
        }

        return appliedTractiveEffort;
    }

    /**
     * Force applied between the wheels and the rails
     */
    public abstract double getAppliedTractiveEffort(Speed speed);
    /*
     *
     * Misc Helper functions
     */

    /**
     * Maximum force that can be between the wheels and the rails before it slips
     */
    private double getStaticTractiveEffort(Speed speed) {
        return (Config.ConfigBalance.FuelRequired ? this.getWeight() : this.getMaxWeight()) // KG
                * 9.8 // M/S/S
                * (this.slipping ? STEEL.kineticFriction(STEEL) : STEEL.staticFriction(STEEL))
                * this.slipCoefficient(speed)
                / this.getDefinition().factorOfAdhesion()
                * Config.ConfigBalance.tractionMultiplier;
    }

    public double slipCoefficient(Speed speed) {
        double slipMult = 1.0;
        World world = this.getWorld();
        if (world.isPrecipitating() && world.canSeeSky(this.getBlockPosition())) {
            if (world.isRaining(this.getBlockPosition())) {
                slipMult = 0.6;
            }
            if (world.isSnowing(this.getBlockPosition())) {
                slipMult = 0.4;
            }
        }
        // Wheel balance messing with friction
        if (speed.metric() != 0) {
            double balance = 1d / (Math.abs(speed.metric()) + 300) / (1d / 300);
            slipMult *= balance;
        }
        return slipMult;
    }

    public void setHorn(int val, UUID uuid) {
        if (uuid == null) {
            // Legacy API
            this.hornPull = 1;
        }

        if (this.hornPlayer == null && uuid != null) {
            this.hornPlayer = uuid;
        }
        if (this.hornPlayer == null || this.hornPlayer.equals(uuid)) {
            this.hornTime = val;
        }
    }

    public void setHorn(int time, float value) {
        this.hornTime = time;
        this.hornPull = value;
    }

    public int getHornTime() {
        return this.hornTime;
    }

    public float getHornPull() {
        if (this.getHornPlayer() != null) {
            return (this.getHornPlayer().getRotationPitch() + 90) / 180;
        }
        double control = this.getDefinition().getModel().getControls().stream()
                .filter(x -> x.part.type == ModelComponentType.WHISTLE_CONTROL_X)
                .mapToDouble(this::getControlPosition)
                .max().orElse(0);

        return Math.max((float) control, this.hornPull);
    }

    public Entity getHornPlayer() {
        for (Entity pass : this.getPassengers()) {
            if (pass.getUUID().equals(this.hornPlayer)) {
                return pass;
            }
        }
        return null;
    }

    @Deprecated
    public float getAirBrake() {
        return this.getTrainBrake();
    }

    public float getTrainBrake() {
        return this.trainBrake;
    }

    public void setTrainBrake(float newTrainBrake) {
        this.setRealTrainBrake(newTrainBrake);
        if (this.getDefinition().muliUnitCapable) {
            this.mapTrain(this, true, false, this::copySettings);
        }
    }

    @Deprecated
    public void setAirBrake(float value) {
        this.setTrainBrake(value);
    }

    private void setRealTrainBrake(float newTrainBrake) {
        newTrainBrake = Math.min(1, Math.max(0, newTrainBrake));
        if (this.getTrainBrake() != newTrainBrake) {
            if (this.getDefinition().isLinearBrakeControl()) {
                this.setControlPositions(ModelComponentType.TRAIN_BRAKE_X, newTrainBrake);
            }
            this.trainBrake = newTrainBrake;
            this.setControlPositions(ModelComponentType.THROTTLE_BRAKE_X, this.getThrottle() / 2 + (1 - this.getTrainBrake()) / 2);
        }
    }

    private void copySettings(EntityRollingStock stock, boolean direction) {
        if (stock instanceof Locomotive && ((Locomotive) stock).getDefinition().muliUnitCapable) {
            ((Locomotive) stock).setRealThrottle(this.getThrottle());
            ((Locomotive) stock).setRealReverser(this.getReverser() * (direction ? 1 : -1));
            ((Locomotive) stock).setRealTrainBrake(this.getTrainBrake());
            ((Locomotive) stock).setRealIndependentBrake(this.getIndependentBrake());
        }
    }

    public float getThrottle() {
        return this.throttle;
    }

    public void setThrottle(float newThrottle) {
        this.setRealThrottle(newThrottle);
        if (this.getDefinition().muliUnitCapable) {
            this.mapTrain(this, true, false, this::copySettings);
        }
    }

    private void setRealThrottle(float newThrottle) {
        newThrottle = Math.min(1, Math.max(0, newThrottle));
        if (this.getThrottle() != newThrottle) {
            this.setControlPositions(ModelComponentType.THROTTLE_X, newThrottle);
            this.throttle = newThrottle;
            this.setControlPositions(ModelComponentType.THROTTLE_BRAKE_X, this.getThrottle() / 2 + (1 - this.getTrainBrake()) / 2);
        }
    }

    private void setRealReverser(float newReverser) {
        newReverser = Math.min(1, Math.max(-1, newReverser));

        if (this.getReverser() != newReverser) {
            this.setControlPositions(ModelComponentType.REVERSER_X, newReverser / -2 + 0.5f);
            this.reverser = newReverser;
        }
    }

    public float getReverser() {
        return this.reverser;
    }

    public void setReverser(float newReverser) {
        this.setRealReverser(newReverser);
        if (this.getDefinition().muliUnitCapable) {
            this.mapTrain(this, true, false, this::copySettings);
        }
    }

    private void setRealIndependentBrake(float newIndependentBrake) {
        super.setIndependentBrake(newIndependentBrake);
    }

    public int getBell() {
        return this.bellTime;
    }

    public void setBell(int newBell) {
        this.bellTime = newBell;
    }

    @Override
    public boolean hasElectricalPower() {
        return super.hasElectricalPower() || this.providesElectricalPower();
    }

    public abstract boolean providesElectricalPower();

    public float ambientTemperature() {
        // null during registration
        return this.internal != null ? this.getWorld().getTemperature(this.getBlockPosition()) : 0f;
    }
}

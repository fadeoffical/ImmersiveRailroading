package cam72cam.immersiverailroading.model;

import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.gui.overlay.Readouts;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.library.ValveGearConfig;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.model.part.*;
import cam72cam.immersiverailroading.model.part.TrackFollower.TrackFollowers;
import cam72cam.immersiverailroading.registry.LocomotiveDefinition;
import util.Matrix4;

import java.util.List;

public class LocomotiveModel<ENTITY extends Locomotive, DEFINITION extends LocomotiveDefinition> extends FreightTankModel<ENTITY, DEFINITION> {
    private final TrackFollowers frontTrackers;
    private final TrackFollowers rearTrackers;
    protected DrivingAssembly drivingWheels;
    protected DrivingAssembly drivingWheelsFront;
    protected DrivingAssembly drivingWheelsRear;
    protected ModelState frontLocomotive;
    protected ModelState frontLocomotiveRocking;
    protected ModelState rearLocomotive;
    protected ModelState rearLocomotiveRocking;
    private List<ModelComponent> components;
    private Bell bell;
    private ModelComponent frameFront;
    private ModelComponent frameRear;
    private CargoFill cargoFillFront;
    private CargoFill cargoFillRear;
    private ModelComponent shellFront;
    private ModelComponent shellRear;

    public LocomotiveModel(DEFINITION def) throws Exception {
        super(def);
        this.frontTrackers = new TrackFollowers(s -> new TrackFollower(s, this.frameFront, this.drivingWheelsFront != null ? this.drivingWheelsFront.wheels : null, true));
        this.rearTrackers = new TrackFollowers(s -> new TrackFollower(s, this.frameRear, this.drivingWheelsRear != null ? this.drivingWheelsRear.wheels : null, false));
    }

    @Override
    protected void initStates() {
        super.initStates();
        this.frontLocomotive = this.base.push(settings -> settings.animator(this::getFrontLocomotiveMatrix));
        this.frontLocomotiveRocking = this.addRoll(this.frontLocomotive);
        this.rearLocomotive = this.base.push(settings -> settings.animator(this::getRearLocomotiveMatrix));
        this.rearLocomotiveRocking = this.addRoll(this.rearLocomotive);
    }

    @Override
    protected void parseControllable(ComponentProvider provider, DEFINITION def) {
        this.gauges.addAll(Readout.getReadouts(provider, this.frontLocomotiveRocking, ModelComponentType.COUPLED_X, ModelPosition.FRONT_LOCOMOTIVE, Readouts.COUPLED_FRONT));
        this.gauges.addAll(Readout.getReadouts(provider, this.rearLocomotiveRocking, ModelComponentType.COUPLED_X, ModelPosition.REAR_LOCOMOTIVE, Readouts.COUPLED_REAR));

        super.parseControllable(provider, def);

        this.addGauge(provider, ModelComponentType.GAUGE_SPEED_X, Readouts.SPEED);
        this.addGauge(provider, ModelComponentType.GAUGE_THROTTLE_X, Readouts.THROTTLE);
        this.addGauge(provider, ModelComponentType.GAUGE_REVERSER_X, Readouts.REVERSER);
        this.addGauge(provider, ModelComponentType.GAUGE_TRAIN_BRAKE_X, Readouts.TRAIN_BRAKE);
        if (def.hasIndependentBrake()) {
            this.addGauge(provider, ModelComponentType.GAUGE_INDEPENDENT_BRAKE_X, Readouts.INDEPENDENT_BRAKE);
        }

        this.addControl(provider, ModelComponentType.BELL_CONTROL_X);
        this.addControl(provider, ModelComponentType.THROTTLE_BRAKE_X);
        this.addControl(provider, ModelComponentType.THROTTLE_X);
        this.addControl(provider, ModelComponentType.REVERSER_X);
        this.addControl(provider, ModelComponentType.TRAIN_BRAKE_X);
        if (def.hasIndependentBrake()) {
            this.addControl(provider, ModelComponentType.INDEPENDENT_BRAKE_X);
        }
    }

    @Override
    protected void addControl(ComponentProvider provider, ModelComponentType type) {
        this.controls.addAll(Control.get(provider, this.frontLocomotiveRocking, type, ModelPosition.FRONT_LOCOMOTIVE));
        this.controls.addAll(Control.get(provider, this.rearLocomotiveRocking, type, ModelPosition.REAR_LOCOMOTIVE));
        super.addControl(provider, type);
    }

    @Override
    protected void addGauge(ComponentProvider provider, ModelComponentType type, Readouts value) {
        this.gauges.addAll(Readout.getReadouts(provider, this.frontLocomotiveRocking, type, ModelPosition.FRONT_LOCOMOTIVE, value));
        this.gauges.addAll(Readout.getReadouts(provider, this.rearLocomotiveRocking, type, ModelPosition.REAR_LOCOMOTIVE, value));
        super.addGauge(provider, type, value);
    }

    @Override
    protected void addDoor(ComponentProvider provider) {
        this.doors.addAll(Door.get(provider, this.frontLocomotiveRocking, ModelPosition.FRONT_LOCOMOTIVE));
        this.doors.addAll(Door.get(provider, this.rearLocomotiveRocking, ModelPosition.REAR_LOCOMOTIVE));
        super.addDoor(provider);
    }

    @Override
    protected void addHeadlight(DEFINITION def, ComponentProvider provider, ModelComponentType type) {
        this.headlights.addAll(LightFlare.get(def, provider, this.frontLocomotiveRocking, type, ModelPosition.FRONT_LOCOMOTIVE));
        this.headlights.addAll(LightFlare.get(def, provider, this.rearLocomotiveRocking, type, ModelPosition.REAR_LOCOMOTIVE));
        this.headlights.addAll(LightFlare.get(def, provider, this.frontLocomotiveRocking, type, ModelPosition.FRONT));
        this.headlights.addAll(LightFlare.get(def, provider, this.rearLocomotiveRocking, type, ModelPosition.REAR));
        super.addHeadlight(def, provider, type);
    }

    // TODO rename to tick
    @Override
    protected void effects(ENTITY stock) {
        super.effects(stock);
        this.bell.effects(stock, stock.getBell() > 0 ? 0.8f : 0);
    }

    @Override
    protected void removed(ENTITY stock) {
        super.removed(stock);

        if (this.frontTrackers != null) {
            this.frontTrackers.remove(stock);
        }
        if (this.rearTrackers != null) {
            this.rearTrackers.remove(stock);
        }

        this.bell.removed(stock);
    }

    private Matrix4 getFrontLocomotiveMatrix(EntityMovableRollingStock s) {
        return this.frontTrackers.get(s).getMatrix();
    }

    private Matrix4 getRearLocomotiveMatrix(EntityMovableRollingStock s) {
        return this.rearTrackers.get(s).getMatrix();
    }

    @Override
    protected void parseComponents(ComponentProvider provider, DEFINITION def) {
        ValveGearConfig type = def.getValveGear();
        boolean showCurrentLoadOnly = def.shouldShowCurrentLoadOnly();

        this.frameFront = provider.parse(ModelComponentType.FRONT_FRAME);
        this.shellFront = provider.parse(ModelComponentType.FRONT_SHELL);
        this.frontLocomotiveRocking.include(this.frameFront);
        this.frontLocomotiveRocking.include(this.shellFront);

        this.cargoFillFront = CargoFill.get(provider, this.frontLocomotiveRocking, showCurrentLoadOnly, ModelPosition.FRONT);
        this.drivingWheelsFront = DrivingAssembly.get(type, provider, this.frontLocomotive, ModelPosition.FRONT, 0);


        this.frameRear = provider.parse(ModelComponentType.REAR_FRAME);
        this.shellRear = provider.parse(ModelComponentType.REAR_SHELL);
        this.rearLocomotiveRocking.include(this.frameRear);
        this.rearLocomotiveRocking.include(this.shellRear);

        this.cargoFillRear = CargoFill.get(provider, this.rearLocomotiveRocking, showCurrentLoadOnly, ModelPosition.REAR);
        this.drivingWheelsRear = DrivingAssembly.get(type, provider, this.rearLocomotive, ModelPosition.REAR, 45);

        this.drivingWheels = DrivingAssembly.get(type, provider, this.base, 0,
                this.frame != null ? this.frame.wheels : null,
                this.bogeyFront != null ? this.bogeyFront.wheels : null,
                this.bogeyRear != null ? this.bogeyRear.wheels : null
        );

        this.components = provider.parse(
                new ModelComponentType[]{ModelComponentType.CAB}
        );
        this.rocking.include(this.components);
        this.bell = Bell.get(
                provider, this.rocking,
                def.bell);

        super.parseComponents(provider, def);
    }

    public float getFrontLocomotiveYaw(EntityMovableRollingStock s) {
        return this.frontTrackers.get(s).getYawReadout();
    }

    public float getRearLocomotiveYaw(EntityMovableRollingStock s) {
        return this.rearTrackers.get(s).getYawReadout();
    }
}

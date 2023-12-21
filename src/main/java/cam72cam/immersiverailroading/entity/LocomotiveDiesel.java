package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.model.part.Control;
import cam72cam.immersiverailroading.registry.LocomotiveDieselDefinition;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.library.unit.Speed;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.fluid.FluidStack;
import cam72cam.mod.serialization.TagField;

import java.util.List;
import java.util.OptionalDouble;

public class LocomotiveDiesel extends Locomotive {

    public static final double DIESEL_ENGINE_EFFICIENCY = 0.82;
    private float soundThrottle;
    private float internalBurn = 0;
    private int turnOnOffDelay = 0;

    @TagSync
    @TagField("ENGINE_TEMPERATURE")
    private float engineTemperature;

    @TagSync
    @TagField("TURNED_ON")
    private boolean turnedOn = false;

    @TagSync
    @TagField("ENGINE_OVERHEATED")
    private boolean engineOverheated = false;

    private int throttleCooldown;
    private int reverserCooldown;

    public LocomotiveDiesel() {
        this.engineTemperature = this.ambientTemperature();
    }

    @Override
    public int getInventoryWidth() {
        return this.getDefinition().isCabCar() ? 0 : 2;
    }

    @Override
    public boolean openGui(Player player) {
        if (!this.getDefinition().isCabCar() && player.hasPermission(Permissions.LOCOMOTIVE_CONTROL)) {
            GuiTypes.DIESEL_LOCOMOTIVE.open(player, this);
            return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        super.onTick();

        if (this.turnOnOffDelay > 0) {
            this.turnOnOffDelay -= 1;
        }

        if (this.getWorld().isClient) {
            float absThrottle = Math.abs(this.getThrottle());
            if (this.soundThrottle > absThrottle) {
                this.soundThrottle -= Math.min(0.01f, this.soundThrottle - absThrottle);
            } else if (this.soundThrottle < absThrottle) {
                this.soundThrottle += Math.min(0.01f, absThrottle - this.soundThrottle);
            }
            return;
        }

        OptionalDouble control = this.getDefinition()
                .getModel()
                .getControls()
                .stream()
                .filter(x -> x.part.type == ModelComponentType.HORN_CONTROL_X)
                .mapToDouble(this::getControlPosition)
                .max();
        if (control.isPresent() && control.getAsDouble() > 0) {
            this.setHorn(10, this.hornPlayer);
        }

        float engineTemperature = this.getEngineTemperature();
        float heatUpSpeed = 0.0029167f * Config.ConfigBalance.dieselLocoHeatTimeScale / 1.7f;
        float ambientDelta = engineTemperature - this.ambientTemperature();
        float coolDownSpeed = heatUpSpeed * Math.copySign((float) Math.pow(ambientDelta / 130, 2), ambientDelta);

        if (this.throttleCooldown > 0) {
            this.throttleCooldown--;
        }

        if (this.reverserCooldown > 0) {
            this.reverserCooldown--;
        }

        engineTemperature -= coolDownSpeed;

        if (this.getLiquidAmount() > 0 && this.isRunning()) {
            float consumption = Math.abs(this.getThrottle()) + 0.05f;
            float burnTime = BurnUtil.getBurnTime(this.getLiquid());
            if (burnTime == 0) {
                burnTime = 200; //Default to 200 for unregistered liquids
            }
            burnTime *= this.getDefinition().getFuelEfficiency() / 100f;
            burnTime *= (Config.ConfigBalance.locoDieselFuelEfficiency / 100f);

            while (this.internalBurn < 0 && this.getLiquidAmount() > 0) {
                this.internalBurn += burnTime;
                this.tank.drain(new FluidStack(this.tank.getContents().getFluid(), 1), false);
            }

            consumption *= 100;
            consumption *= (float) this.getGauge().scale();

            this.internalBurn -= consumption;

            engineTemperature += heatUpSpeed * (Math.abs(this.getThrottle()) + 0.2f);

            if (engineTemperature > 150) {
                engineTemperature = 150;
                this.setEngineOverheated(true);
            }
        }

        if (engineTemperature < 100 && this.isEngineOverheated()) {
            this.setEngineOverheated(false);
        }

        this.setEngineTemperature(engineTemperature);
    }

    @Override
    protected float getReverserDelta() {
        return 0.51f;
    }

    @Override
    public void onDragRelease(Control<?> component) {
        super.onDragRelease(component);
        if (component.part.type == ModelComponentType.ENGINE_START_X) {
            this.turnedOn = this.getDefinition()
                    .getModel()
                    .getControls()
                    .stream()
                    .filter(c -> c.part.type == ModelComponentType.ENGINE_START_X)
                    .allMatch(c -> this.getControlPosition(c) == 1);
        }
        if (component.part.type == ModelComponentType.REVERSER_X) {
            // Make sure reverser is sync'd
            this.setControlPositions(ModelComponentType.REVERSER_X, this.getReverser() / -2 + 0.5f);
        }
    }

    /*
     * Sets the throttle or brake on all connected diesel locomotives if the throttle or brake has been changed
     */
    @Override
    public void handleKeyPress(Player source, KeyTypes key, boolean disableIndependentThrottle) {
        switch (key) {
            case START_STOP_ENGINE:
                if (this.turnOnOffDelay == 0) {
                    this.turnOnOffDelay = 10;
                    this.setTurnedOn(!this.isTurnedOn());
                }
                break;
            case REVERSER_UP:
            case REVERSER_ZERO:
            case REVERSER_DOWN:
                if (this.reverserCooldown > 0) {
                    return;
                }
                this.reverserCooldown = 3;
                super.handleKeyPress(source, key, disableIndependentThrottle);
                break;
            case THROTTLE_UP:
            case THROTTLE_ZERO:
            case THROTTLE_DOWN:
                if (this.throttleCooldown > 0) {
                    return;
                }
                this.throttleCooldown = 2;
                super.handleKeyPress(source, key, disableIndependentThrottle);
                break;
            default:
                super.handleKeyPress(source, key, disableIndependentThrottle);
        }
    }

    @Override
    public LocomotiveDieselDefinition getDefinition() {
        return super.getDefinition(LocomotiveDieselDefinition.class);
    }

    @Override
    public double getAppliedTractiveEffort(Speed speed) {
        if (!this.isRunning()) return 0;
        if (this.getEngineTemperature() <= 75 && Config.isFuelRequired(this.getGauge())) return 0;

        double maxPowerWatts = this.getDefinition().getHorsePower(this.getGauge()) * 745.7d;
        double speedMetersPerSecond = speed.as(Speed.SpeedUnit.METERS_PER_SECOND).value();
        double maxPowerAtSpeed = maxPowerWatts * DIESEL_ENGINE_EFFICIENCY / Math.max(0.001, speedMetersPerSecond);
        return maxPowerAtSpeed * this.getThrottle() * this.getReverser();
    }

    @Override
    public void setThrottle(float throttle) {
        int notches = this.getDefinition().getThrottleNotches();
        if (throttle > this.getThrottle()) {
            super.setThrottle((float) (Math.ceil(throttle * notches) / notches));
        } else {
            super.setThrottle((float) (Math.floor(throttle * notches) / notches));
        }
    }

    @Override
    public void setReverser(float newReverser) {
        // todo: why round?
        super.setReverser(Math.round(newReverser));

    }

    @Override
    public boolean providesElectricalPower() {
        return this.isRunning();
    }

    public float getEngineTemperature() {
        return this.engineTemperature;
    }

    private void setEngineTemperature(float temp) {
        this.engineTemperature = temp;
    }

    public boolean isRunning() {
        if (!Config.isFuelRequired(this.getGauge())) return this.isTurnedOn();
        return this.isTurnedOn() && !this.isEngineOverheated() && this.getLiquidAmount() > 0;
    }

    public boolean isEngineOverheated() {
        return Config.ConfigBalance.canDieselEnginesOverheat && this.engineOverheated;
    }

    public boolean isTurnedOn() {
        return this.turnedOn;
    }

    public void setTurnedOn(boolean value) {
        this.turnedOn = value;
        this.setControlPositions(ModelComponentType.ENGINE_START_X, this.turnedOn ? 1 : 0);
    }

    private void setEngineOverheated(boolean value) {
        this.engineOverheated = value;
    }

    @Override
    public boolean internalLightsEnabled() {
        return this.isRunning() || super.internalLightsEnabled();
    }

    @Override
    public FluidQuantity getTankCapacity() {
        return this.getDefinition().getFuelCapacity(this.getGauge());
    }

    @Override
    public List<Fluid> getFluidFilter() {
        return BurnUtil.getBurnableFuels();
    }

    @Override
    public void onDissassemble() {
        super.onDissassemble();
        this.setEngineTemperature(this.ambientTemperature());
        this.setEngineOverheated(false);
        this.setTurnedOn(false);
    }

    public float getSoundThrottle() {
        return this.soundThrottle;
    }
}

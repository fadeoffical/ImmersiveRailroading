package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.inventory.SlotFilter;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.model.part.Control;
import cam72cam.immersiverailroading.registry.LocomotiveSteamDefinition;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.util.LiquidUtil;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.fluid.FluidStack;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapper;

import java.util.*;
import java.util.stream.Collectors;

public class LocomotiveSteam extends Locomotive {
    // PSI
    @TagSync
    @TagField("boiler_psi")
    private float boilerPressure = 0;

    // Celsius
    @TagSync
    @TagField("boiler_temperature")
    private float boilerTemperature;

    @TagSync
    @TagField("pressure_valve")
    private boolean pressureValve = false;

    // Map<Slot, TicksToBurn>
    @TagSync
    @TagField(value = "burn_time", mapper = LocomotiveSteam.SlotTagMapper.class)
    private final Map<Integer, Integer> burnTime = new HashMap<>();
    @TagSync
    @TagField(value = "burn_max", mapper = LocomotiveSteam.SlotTagMapper.class)
    private final Map<Integer, Integer> burnMax = new HashMap<>();

    private float drainRemainder;

    public LocomotiveSteam() {
        this.boilerTemperature = this.ambientTemperature();
    }

    @Override
    public boolean openGui(Player player) {
        if (!this.getDefinition().isCabCar() && player.hasPermission(Permissions.LOCOMOTIVE_CONTROL)) {
            GuiTypes.STEAM_LOCOMOTIVE.open(player, this);
            return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        super.onTick();

        if (this.getWorld().isClient) {
            return;
        }

        if (this.getTickCount() < 2) {
            // Prevent explosions
            return;
        }


        OptionalDouble control = this.getDefinition().getModel().getControls().stream()
                .filter(x -> x.part.type == ModelComponentType.WHISTLE_CONTROL_X)
                .mapToDouble(this::getControlPosition)
                .max();
        if (control.isPresent() && control.getAsDouble() > 0) {
            this.setHorn(10, this.hornPlayer);
        }

        if (!this.isBuilt() || this.getDefinition().isCabCar()) {
            return;
        }

        EntityCoupleableRollingStock stock = this;
        CouplerType coupler = this.getDefinition().cab_forward ? CouplerType.FRONT : CouplerType.BACK;
        while (coupler != null && stock.getCoupled(coupler) instanceof Tender) {
            Tender tender = (Tender) stock.getCoupled(coupler);

            // Only drain 10mb at a time from the tender
            int desiredDrain = 10;
            if (this.getTankCapacity().MilliBuckets() - this.getServerLiquidAmount() >= 10) {
                this.theTank.drain(tender.theTank, desiredDrain, false);
            }

            if (this.getTickCount() % 20 == 0 && this.getDefinition().tender_auto_feed) {
                // Top off stacks
                for (int slot = 2; slot < this.cargoItems.getSlotCount(); slot++) {
                    if (BurnUtil.getBurnTime(this.cargoItems.get(slot)) != 0) {
                        for (int tenderSlot = 0; tenderSlot < tender.cargoItems.getSlotCount(); tenderSlot++) {
                            if (this.cargoItems.get(slot).is(tender.cargoItems.get(tenderSlot))) {
                                if (this.cargoItems.get(slot).getLimit() > this.cargoItems.get(slot).getCount()) {
                                    ItemStack extracted = tender.cargoItems.extract(tenderSlot, 1, false);
                                    this.cargoItems.insert(slot, extracted, false);
                                }
                            }
                        }
                    }
                }
            }
            coupler = tender.getCouplerFor(stock);
            if (coupler == null) {
                break;
            }
            coupler = coupler.opposite();
            stock = tender;
        }

        float boilerTemperature = this.getBoilerTemperature();
        float boilerPressure = this.getBoilerPressure();
        float waterLevelMB = this.getLiquidAmount();
        int burningSlots = 0;
        float waterUsed = 0;

        if (boilerPressure < 0) {
            boilerPressure = 0;
        }

        if (this.getLiquidAmount() > 0) {
            for (int slot = 2; slot < this.cargoItems.getSlotCount(); slot++) {
                int remainingTime = this.burnTime.getOrDefault(slot, 0);
                if (remainingTime <= 0) {
                    ItemStack stack = this.cargoItems.get(slot);
                    if (stack.getCount() <= 0 || BurnUtil.getBurnTime(stack) == 0) {
                        continue;
                    }
                    remainingTime = (int) (BurnUtil.getBurnTime(stack) / this.gauge.scale() * (Config.ConfigBalance.locoSteamFuelEfficiency / 100.0));
                    this.burnTime.put(slot, remainingTime);
                    this.burnMax.put(slot, remainingTime);
                    stack.setCount(stack.getCount() - 1);
                    this.cargoItems.set(slot, stack);
                } else {
                    this.burnTime.put(slot, remainingTime - 1);
                }
                burningSlots += 1;
            }
        }

        double energyKCalDeltaTick = 0;

        if (burningSlots != 0 && this.getLiquidAmount() > 0) {
            energyKCalDeltaTick += burningSlots * this.coalEnergyKCalTick();
        }

        // Assume the boiler is a cube...
        double boilerVolume = this.getTankCapacity().Buckets();
        double boilerEdgeM = Math.pow(boilerVolume, 1.0 / 3.0);
        double boilerAreaM = 6 * Math.pow(boilerEdgeM, 2);

        if (boilerTemperature > 0) {
            // Decrease temperature due to heat loss
            // Estimate Kw emitter per m^2: (TdegC/10)^2 / 100
            // TODO consider ambientTemperature
            double radiatedKwHr = Math.pow(boilerTemperature / 10, 2) / 100 * boilerAreaM * 2;
            double radiatedKCalHr = radiatedKwHr * 859.85;
            double radiatedKCalTick = radiatedKCalHr / 60 / 60 / 20 * ConfigBalance.locoHeatTimeScale;
            energyKCalDeltaTick -= radiatedKCalTick / 1000;
        }

        if (energyKCalDeltaTick != 0) {
            // Change temperature
            // 1 KCal raises 1KG water at STP 1 degree
            // 1 KG of water == 1 m^3 of water
            // TODO what happens when we change liters per mb FluidQuantity.FromMillibuckets((int) waterLevelMB).Liters()
            //  +1 prevents div by zero
            boilerTemperature += energyKCalDeltaTick / ((waterLevelMB + 1) / 1000);
        }

        if (boilerTemperature > 100) {
            // Assume linear relationship between temperature and pressure
            float heatTransfer = boilerTemperature - 100;
            boilerPressure += heatTransfer;

            if (this.getPercentLiquidFull() > 25) {
                boilerTemperature -= heatTransfer;
            }

            // Pressure relief valve
            int maxPSI = this.getDefinition().getMaxPSI(this.gauge);
            this.pressureValve = boilerPressure > maxPSI;
            if (boilerPressure > maxPSI) {
                waterUsed += boilerPressure - maxPSI;
                boilerPressure = maxPSI;
            }
        } else {
            if (boilerPressure > 0) {
                // Reduce pressure by needed temperature
                boilerPressure = Math.max(0, boilerPressure - (100 - boilerTemperature));
                boilerTemperature = 100;
            }

            this.pressureValve = false;
        }

        float throttle = this.getThrottle() * Math.abs(this.getReverser());
        if (throttle != 0 && boilerPressure > 0) {
            double burnableSlots = this.cargoItems.getSlotCount() - 2;
            double maxKCalTick = burnableSlots * this.coalEnergyKCalTick();
            double maxPressureTick = maxKCalTick / (this.getTankCapacity().MilliBuckets() / 1000);
            maxPressureTick *= 0.8; // 20% more pressure gen energyCapability to balance heat loss

            float delta = (float) (throttle * maxPressureTick);

            boilerPressure = Math.max(0, boilerPressure - delta);
            waterUsed += delta;
        }

        if (waterUsed != 0) {
            waterUsed *= Config.ConfigBalance.locoWaterUsage;
            waterUsed += this.drainRemainder;
            if (waterUsed > 0 && this.theTank.getContents() != null) {
                this.theTank.drain(new FluidStack(this.theTank.getContents().getFluid(), (int) Math.floor(waterUsed)), false);
                this.drainRemainder = waterUsed % 1;
            }
        }

        this.setBoilerPressure(boilerPressure);
        this.setBoilerTemperature(Math.max(boilerTemperature, this.ambientTemperature()));

        if (boilerPressure > this.getDefinition().getMaxPSI(this.gauge) * 1.1 || (boilerPressure > this.getDefinition()
                .getMaxPSI(this.gauge) * 0.5 && boilerTemperature > 150)) {
            // 10% over max pressure OR
            // Half max pressure and high boiler temperature
            //EXPLODE

            Vec3d pos = this.getPosition();
            if (Config.ConfigDamage.explosionsEnabled) {
                this.createExplosion(pos, boilerPressure / 5, Config.ConfigDamage.explosionEnvDamageEnabled);
            }
            this.getWorld().removeEntity(this);
        }
    }

    @Override
    public void onDrag(Control<?> component, double newValue) {
        super.onDrag(component, newValue);

        if (component.part.type == ModelComponentType.WHISTLE_CONTROL_X) {
            this.setHorn(10, null);
        }
    }

    @Override
    public void onDragRelease(Control<?> component) {
        super.onDragRelease(component);
        if (component.part.type == ModelComponentType.WHISTLE_CONTROL_X) {
            this.setControlPosition(component, 0);
        }
    }

    @Override
    public LocomotiveSteamDefinition getDefinition() {
        return super.getDefinition(LocomotiveSteamDefinition.class);
    }

    @Override
    protected double simulateWheelSlip() {
        return (this.getDefinition().cab_forward ? -1 : 1) * super.simulateWheelSlip();
    }

    @Override
    public double getTractiveEffortNewtons(Speed speed) {
        return (this.getDefinition().cab_forward ? -1 : 1) * super.getTractiveEffortNewtons(speed);
    }

    @Override
    public double getAppliedTractiveEffort(Speed speed) {
        if (this.getDefinition().isCabCar()) {
            return 0;
        }

        //double traction_N = this.getDefinition().getStartingTractionNewtons(gauge);
        double traction_N = this.getDefinition().getHorsePower(this.gauge) * 375 / Math.max(Math.abs(speed.imperial()), 1.0);
        if (Config.isFuelRequired(this.gauge)) {
            traction_N = traction_N / this.getDefinition().getMaxPSI(this.gauge) * this.getBoilerPressure();
        }

        // Cap the max "effective" reverser.  At high speeds having a fully open reverser just damages equipment
        double reverser = this.getReverser();
        double reverserCap = 0.25;
        double maxReverser = 1 - Math.abs(this.getCurrentSpeed().metric()) / this.getDefinition().getMaxSpeed(this.gauge)
                .metric() * reverserCap;

        // This should probably be tuned...
        double multiplier = Math.copySign(Math.abs(Math.pow(this.getThrottle() * Math.min(Math.abs(reverser), maxReverser), 3)), reverser);

        return traction_N * multiplier;
    }

    public float getBoilerPressure() {
        return this.boilerPressure;
    }

    private void setBoilerPressure(float temp) {
        this.boilerPressure = temp;
    }

    @Override
    public boolean providesElectricalPower() {
        return this.getBoilerPressure() > 0 || !ConfigBalance.FuelRequired;
    }

    @Override
    public FluidQuantity getTankCapacity() {
        return this.getDefinition().getTankCapacity(this.gauge);
    }

    public float getBoilerTemperature() {
        return this.boilerTemperature;
    }

    private void setBoilerTemperature(float temp) {
        this.boilerTemperature = temp;
    }

    private double coalEnergyKCalTick() {
        // Coal density = 800 KG/m3 (engineering toolbox)
        double coalEnergyDensity = 30000; // KJ/KG (engineering toolbox)
        double coalEnergyKJ = coalEnergyDensity / 9; // Assume each slot is burning 1/9th of a coal block
        double coalEnergyBTU = coalEnergyKJ * 0.958; // 1 KJ = 0.958 BTU
        double coalEnergyKCal = coalEnergyBTU / (3.968 * 1000); // 3.968 BTU = 1 KCal
        double coalBurnTicks = 1600; // This is a bit of fudge
        return coalEnergyKCal / coalBurnTicks * ConfigBalance.locoHeatTimeScale;
    }

    @Override
    public List<Fluid> getFluidFilter() {
        return LiquidUtil.getWater();
    }

    @Override
    public int getInventorySize() {
        return this.getDefinition().getInventorySize(this.gauge) + 2;
    }

    @Override
    protected void initContainerFilter() {
        this.cargoItems.filter.clear();
        this.cargoItems.filter.put(0, SlotFilter.FLUID_CONTAINER);
        this.cargoItems.filter.put(1, SlotFilter.FLUID_CONTAINER);
        this.cargoItems.defaultFilter = SlotFilter.BURNABLE;
    }

    @Override
    public void onDissassemble() {
        super.onDissassemble();
        this.setBoilerTemperature(this.ambientTemperature());
        this.setBoilerPressure(0);

        for (Integer slot : this.burnTime.keySet()) {
            this.burnTime.put(slot, 0);
        }
    }

    @Override
    protected int[] getContainerInputSlots() {
        return new int[]{0};
    }

    @Override
    protected int[] getContainertOutputSlots() {
        return new int[]{1};
    }

    public Map<Integer, Integer> getBurnTime() {
        return this.burnTime;
    }

    public Map<Integer, Integer> getBurnMax() {
        return this.burnMax;
    }

    @Override
    public boolean internalLightsEnabled() {
        return this.getBoilerPressure() > 0 || !ConfigBalance.FuelRequired || super.internalLightsEnabled();
    }

    @Override
    public int getInventoryWidth() {
        return this.getDefinition().getInventoryWidth(this.gauge);
    }

    public boolean isOverpressure() {
        return this.pressureValve;
    }

    public boolean cylinderDrainsEnabled() {
        // This could be optimized to once-per-tick, but I'm not sure that is necessary
        List<Control<?>> drains = this.getDefinition().getModel()
                .getControls()
                .stream()
                .filter(x -> x.part.type == ModelComponentType.CYLINDER_DRAIN_CONTROL_X)
                .collect(Collectors.toList());
        if (drains.isEmpty()) {
            double csm = Math.abs(this.getCurrentSpeed().metric()) / this.gauge.scale();
            return csm < 20;
        }

        return drains.stream().anyMatch(c -> this.getControlPosition(c) == 1);
    }

    public void setCylinderDrains(boolean enabled) {
        // This could be optimized to once-per-tick, but I'm not sure that is necessary
        List<Control<?>> drains = this.getDefinition().getModel()
                .getControls()
                .stream()
                .filter(x -> x.part.type == ModelComponentType.CYLINDER_DRAIN_CONTROL_X)
                .collect(Collectors.toList());

        for (Control<?> drain : drains) {
            this.setControlPosition(drain, enabled ? 1 : 0);
        }
    }

    private static class SlotTagMapper implements TagMapper<Map<Integer, Integer>> {
        @Override
        public TagAccessor<Map<Integer, Integer>> apply(Class<Map<Integer, Integer>> type, String fieldName, TagField tag) {
            return new TagAccessor<>(
                    (d, o) -> d.setMap(fieldName, o, Objects::toString, i -> new TagCompound().setInteger("val", i)),
                    d -> d.getMap(fieldName, Integer::parseInt, t -> {
                        Integer val = t.getInteger("val");
                        if (val == null) {
                            val = 0;
                        }
                        return val;
                    })
            );
        }
    }
}

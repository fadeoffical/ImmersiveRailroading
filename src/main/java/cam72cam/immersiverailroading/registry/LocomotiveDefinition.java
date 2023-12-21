package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.model.LocomotiveModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.immersiverailroading.library.unit.Speed;
import cam72cam.mod.resource.Identifier;

import java.util.List;

public abstract class LocomotiveDefinition extends FreightDefinition {
    public boolean toggleBell;
    public SoundDefinition bell;
    public boolean muliUnitCapable;
    private String works;
    private double power;
    private double traction;
    private Speed maxSpeed;
    private boolean hasRadioEquipment;
    private boolean isCabCar;
    private boolean isLinkedBrakeThrottle;
    private boolean isCog;
    private double factorOfAdhesion;

    LocomotiveDefinition(Class<? extends EntityRollingStock> type, String defID, DataBlock data) throws Exception {
        super(type, defID, data);
    }

    @Override
    protected Identifier defaultDataLocation() {
        return new Identifier(ImmersiveRailroading.MODID, "rolling_stock/default/locomotive.caml");
    }

    @Override
    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = super.getTooltip(gauge);
        tips.add(GuiText.LOCO_WORKS.translate(this.works));
        if (!this.isCabCar) {
            tips.add(GuiText.LOCO_HORSE_POWER.translate(this.getHorsePower(gauge)));
            tips.add(GuiText.LOCO_TRACTION.translate(this.getStartingTractionNewtons(gauge)));
            tips.add(GuiText.LOCO_MAX_SPEED.translate(this.getMaxSpeed(gauge).as(ConfigGraphics.speedUnit).format() + " " + ConfigGraphics.speedUnit.getDisplayString()));
        }
        return tips;
    }

    public int getHorsePower(Gauge gauge) {
        return (int) Math.ceil(gauge.scale() * this.power);
    }

    /**
     * @return tractive effort in newtons
     */
    public int getStartingTractionNewtons(Gauge gauge) {
        return (int) Math.ceil(gauge.scale() * this.traction * 4.44822);
    }

    public Speed getMaxSpeed(Gauge gauge) {
        double maxSpeed = this.maxSpeed.as(Speed.SpeedUnit.METERS_PER_TICK).value();
        return Speed.fromUnit(gauge.scale() * maxSpeed, Speed.SpeedUnit.METERS_PER_TICK);
    }

    public boolean isLinearBrakeControl() {
        return this.isLinkedBrakeThrottle() || super.isLinearBrakeControl();
    }

    public boolean isLinkedBrakeThrottle() {
        return this.isLinkedBrakeThrottle;
    }

    @Override
    public void loadData(DataBlock data) throws Exception {
        super.loadData(data);

        this.works = data.getValue("works").asString();

        DataBlock properties = data.getBlock("properties");

        this.hasRadioEquipment = properties.getValue("radio_equipped").asBoolean(false);

        this.isCabCar = this.readCabCarFlag(data);
        if (this.isCabCar) {
            this.power = 0;
            this.traction = 0;
            this.maxSpeed = Speed.ZERO;
            this.muliUnitCapable = true;
            this.factorOfAdhesion = 0;
        } else {
            this.power = properties.getValue("horsepower").asInteger() * this.internal_inv_scale;
            this.traction = properties.getValue("tractive_effort_lbf").asInteger() * this.internal_inv_scale;
            this.factorOfAdhesion = properties.getValue("factor_of_adhesion").asDouble(4);
            this.maxSpeed = Speed.fromUnit(properties.getValue("max_speed_kmh").asDouble() * this.internal_inv_scale, Speed.SpeedUnit.KILOMETERS_PER_HOUR);
            this.muliUnitCapable = properties.getValue("multi_unit_capable").asBoolean();
        }
        this.isLinkedBrakeThrottle = properties.getValue("isLinkedBrakeThrottle").asBoolean();
        this.toggleBell = properties.getValue("toggle_bell").asBoolean();
        this.isCog = properties.getValue("cog").asBoolean();
    }

    protected boolean readCabCarFlag(DataBlock data) {
        return data.getBlock("properties").getValue("cab_car").asBoolean(false);
    }

    @Override
    protected StockModel<?, ?> createModel() throws Exception {
        return new LocomotiveModel<>(this);
    }

    public boolean getRadioCapability() {
        return this.hasRadioEquipment;
    }

    public boolean isCabCar() {
        return this.isCabCar;
    }

    public boolean isCog() {
        return this.isCog;
    }

    public double factorOfAdhesion() {
        return this.factorOfAdhesion;
    }
}

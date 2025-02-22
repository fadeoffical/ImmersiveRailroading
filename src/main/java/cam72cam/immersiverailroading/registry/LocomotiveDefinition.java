package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.model.LocomotiveModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.mod.resource.Identifier;

import java.util.List;

public abstract class LocomotiveDefinition extends FreightDefinition {
    public boolean toggleBell;
    public SoundDefinition bell;
    private String works;
    private double power;
    private double traction;
    private Speed maxSpeed;
    private boolean hasRadioEquipment;
    public boolean muliUnitCapable;
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
    public void loadData(DataBlock data) throws Exception {
        super.loadData(data);

        works = data.getValue("works").asString();

        DataBlock properties = data.getBlock("properties");

        hasRadioEquipment = properties.getValue("radio_equipped").asBoolean(false);

        isCabCar = readCabCarFlag(data);
        if (isCabCar) {
            power = 0;
            traction = 0;
            maxSpeed = Speed.ZERO;
            muliUnitCapable = true;
            factorOfAdhesion = 0;
        } else {
            power = properties.getValue("horsepower").asInteger() * internal_inv_scale;
            traction = properties.getValue("tractive_effort_lbf").asInteger() * internal_inv_scale;
            factorOfAdhesion = properties.getValue("factor_of_adhesion").asDouble(4);
            maxSpeed = Speed.fromMetric(properties.getValue("max_speed_kmh").asDouble() * internal_inv_scale);
            muliUnitCapable = properties.getValue("multi_unit_capable").asBoolean();
        }
        isLinkedBrakeThrottle = properties.getValue("isLinkedBrakeThrottle").asBoolean();
        toggleBell = properties.getValue("toggle_bell").asBoolean();
        isCog = properties.getValue("cog").asBoolean();
    }

    protected boolean readCabCarFlag(DataBlock data) {
        return data.getBlock("properties").getValue("cab_car").asBoolean(false);
    }

    @Override
    protected StockModel<?, ?> createModel() throws Exception {
        return new LocomotiveModel<>(this);
    }

    @Override
    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = super.getTooltip(gauge);
        tips.add(GuiText.LOCO_WORKS.toString(this.works));
        if (!isCabCar) {
            tips.add(GuiText.LOCO_HORSE_POWER.toString(this.getHorsePower(gauge)));
            tips.add(GuiText.LOCO_TRACTION.toString(this.getStartingTractionNewtons(gauge)));
            tips.add(GuiText.LOCO_MAX_SPEED.toString(this.getMaxSpeed(gauge).metricString()));
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
        return Speed.fromMinecraft(gauge.scale() * this.maxSpeed.minecraft());
    }

    public boolean getRadioCapability() {
        return this.hasRadioEquipment;
    }

    public boolean isLinearBrakeControl() {
        return isLinkedBrakeThrottle() || super.isLinearBrakeControl();
    }

    public boolean isLinkedBrakeThrottle() {
        return isLinkedBrakeThrottle;
    }

    public boolean isCabCar() {
        return isCabCar;
    }

    public boolean isCog() {
        return isCog;
    }

    public double factorOfAdhesion() {
        return this.factorOfAdhesion;
    }
}

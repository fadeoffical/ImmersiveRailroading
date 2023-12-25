package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.CarTank;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.model.FreightTankModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.resource.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CarTankDefinition extends FreightDefinition {

    private List<Fluid> fluidFilter; // null == no filter
    private double capacity_l;

    public CarTankDefinition(String defID, DataBlock data) throws Exception {
        this(CarTank.class, defID, data);
    }

    CarTankDefinition(Class<? extends CarTank> type, String defID, DataBlock data) throws Exception {
        super(type, defID, data);
    }

    @Override
    protected Identifier defaultDataLocation() {
        return new Identifier(ImmersiveRailroading.MOD_ID, "rolling_stock/default/tank.caml");
    }

    @Override
    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = super.getTooltip(gauge);
        tips.add(GuiText.TANK_CAPACITY_TOOLTIP.translate(this.getTankCapaity(gauge).asBuckets()));
        return tips;
    }

    public FluidQuantity getTankCapaity(Gauge gauge) {
        return FluidQuantity.fromLiters((int) Math.ceil(this.capacity_l * gauge.scale()))
                .min(FluidQuantity.fromBuckets(1))
                .roundBuckets();
    }

    @Override
    public void loadData(DataBlock data) throws Exception {
        super.loadData(data);
        DataBlock tank = data.getBlock("tank");
        this.capacity_l = tank.getValue("capacity_l").asInteger() * this.internal_inv_scale;
        List<DataBlock.Value> whitelist = tank.getValues("whitelist");
        if (whitelist != null) {
            this.fluidFilter = new ArrayList<>();
            for (DataBlock.Value allowed : whitelist) {
                Fluid allowedFluid = Fluid.getFluid(allowed.asString());
                if (allowedFluid == null) {
                    ImmersiveRailroading.warn("Skipping unknown whitelisted fluid: " + allowed);
                    continue;
                }
                this.fluidFilter.add(allowedFluid);
            }
        }
    }

    @Override
    protected StockModel<?, ?> createModel() throws Exception {
        return new FreightTankModel<>(this);
    }

    public List<Fluid> getFluidFilter() {
        return this.fluidFilter;
    }
}

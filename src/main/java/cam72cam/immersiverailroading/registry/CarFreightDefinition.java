package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.CarFreight;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.mod.resource.Identifier;

import java.util.List;
import java.util.stream.Collectors;

public class CarFreightDefinition extends FreightDefinition {

    private double numSlots;
    private double width;
    private List<String> validCargo;

    public CarFreightDefinition(String defID, DataBlock data) throws Exception {
        this(CarFreight.class, defID, data);
    }

    public CarFreightDefinition(Class<? extends CarFreight> cls, String defID, DataBlock data) throws Exception {
        super(cls, defID, data);
    }

    @Override
    protected Identifier defaultDataLocation() {
        return new Identifier(ImmersiveRailroading.MODID, "rolling_stock/default/freight.caml");
    }

    @Override
    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = super.getTooltip(gauge);
        if (this.numSlots > 0) {
            tips.add(GuiText.FREIGHT_CAPACITY_TOOLTIP.translate(this.getInventorySize(gauge)));
        }
        return tips;
    }

    public int getInventorySize(Gauge gauge) {
        return (int) Math.ceil(this.numSlots * gauge.scale());
    }

    @Override
    public boolean acceptsLivestock() {
        return true;
    }

    @Override
    public void loadData(DataBlock data) throws Exception {
        super.loadData(data);
        DataBlock freight = data.getBlock("freight");
        this.numSlots = freight.getValue("slots").asInteger() * this.internal_inv_scale;
        this.width = freight.getValue("width").asInteger() * this.internal_inv_scale;
        List<DataBlock.Value> cargo = freight.getValues("cargo");
        this.validCargo = cargo == null ? null : cargo.stream()
                .map(DataBlock.Value::asString)
                .collect(Collectors.toList());
    }

    public int getInventoryWidth(Gauge gauge) {
        return (int) Math.ceil(this.width * gauge.scale());
    }
}

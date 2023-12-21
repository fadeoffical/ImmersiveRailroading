package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.registry.CarFreightDefinition;

public class CarFreight extends Freight {

    public int getInventoryWidth() {
        return this.getDefinition().getInventoryWidth(this.getGauge());
    }

    @Override
    public int getInventorySize() {
        return this.getDefinition().getInventorySize(this.getGauge());
    }

    @Override
    protected void initContainerFilter() {
        /* no-op */
    }

    @Override
    public CarFreightDefinition getDefinition() {
        return super.getDefinition(CarFreightDefinition.class);
    }

    //TODO filter inventory
}

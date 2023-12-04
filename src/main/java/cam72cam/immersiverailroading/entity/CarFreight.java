package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.registry.CarFreightDefinition;

public class CarFreight extends Freight {
    public int getInventoryWidth() {
        return this.getDefinition().getInventoryWidth(this.gauge);
    }

    @Override
    public int getInventorySize() {
        return this.getDefinition().getInventorySize(this.gauge);
    }

    @Override
    public CarFreightDefinition getDefinition() {
        return super.getDefinition(CarFreightDefinition.class);
    }

    //TODO filter inventory
}

package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.registry.CarTankDefinition;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.fluid.Fluid;

import java.util.List;

public class CarTank extends FreightTank {
    @Override
    public int getInventoryWidth() {
        return 2;
    }

    @Override
    public CarTankDefinition getDefinition() {
        return super.getDefinition(CarTankDefinition.class);
    }

    @Override
    public FluidQuantity getTankCapacity() {
        return this.getDefinition().getTankCapaity(this.gauge);
    }

    @Override
    public List<Fluid> getFluidFilter() {
        return this.getDefinition().getFluidFilter();
    }
}

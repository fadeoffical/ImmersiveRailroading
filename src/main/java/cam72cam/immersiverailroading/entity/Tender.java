package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.inventory.SlotFilter;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.registry.TenderDefinition;
import cam72cam.immersiverailroading.util.LiquidUtil;
import cam72cam.mod.entity.Player;
import cam72cam.mod.fluid.Fluid;

import java.util.List;

public class Tender extends CarTank {

    public int getInventoryWidth() {
        return this.getDefinition().getInventoryWidth(this.getGauge());
    }

    @Override
    public TenderDefinition getDefinition() {
        return super.getDefinition(TenderDefinition.class);
    }

    @Override
    public List<Fluid> getFluidFilter() {
        return LiquidUtil.getWater();
    }

    @Override
    public int getInventorySize() {
        return this.getDefinition().getInventorySize(this.getGauge()) + 2;
    }

    @Override
    protected void initContainerFilter() {
        this.cargoItems.filter.clear();
        this.cargoItems.filter.put(0, SlotFilter.FLUID_CONTAINER);
        this.cargoItems.filter.put(1, SlotFilter.FLUID_CONTAINER);
        this.cargoItems.defaultFilter = SlotFilter.BURNABLE;
    }

    @Override
    public boolean openGui(Player player) {
        if (player.hasPermission(Permissions.LOCOMOTIVE_CONTROL)) {
            GuiTypes.TENDER.open(player, this);
        }
        return true;
    }

    @Override
    protected int[] getContainerOutputSlots() {
        return new int[]{1};
    }
}

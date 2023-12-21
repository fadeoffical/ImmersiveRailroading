package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config.ConfigDebug;
import cam72cam.immersiverailroading.inventory.SlotFilter;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.fluid.FluidTank;
import cam72cam.mod.fluid.ITank;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.serialization.StrictTagMapper;
import cam72cam.mod.serialization.TagField;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nullable;
import java.util.List;

public abstract class FreightTank extends Freight {

    @TagField("tank")
    public FluidTank tank = new FluidTank(null, 0);

    @TagSync
    @TagField("FLUID_AMOUNT")
    private int fluidAmount;

    @TagSync
    @TagField(value = "FLUID_TYPE", mapper = StrictTagMapper.class)
    private String fluidType;

    @Override
    public void onAssemble() {
        super.onAssemble();
        this.tank.setCapacity(this.getTankCapacity().asMillibuckets());
        this.tank.setFilter(this::getFluidFilter);
        this.tank.onChanged(this::handleTankContentChange);
        this.handleTankContentChange();
    }

    public abstract FluidQuantity getTankCapacity();

    public abstract @Nullable List<Fluid> getFluidFilter();

    private void handleTankContentChange() {
        if (this.getWorld().isClient) return;

        this.fluidAmount = this.tank.getContents().getAmount();
        this.fluidType = this.tank.getContents().getFluid() == null ? null : this.tank.getContents().getFluid().ident;
    }

    @Override
    public int getInventorySize() {
        return 2;
    }

    @Override
    protected void initContainerFilter() {
        this.cargoItems.filter.clear();
        this.cargoItems.filter.put(0, SlotFilter.FLUID_CONTAINER);
        this.cargoItems.filter.put(1, SlotFilter.FLUID_CONTAINER);
        this.cargoItems.defaultFilter = SlotFilter.NONE;
    }

    @Override
    public void onDissassemble() {
        super.onDissassemble();
        this.tank.setCapacity(0);
        this.handleTankContentChange();
    }

    @Override
    public boolean openGui(Player player) {
        if (player.hasPermission(Permissions.FREIGHT_INVENTORY)) GuiTypes.TANK.open(player, this);
        return true;
    }

    @Override
    public double getWeight() {
        double fluidLoad = super.getWeight();
        if (this.getLiquid() != null && this.getLiquidAmount() > 0) {
            // intellij is complaining about InTeGeR dIvIsIoN iN fLoAtInG pOiNt CoNtExT; cast to double to shut it up
            fluidLoad += (double) (this.getLiquidAmount() * this.getLiquid().getDensity()) / Fluid.BUCKET_VOLUME;
        }
        return fluidLoad;
    }

    public Fluid getLiquid() {
        return this.fluidType == null ? null : Fluid.getFluid(this.fluidType);
    }

    public int getLiquidAmount() {
        return this.fluidAmount;
    }

    @Override
    public double getMaxWeight() {
        double waterDensity = 1000;
        return super.getMaxWeight() + this.getTankCapacity().asBuckets() * waterDensity;
    }

    /*
     *
     * Freight Overrides
     */

    public int getServerLiquidAmount() {
        return this.tank.getContents().getAmount();
    }

    @Override
    public void onTick() {
        super.onTick();
        this.tickInventory();
    }

    private void tickInventory() {
        if (this.getWorld().isClient) return;
        if (!this.isBuilt()) return;
        if (this.cargoItems.getSlotCount() == 0) return;

        for (int inputSlot : this.getContainerInputSlots()) {
            ItemStack input = this.cargoItems.get(inputSlot);

            final ItemStack[] inputCopy = {input.copy()};
            inputCopy[0].setCount(1);
            ITank inputTank = ITank.getTank(inputCopy[0], (ItemStack stack) -> inputCopy[0] = stack);

            if (inputTank == null) {
                continue;
            }

            // This is kind of funky, but it works
            // WILL BE CALLED RECURSIVELY from onInventoryChanged
            if (input.getCount() == 0) continue;

            // First try to drain the container, if we can't do that we try
            // to fill it

            // todo: this is unreadable
            for (Boolean doFill : new Boolean[]{false, true}) {
                boolean success;
                if (doFill) {
                    success = this.tank.drain(inputTank, this.tank.getCapacity(), true) > 0;
                } else {
                    success = this.tank.fill(inputTank, this.tank.getCapacity(), true) > 0;
                }

                if (success) {
                    // We were able to drain into the container

                    // Can we move it to an output slot?
                    ItemStack out = inputCopy[0].copy();
                    for (Integer slot : this.getContainerOutputSlots()) {
                        if (this.cargoItems.insert(slot, out, true).getCount() != 0) continue;

                        // Move Liquid
                        if (doFill) {
                            this.tank.drain(inputTank, this.tank.getCapacity(), false);
                        } else {
                            this.tank.fill(inputTank, this.tank.getCapacity(), false);
                        }

                        // do not drain liquid if debug mode is enabled
                        if (ConfigDebug.debugInfiniteLiquids) continue;

                        // Decrease input
                        this.cargoItems.extract(inputSlot, 1, false);

                        // Increase output
                        this.cargoItems.insert(slot, out, false);
                        break;
                    }
                }
            }
        }
    }

    private int[] getContainerInputSlots() {
        return new int[]{0};
    }

    protected int[] getContainerOutputSlots() {
        int[] result = new int[this.getInventorySize()];
        for (int i = 0; i < this.getInventorySize(); i++) {
            result[i] = i;
        }

        for (int i : this.getContainerInputSlots()) {
            result = ArrayUtils.removeElement(result, i);
        }

        return result;
    }

    public int getPercentLiquidFull() {
        return this.getLiquidAmount() * 100 / this.getTankCapacity().asMillibuckets();
    }
}

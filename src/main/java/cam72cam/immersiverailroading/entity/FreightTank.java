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
    public FluidTank theTank = new FluidTank(null, 0);

    @TagSync
    @TagField("FLUID_AMOUNT")
    private int fluidAmount = 0;

    @TagSync
    @TagField(value = "FLUID_TYPE", mapper = StrictTagMapper.class)
    private String fluidType = null;

    @Override
    public void onAssemble() {
        super.onAssemble();
        this.theTank.setCapacity(this.getTankCapacity().MilliBuckets());
        this.theTank.setFilter(this::getFluidFilter);
        this.theTank.onChanged(this::onTankContentsChanged);
        this.onTankContentsChanged();
    }

    /*
     *
     * Specifications
     */
    public abstract FluidQuantity getTankCapacity();

    @Nullable
    public abstract List<Fluid> getFluidFilter();

    protected void onTankContentsChanged() {
        if (this.getWorld().isClient) {
            return;
        }

        this.fluidAmount = this.theTank.getContents().getAmount();
        if (this.theTank.getContents().getFluid() == null) {
            this.fluidType = null;
        } else {
            this.fluidType = this.theTank.getContents().getFluid().ident;
        }
    }

    /*
     *
     * Freight Specification Overrides
     */
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
        this.theTank.setCapacity(0);
        this.onTankContentsChanged();
    }

    @Override
    public boolean openGui(Player player) {
        if (player.hasPermission(Permissions.FREIGHT_INVENTORY)) {
            GuiTypes.TANK.open(player, this);
        }
        return true;
    }


    /*
     *
     * Server functions
     *
     */

    @Override
    public double getWeight() {
        double fLoad = super.getWeight();
        if (this.getLiquidAmount() > 0 && this.getLiquid() != null) {
            fLoad += this.getLiquidAmount() * this.getLiquid().getDensity() / Fluid.BUCKET_VOLUME;
        }
        return fLoad;
    }

    public int getLiquidAmount() {
        return this.fluidAmount;
    }

    public Fluid getLiquid() {
        if (this.fluidType == null) {
            return null;
        }
        return Fluid.getFluid(this.fluidType);
    }

    @Override
    public double getMaxWeight() {
        double waterDensity = 1000;
        return super.getMaxWeight() + this.getTankCapacity().Buckets() * waterDensity;
    }

    /*
     *
     * Freight Overrides
     */

    public int getServerLiquidAmount() {
        return this.theTank.getContents().getAmount();
    }

    @Override
    public void onTick() {
        super.onTick();
        this.checkInvent();
    }

    protected void checkInvent() {

        if (this.getWorld().isClient) {
            return;
        }

        if (!this.isBuilt()) {
            return;
        }

        if (this.cargoItems.getSlotCount() == 0) {
            return;
        }

        for (int inputSlot : this.getContainerInputSlots()) {
            ItemStack input = this.cargoItems.get(inputSlot);

            final ItemStack[] inputCopy = {input.copy()};
            inputCopy[0].setCount(1);
            ITank inputTank = ITank.getTank(inputCopy[0], (ItemStack stack) -> inputCopy[0] = stack);

            if (inputTank == null) {
                continue;
            }

            // This is kind of funky but it works
            // WILL BE CALLED RECUSIVELY from onInventoryChanged
            if (input.getCount() > 0) {
                // First try to drain the container, if we can't do that we try
                // to fill it

                for (Boolean doFill : new Boolean[]{false, true}) {
                    boolean success;
                    if (doFill) {
                        success = this.theTank.drain(inputTank, this.theTank.getCapacity(), true) > 0;
                    } else {
                        success = this.theTank.fill(inputTank, this.theTank.getCapacity(), true) > 0;
                    }

                    if (success) {
                        // We were able to drain into the container

                        // Can we move it to an output slot?
                        ItemStack out = inputCopy[0].copy();
                        for (Integer slot : this.getContainertOutputSlots()) {
                            if (this.cargoItems.insert(slot, out, true).getCount() == 0) {
                                // Move Liquid
                                if (doFill) {
                                    this.theTank.drain(inputTank, this.theTank.getCapacity(), false);
                                } else {
                                    this.theTank.fill(inputTank, this.theTank.getCapacity(), false);
                                }
                                if (!ConfigDebug.debugInfiniteLiquids) {
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
            }
        }
    }

    /*
     *
     * ITank Overrides
     *
     */

    protected int[] getContainerInputSlots() {
        return new int[]{0};
    }

    protected int[] getContainertOutputSlots() {
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
        return this.getLiquidAmount() * 100 / this.getTankCapacity().MilliBuckets();
    }
}

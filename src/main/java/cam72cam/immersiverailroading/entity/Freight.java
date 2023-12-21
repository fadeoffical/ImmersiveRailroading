package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.inventory.FilteredStackHandler;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.registry.FreightDefinition;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Living;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.serialization.TagField;

import java.util.List;

public abstract class Freight extends EntityCoupleableRollingStock {

    @TagField("items")
    public FilteredStackHandler cargoItems = new FilteredStackHandler(0);

    @TagSync
    @TagField("CARGO_ITEMS")
    private int itemCount = 0;

    @TagSync
    @TagField("PERCENT_FULL")
    private int percentFull = 0;

    public abstract int getInventoryWidth();

    @Override
    public void onAssemble() {
        super.onAssemble();
        List<ItemStack> extras = this.cargoItems.setSize(this.getInventorySize());
        if (this.getWorld().isServer) {
            extras.forEach(stack -> this.getWorld().dropItem(stack, this.getPosition()));
            this.cargoItems.onChanged(slot -> this.handleMass());
            this.handleMass();
        }
        this.initContainerFilter();
    }

    public abstract int getInventorySize();

    /*
     *
     * EntityRollingStock Overrides
     */

    /**
     * Handle mass depending on item count
     */
    private void handleMass() {
        int itemInsideCount = 0;
        int stacksWithStuff = 0;
        for (int slot = 0; slot < this.cargoItems.getSlotCount(); slot++) {
            itemInsideCount += this.cargoItems.get(slot).getCount();
            if (this.cargoItems.get(slot).getCount() != 0) {
                stacksWithStuff += 1;
            }
        }
        this.itemCount = itemInsideCount;
        this.percentFull = this.getInventorySize() > 0 ? stacksWithStuff * 100 / this.getInventorySize() : 100;
    }

    protected abstract void initContainerFilter();

    @Override
    public void onDissassemble() {
        super.onDissassemble();

        if (this.getWorld().isServer) {
            for (int i = 0; i < this.cargoItems.getSlotCount(); i++) {
                ItemStack stack = this.cargoItems.get(i);
                if (!stack.isEmpty()) {
                    this.getWorld().dropItem(stack.copy(), this.getPosition());
                    stack.setCount(0);
                }
            }
        }
    }

    @Override
    public ClickResult onClick(Player player, Player.Hand hand) {
        ClickResult clickRes = super.onClick(player, hand);
        if (clickRes != ClickResult.PASS) {
            return clickRes;
        }

        if (!this.isBuilt()) {
            return ClickResult.PASS;
        }

        // See ItemLead.attachToFence
        if (this.getDefinition().acceptsLivestock()) {
            List<Living> leashed = this.getWorld().getEntities((Living e) -> e.getPosition()
                    .distanceTo(player.getPosition()) < 16 && e.isLeashedTo(player), Living.class);
            if (this.getWorld().isClient && !leashed.isEmpty()) {
                return ClickResult.ACCEPTED;
            }
            if (player.hasPermission(Permissions.BOARD_WITH_LEAD)) {
                for (Living entity : leashed) {
                    if (this.canFitPassenger(entity)) {
                        entity.unleash(player);
                        this.addPassenger(entity);
                        return ClickResult.ACCEPTED;
                    }
                }
            }

            if (player.getHeldItem(hand).is(Fuzzy.LEAD)) {
                for (Entity passenger : this.getPassengers()) {
                    if (passenger instanceof Living && !passenger.isVillager()) {
                        if (this.getWorld().isServer) {
                            Living living = (Living) passenger;
                            if (living.canBeLeashedTo(player)) {
                                this.removePassenger(living);
                                living.setLeashHolder(player);
                                player.getHeldItem(hand).shrink(1);
                            }
                        }
                        return ClickResult.ACCEPTED;
                    }
                }
            }
        }

        if (player.getHeldItem(hand).isEmpty() || player.getRiding() != this) {
            if (this.getWorld().isClient || this.openGui(player)) {
                return ClickResult.ACCEPTED;
            }
        }
        return ClickResult.PASS;
    }

    @Override
    public FreightDefinition getDefinition() {
        return this.getDefinition(FreightDefinition.class);
    }

    protected boolean openGui(Player player) {
        if (this.getInventorySize() == 0) return false;
        if (player.hasPermission(Permissions.FREIGHT_INVENTORY)) GuiTypes.FREIGHT.open(player, this);
        return true;
    }

    @Override
    public double getWeight() {
        return ConfigBalance.blockWeight * this.itemCount + super.getWeight();
    }

    @Override
    public double getMaxWeight() {
        return ConfigBalance.blockWeight * this.getInventorySize() * 64 + super.getMaxWeight();
    }

    public int getPercentCargoFull() {
        return this.percentFull;
    }
}

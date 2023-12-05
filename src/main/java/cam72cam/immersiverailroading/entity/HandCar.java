package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.fluid.Fluid;

import java.util.ArrayList;
import java.util.List;

public class HandCar extends Locomotive {

    // todo: Should the required hunger level be > 0 or > 6?
    //       The player can't sprint at hunger <= 6.

    /**
     * The hunger level required to be able to 'crank' the handcar.
     */
    private static final int HUNGER_LEVEL_REQUIRED = 1;

    // Similar to a *lot* of imperial references
    private static final double HANDCAR_EFFICIENCY = 0.82;

    @Override
    public void onTick() {
        super.onTick();

        if (this.getWorld().isClient) {
            return;
        }

        if (this.getTrainBrake() > 0) {
            this.setTrainBrake(0);
        }

        if (this.getThrottle() == 0) {
            return;
        }

        if (this.getTickCount() % (int) (600 * (1.1 - this.getThrottle())) != 0) return;

        this.getPassengers()
                .stream()
                .filter(Entity::isPlayer)
                .map(Entity::asPlayer)
                .filter(player -> !player.isCreative())
                .forEach(player -> {
                    if (player.getFoodLevel() >= HUNGER_LEVEL_REQUIRED) {
                        player.useFood(1);
                    }
                });
    }

    @Override
    protected boolean forceLinkThrottleReverser() {
        // Always linked
        return true;
    }

    @Override
    public double getAppliedTractiveEffort(Speed speed) {
        int passengers = this.getPassengers()
                .stream()
                .filter(Entity::isPlayer)
                .map(Entity::asPlayer)
                .filter(player -> player.isCreative() || player.getFoodLevel() >= HUNGER_LEVEL_REQUIRED)
                .mapToInt(player -> 1)
                .sum();

        // Same as diesel for now
        double maxPower_W = this.getDefinition().getHorsePower(this.gauge) * 745.7d * passengers;
        double speed_M_S = Math.abs(speed.metric()) / 3.6d;

        // todo: this formula feels off... as per this, acceleration approaches infinity as speed approaches 0
        double maxPowerAtSpeed = maxPower_W * HANDCAR_EFFICIENCY / Math.max(0.001, speed_M_S);
        return maxPowerAtSpeed * this.getThrottle() * this.getReverser();
    }

    @Override
    public boolean providesElectricalPower() {
        return false;
    }

    @Override
    public FluidQuantity getTankCapacity() {
        return FluidQuantity.ZERO;
    }

    @Override
    public List<Fluid> getFluidFilter() {
        return new ArrayList<>();
    }

    @Override
    public int getInventoryWidth() {
        return 2;
    }
}

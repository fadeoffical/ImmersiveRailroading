package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.library.unit.Speed;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.fluid.Fluid;

import java.util.Collections;
import java.util.List;

public final class HandCar extends Locomotive {

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

        if (this.getWorld().isClient) return;
        if (this.getTrainBrake() > 0) this.setTrainBrake(0);
        if (this.getThrottle() == 0) return;

        if (this.getTickCount() % (int) (600 * (1.1 - this.getThrottle())) != 0) return;

        this.getPassengers()
                .stream()
                .filter(Entity::isPlayer)
                .map(Entity::asPlayer)
                .filter(player -> !player.isCreative())
                .filter(player -> player.getFoodLevel() >= HUNGER_LEVEL_REQUIRED)
                .forEach(player -> player.useFood(1)); // todo: will this use one food per tick?
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
        double maxPowerWatts = this.getDefinition().getHorsePower(this.getGauge()) * 745.7d * passengers;
        double speedMetersPerSecond = speed.as(Speed.SpeedUnit.METERS_PER_SECOND).absolute().value();

        // todo: this formula feels off... as per this, acceleration approaches infinity as speed approaches 0
        double maxPowerAtSpeed = maxPowerWatts * HANDCAR_EFFICIENCY / Math.max(0.001, speedMetersPerSecond);
        return maxPowerAtSpeed * this.getThrottle() * this.getReverser();
    }

    @Override
    protected boolean forceLinkThrottleReverser() {
        return true;
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
        return Collections.emptyList();
    }

    @Override
    public int getInventoryWidth() {
        return 2;
    }
}

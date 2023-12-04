package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.fluid.Fluid;

import java.util.ArrayList;
import java.util.List;

public class HandCar extends Locomotive {

    @Override
    protected boolean forceLinkThrottleReverser() {
        // Always linked
        return true;
    }

    @Override
    public void onTick() {
        super.onTick();

        if (this.getWorld().isClient) {
            return;
        }

        if (this.getTrainBrake() > 0) {
            this.setTrainBrake(0);
        }

        if (this.getThrottle() != 0 && this.getTickCount() % (int) (600 * (1.1 - this.getThrottle())) == 0) {
            for (Entity passenger : this.getPassengers()) {
                if (passenger.isPlayer()) {
                    Player player = passenger.asPlayer();
                    if (!player.isCreative()) {
                        if (player.getFoodLevel() > 0) {
                            player.useFood(1);
                        }
                    }
                }
            }
        }
    }

    @Override
    public double getAppliedTractiveEffort(Speed speed) {
        int passengers = 0;
        for (Entity passenger : this.getPassengers()) {
            if (passenger.isPlayer()) {
                Player player = passenger.asPlayer();
                if (!player.isCreative()) {
                    if (player.getFoodLevel() > 0) {
                        passengers++;
                    }
                } else {
                    passengers++;
                }
            }
        }
        // Same as diesel for now
        double maxPower_W = this.getDefinition().getHorsePower(this.gauge) * 745.7d * passengers;
        double efficiency = 0.82; // Similar to a *lot* of imperial references
        double speed_M_S = (Math.abs(speed.metric()) / 3.6);
        double maxPowerAtSpeed = maxPower_W * efficiency / Math.max(0.001, speed_M_S);
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

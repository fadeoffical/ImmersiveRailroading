package cam72cam.immersiverailroading.library.augment.impl;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.library.augment.Augment;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.util.Facing;
import org.jetbrains.annotations.NotNull;

public final class ControllerAugment extends Augment {

    public static final @NotNull Identifier ID = new Identifier(ImmersiveRailroading.MOD_ID, "controller");

    private @NotNull Mode mode;

    public ControllerAugment() {
        super(ID, Color.LIGHT_BLUE);
        this.mode = Config.ConfigDebug.defaultAugmentComputer ? Mode.COMPUTER : Mode.THROTTLE;
    }

    @Override
    public boolean reactsToRedstone() {
        return false;
    }

    @Override
    public int getRedstoneOutput(@NotNull TileRailBase rail) {
        return 0;
    }

    @Override
    public boolean canApplyAugment(@NotNull TileRailBase rail) {
        return true;
    }

    @Override
    public void update(@NotNull TileRailBase rail) {
        Locomotive locomotive = rail.getStockOverhead(Locomotive.class);
        if (locomotive == null) return;

        int power = rail.getWorld().getRedstone(rail.getPos());

        switch (this.getMode()) {
            case THROTTLE:
                float throttle = power / 15f;
                locomotive.setThrottle(throttle);
                break;
            case REVERSER:
                float reverser = (power / 14f - 0.5f) * 2f;
                locomotive.setReverser(reverser);
                break;
            case BRAKE:
                float brake = power / 15f;
                locomotive.setTrainBrake(brake);
                break;
            case HORN:
                float hornPull = power / 15f;
                locomotive.setHorn(40, hornPull);
                break;
            case BELL:
                int bell = power * 10;
                locomotive.setBell(bell);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onClick(@NotNull TileRailBase rail, @NotNull Player player, Player.@NotNull Hand hand, @NotNull Facing facing, @NotNull Vec3d hit) {
        if (super.onClick(rail, player, hand, facing, hit)) return true;

        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.is(Fuzzy.REDSTONE_DUST) || heldItem.is(Fuzzy.REDSTONE_TORCH)) {
            this.mode = this.mode.next();
            PlayerMessage message = PlayerMessage.translate(this.mode.toString());
            if (rail.getWorld().isServer) player.sendMessage(message);
            return true;
        }

        return false;
    }

    public @NotNull Mode getMode() {
        return this.mode;
    }

    public enum Mode {
        THROTTLE("throttle"),
        REVERSER("reverser"),
        BRAKE("brake"),
        HORN("horn"),
        BELL("bell"),
        COMPUTER("computer");

        private final @NotNull String name;

        Mode(@NotNull String name) {
            this.name = name;
        }

        public @NotNull String getName() {
            return this.name;
        }

        @Override
        public String toString() {
            return String.format("%s:loco_control_mode.%s", ImmersiveRailroading.MOD_ID, this.name);
        }

        private @NotNull Mode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }
}

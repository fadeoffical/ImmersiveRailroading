package cam72cam.immersiverailroading.library.augment;

import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.render.Color;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.util.Facing;
import org.jetbrains.annotations.NotNull;

// todo: merge *Loader and *Unloader augments into one class
//       and have a Push, Pull, and Neutral(?) mode
public abstract class PushPullAugment extends Augment {

    @TagField("PushPull")
    private boolean pushPull;

    public PushPullAugment(@NotNull Identifier id, @NotNull Color color) {
        super(id, color);
        this.pushPull = false;
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
        if (rail.getTicksExisted() % 20 == 0) rail.markDirty();
    }

    @Override
    public boolean onClick(@NotNull TileRailBase rail, @NotNull Player player, Player.@NotNull Hand hand, @NotNull Facing facing, @NotNull Vec3d hit) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.is(Fuzzy.PISTON)) {
            this.pushPull = !this.pushPull;
            PlayerMessage message = PlayerMessage.translate("immersiverailroading:augment.pushpull." + (this.pushPull ? "enabled" : "disabled"));
            if (rail.getWorld().isServer) player.sendMessage(message);
            return true;
        }
        return super.onClick(rail, player, hand, facing, hit);
    }

    protected boolean isPushPull() {
        return this.pushPull;
    }

    public void setPushPull(boolean pushPull) {
        this.pushPull = pushPull;
    }
}

package cam72cam.immersiverailroading.tile;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.items.nbt.RailSettings;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.TrackDirection;
import cam72cam.immersiverailroading.net.PreviewRenderPacket;
import cam72cam.immersiverailroading.track.IIterableTrack;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.PlacementInfo;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.block.BlockEntityTickable;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.util.Facing;

public class TileRailPreview extends BlockEntityTickable {
    private int ticksAlive;
    private RailInfo info;

    @TagField
    private ItemStack item;
    @TagField
    private PlacementInfo placementInfo;
    @TagField
    private PlacementInfo customInfo;
    @TagField
    private boolean isAboveRails = false;

    public void setup(ItemStack stack, PlacementInfo info) {
        this.item = stack.copy();
        this.placementInfo = info;
        this.isAboveRails = BlockUtil.isIRRail(this.getWorld(), this.getPos().down()) && this.getWorld().getBlockEntity(this.getPos().down(), TileRailBase.class)
                .getRailHeight() < 0.5;
        this.markDirty();
    }

    public boolean isMulti() {
        if (this.getRailRenderInfo().getBuilder(this.getWorld()) instanceof IIterableTrack) {
            return ((IIterableTrack) this.getRailRenderInfo().getBuilder(this.getWorld())).getSubBuilders() != null;
        }
        return false;
    }

    public RailInfo getRailRenderInfo() {
        if (this.getWorld() != null && this.item != null && (this.info == null || this.info.settings == null)) {
            this.info = new RailInfo(this.item, this.placementInfo, this.customInfo);
        }
        return this.info;
    }

    public void setItem(ItemStack stack, Player player) {
        this.item = stack.copy();
        RailSettings settings = RailSettings.from(this.item);

        if (settings.direction != TrackDirection.NONE) {
            this.placementInfo = this.placementInfo.withDirection(settings.direction);
        }

        if (!settings.isPreview) {
            if (this.getRailRenderInfo() != null && this.getRailRenderInfo()
                    .build(player, this.isAboveRails() ? this.getPos().down() : this.getPos())) {
                new PreviewRenderPacket(this.getWorld(), this.getPos()).sendToAll();
                if (this.isAboveRails()) {
                    this.getWorld().breakBlock(this.getPos());
                }
                return;
            }
        }
        this.markDirty();
    }

    public boolean isAboveRails() {
        return this.isAboveRails;
    }

    @Override
    public void load(TagCompound nbt) {
        this.info = null;
    }

    @Override
    public boolean onClick(Player player, Player.Hand hand, Facing facing, Vec3d hit) {
        if (player.isCrouching()) {
            if (this.getWorld().isServer) {
                this.setPlacementInfo(new PlacementInfo(this.getItem(), player.getYawHead(), hit));
            }
            return false;
        } else if (!player.getHeldItem(hand).is(IRItems.ITEM_GOLDEN_SPIKE)) {
            GuiTypes.RAIL_PREVIEW.open(player, this.getPos());
            return true;
        }
        return false;
    }

    public void setPlacementInfo(PlacementInfo info) {
        this.placementInfo = info;
        this.markDirty();
    }

    public ItemStack getItem() {
        return this.item;
    }

    @Override
    public ItemStack onPick() {
        if (this.item == null) {
            return ItemStack.EMPTY;
        }
        return this.item;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        this.info = new RailInfo(this.item, this.placementInfo, this.customInfo);
        if (this.isMulti() && this.getWorld().isServer) {
            new PreviewRenderPacket(this).sendToAll();
        }
    }

    @Override
    public boolean tryBreak(Player entityPlayer) {
        if (entityPlayer != null && entityPlayer.isCrouching()) {
            if (this.getRailRenderInfo() != null && this.getRailRenderInfo()
                    .build(entityPlayer, this.isAboveRails() ? this.getPos().down() : this.getPos())) {
                new PreviewRenderPacket(this.getWorld(), this.getPos()).sendToAll();
                return this.isAboveRails();
            }
            return false;
        }
        new PreviewRenderPacket(this.getWorld(), this.getPos()).sendToAll();
        return true;
    }

    @Override
    public IBoundingBox getBoundingBox() {
        // Won't be a lot of these in world, extra allocations are fine
        return IBoundingBox.ORIGIN.expand(new Vec3d(1, 0.125, 1));
    }

    @Override
    public IBoundingBox getRenderBoundingBox() {
        return IBoundingBox.INFINITE;
    }

    public void setCustomInfo(PlacementInfo info) {
        this.customInfo = info;
        if (this.customInfo != null) {
            RailSettings settings = RailSettings.from(this.item);
            double lx = Math.abs(this.customInfo.placementPosition.x - this.placementInfo.placementPosition.x);
            double lz = Math.abs(this.customInfo.placementPosition.z - this.placementInfo.placementPosition.z);
            switch (settings.type) {
                case TURN:
                    settings = settings.with(b -> {
                        double length = (lx + lz) / 2 + 1;
                        length *= 90 / b.degrees;
                        b.length = (int) Math.round(length);
                    });
                    break;
                case STRAIGHT:
                case SLOPE:
                    settings = settings.with(b -> b.length = (int) Math.round(Math.max(lx, lz) + 1));
            }

            settings.write(this.item);
        }
        this.markDirty();
    }

    @Override
    public void update() {
        if (this.getWorld().isServer && this.isMulti()) {
            this.getWorld().keepLoaded(this.getPos());

            if (this.ticksAlive % 20 == 0) {
                new PreviewRenderPacket(this).sendToAll();
            }
            this.ticksAlive++;
        }
    }
}

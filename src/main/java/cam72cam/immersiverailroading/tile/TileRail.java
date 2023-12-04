package cam72cam.immersiverailroading.tile;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.IRBlocks;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.physics.Simulation;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.track.TrackBase;
import cam72cam.immersiverailroading.util.MathUtil;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapper;

import java.util.ArrayList;
import java.util.List;

public class TileRail extends TileRailBase {
    @TagField("info")
    public RailInfo info;

    @TagField("tableIndex")
    private int tableIndex;

    @TagField(value = "drops", typeHint = ItemStack.class, mapper = DropsMapper.class)
    private List<ItemStack> drops;

    private IBoundingBox boundingBox;
    private List<TrackBase> tracks;

    @Override
    public IBoundingBox getRenderBoundingBox() {
        if (this.info == null) {
            return IBoundingBox.ORIGIN;
        }
        if (this.boundingBox == null) {
            int length = this.info.settings.length;
            if (this.info.settings.type == TrackItems.CUSTOM && !this.info.customInfo.placementPosition.equals(this.info.placementInfo.placementPosition)) {
                length = (int) this.info.customInfo.placementPosition.distanceTo(this.info.placementInfo.placementPosition);
            }
            this.boundingBox = IBoundingBox.ORIGIN.grow(new Vec3d(length, length, length));
        }
        return this.boundingBox;
    }

    @Override
    public double getRenderDistance() {
        return 8 * 32;
    }

    public void setSwitchState(SwitchState state) {
        if (state != this.info.switchState) {
            this.info = this.info.with(b -> b.switchState = state);
            this.markDirty();
        }
    }

    public void setTablePosition(float angle) {

        angle = ((angle % 360) + 360) % 360;
        int slotsPerCircle = Config.ConfigBalance.AnglePlacementSegmentation * 4;
        // 0 -> slotsPerCircle
        int dest = Math.round((angle / (360f / slotsPerCircle)));

        // This is probably stupidly overcomplicated, but it works...
        int delta = MathUtil.deltaMod(this.tableIndex, dest, slotsPerCircle);
        int deltaOpp = MathUtil.deltaMod(this.tableIndex + slotsPerCircle / 2, dest, slotsPerCircle);
        if (Math.abs(MathUtil.deltaMod(0, delta + slotsPerCircle, slotsPerCircle)) < Math.abs(MathUtil.deltaMod(0, deltaOpp + slotsPerCircle, slotsPerCircle))) {
            dest = (this.tableIndex + delta) % slotsPerCircle;
        } else {
            dest = (this.tableIndex + deltaOpp) % slotsPerCircle;
        }

        // Force 180
        if (dest == this.tableIndex) {
            dest += slotsPerCircle / 2;
        }

        // Normalize
        dest = (dest + slotsPerCircle) % slotsPerCircle;

        this.tableIndex = dest;
    }

    public List<ItemStack> getDrops() {
        return this.drops;
    }

    public void setDrops(List<ItemStack> drops) {
        this.drops = drops;
    }

    public void markAllDirty() {
        if (this.info.settings == null) {
            return;
        }
        this.percentFloating(); // initialize track cache

        for (TrackBase track : this.tracks) {
            Vec3i tpos = track.getPos();
            TileRailBase be = this.getWorld().getBlockEntity(tpos, TileRailBase.class);
            if (be != null) {
                be.railBedCache = this.info.settings.railBed;
                be.cachedGauge = this.info.settings.gauge.value();
                be.markDirty();
            }
        }
    }

    public double percentFloating() {
        int floating = 0;
        int total = 0;

        if (this.info.settings == null) {
            return 0;
        }

        if (this.tracks == null) {
            this.tracks = (this.info.settings.type == TrackItems.SWITCH ? this.info.withSettings(b -> b.type = TrackItems.STRAIGHT) : this.info).getBuilder(this.getWorld(), new Vec3i(this.info.placementInfo.placementPosition).add(this.getPos()))
                    .getTracksForFloating();
            // This is just terrible
            Vec3i offset = this.getPos().subtract(this.tracks.get(0).getPos());
            this.tracks = (this.info.settings.type == TrackItems.SWITCH ? this.info.withSettings(b -> b.type = TrackItems.STRAIGHT) : this.info).getBuilder(this.getWorld(), new Vec3i(this.info.placementInfo.placementPosition).add(this.getPos().add(offset)))
                    .getTracksForFloating();
        }


        for (TrackBase track : this.tracks) {
            Vec3i tpos = track.getPos();
            total++;

            if (!this.getWorld().isBlockLoaded(tpos) || !((this.getWorld().isBlock(tpos, IRBlocks.BLOCK_RAIL) || this.getWorld().isBlock(tpos, IRBlocks.BLOCK_RAIL_GAG)))) {
                return 0;
            }
            if (!track.isDownSolid(false)) {
                floating++;
            }
        }
        return floating / (double) total;
    }

    @Override
    public ItemStack getRenderRailBed() {
        if (this.info == null) {
            return null;
        }
        return this.info.settings.railBed;
    }

    @Override
    public double getTrackGauge() {
        if (this.info == null) {
            return 0;
        }
        return this.info.settings.gauge.value();
    }

    @Override
    public void update() {
        super.update();

        if (this.getWorld().isServer && this.info != null && this.info.settings.type == TrackItems.TURNTABLE) {
            int slotsPerCircle = Config.ConfigBalance.AnglePlacementSegmentation * 4;
            float desiredPosition = (360f / slotsPerCircle) * this.tableIndex;
            double speed = Config.ConfigBalance.TurnTableSpeed;
            if (desiredPosition != this.info.tablePos) {
                if (Math.abs(desiredPosition - this.info.tablePos) < speed) {
                    this.info = this.info.with(b -> b.tablePos = desiredPosition);
                } else {
                    // Again, this math is horrific and is probably wayyyyy overcomplicated
                    double dp = MathUtil.deltaAngle(this.info.tablePos + speed, desiredPosition);
                    double dn = MathUtil.deltaAngle(this.info.tablePos - speed, desiredPosition);
                    dp = MathUtil.deltaAngle(0, dp + 360);
                    dn = MathUtil.deltaAngle(0, dn + 360);
                    double delta = Math.abs(dp) <
                            Math.abs(dn) ?
                            speed : -speed;
                    this.info = this.info.with(b -> b.tablePos = (((b.tablePos + delta) % 360) + 360) % 360);
                }
                this.markDirty();
                List<EntityCoupleableRollingStock> ents = this.getWorld().getEntities((EntityCoupleableRollingStock stock) -> stock.getPosition()
                        .distanceTo(new Vec3d(this.getPos())) < this.info.settings.length, EntityCoupleableRollingStock.class);
                for (EntityCoupleableRollingStock stock : ents) {
                    stock.states.forEach(state -> state.dirty = true);
                    Simulation.forceQuickUpdates = true;
                }
            }
        }
    }

    @Override
    public void onBreak() {
        this.spawnDrops();
        super.onBreak();
    }

    public void spawnDrops() {
        this.spawnDrops(new Vec3d(this.getPos()));
    }

    public void spawnDrops(Vec3d pos) {
        if (this.getWorld().isServer) {
            if (this.drops != null && this.drops.size() != 0) {
                for (ItemStack drop : this.drops) {
                    this.getWorld().dropItem(drop, pos);
                }
                this.drops = new ArrayList<>();
            }
        }
    }

    @Override
    public boolean clacks() {
        if (this.info == null) {
            return false;
        }
        return DefinitionManager.getTrack(this.info.settings.track).clack;
    }

    @Override
    public float getBumpiness() {
        if (this.info == null) {
            return 1;
        }
        return DefinitionManager.getTrack(this.info.settings.track).bumpiness;
    }

    @Override
    public boolean isCog() {
        if (this.info == null) {
            return false;
        }
        return DefinitionManager.getTrack(this.info.settings.track).cog;
    }

    private static class DropsMapper implements TagMapper<List<ItemStack>> {
        @Override
        public TagAccessor<List<ItemStack>> apply(Class<List<ItemStack>> type, String fieldName, TagField tag) {
            return new TagAccessor<>(
                    (nbt, drops) -> {
                        // TODO replace with standard List serializer
                        if (drops != null && !drops.isEmpty()) {
                            TagCompound dropNBT = new TagCompound();
                            dropNBT.setInteger("count", drops.size());
                            for (int i = 0; i < drops.size(); i++) {
                                dropNBT.set("drop_" + i, drops.get(i).toTag());
                            }
                            nbt.set("drops", dropNBT);
                        }
                    },
                    nbt -> {
                        List<ItemStack> drops = new ArrayList<>();
                        if (nbt.hasKey("drops")) {
                            TagCompound dropNBT = nbt.get("drops");
                            int count = dropNBT.getInteger("count");
                            for (int i = 0; i < count; i++) {
                                drops.add(new ItemStack(dropNBT.get("drop_" + i)));
                            }
                        }
                        return drops;
                    }
            );
        }
    }
}

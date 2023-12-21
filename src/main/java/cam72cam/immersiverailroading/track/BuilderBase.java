package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.Config.ConfigDamage;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.thirdparty.trackapi.Track;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.util.Facing;
import cam72cam.mod.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//TODO @cam72cam use Vec3i and Vec3i

@SuppressWarnings("incomplete-switch")
public abstract class BuilderBase {
    public final Vec3i pos;
    protected final World world;
    public RailInfo info;
    public boolean overrideFlexible = true;
    public List<ItemStack> drops;
    protected ArrayList<TrackBase> tracks = new ArrayList<TrackBase>();
    private Vec3i parent_pos;

    public BuilderBase(RailInfo info, World world, Vec3i pos) {
        this.info = info;
        this.world = world;
        this.pos = pos;
        this.parent_pos = pos;
    }

    public abstract List<VecYawPitch> getRenderData();

    public boolean canBuild() {
        for (TrackBase track : this.tracks) {
            if (!track.canPlaceTrack()) {
                return false;
            }
        }
        return true;
    }

    public void build() {
		/*
		Assume we have already tested.
		There are a few edge cases which break with overlapping split builders
		if (!canBuild()) {
			return ;
		}
		*/
        for (TrackBase track : this.tracks) {
            if (!track.isOverTileRail()) {
                track.placeTrack(true).markDirty();
            } else {
                // Track -> Base?
                // To
                // Track -> Placed -> Base?

                TileRail rail = this.world.getBlockEntity(track.getPos(), TileRail.class);
                TileRailBase placed = track.placeTrack(false);
                placed.setReplaced(rail.getReplaced());
                rail.setReplaced(placed.getData());
                rail.markDirty();
            }
        }
    }

    public List<TrackBase> getTracksForRender() {
        return this.tracks;
    }

    public List<TrackBase> getTracksForFloating() {
        return this.tracks;
    }

    public Vec3i getParentPos() {
        return this.parent_pos;
    }

    public void setParentPos(Vec3i pos) {
        this.parent_pos = this.pos.add(pos);
    }

    public int costTies() {
        return (int) Math.ceil(this.tracks.size() / 3 * ConfigBalance.TieCostMultiplier);
    }

    public int costRails() {
        return (int) Math.ceil(this.tracks.size() * 2 / 3 * ConfigBalance.RailCostMultiplier / 2);
    }

    public int costBed() {
        //TODO more accurate
        return (int) Math.ceil(this.tracks.size() * 0.1 * ConfigBalance.BedCostMultiplier);
    }

    public int costFill() {
        int fillCount = 0;
        for (TrackBase track : this.tracks) {
            if (BlockUtil.canBeReplaced(this.world, track.getPos().down(), false)) {
                fillCount += 1;
            }
        }
        return (int) Math.ceil(!this.info.settings.railBedFill.isEmpty() ? fillCount : 0);
    }

    public void setDrops(List<ItemStack> drops) {
        this.drops = drops;
    }

    public void clearArea() {
        for (TrackBase track : this.tracks) {
            for (int i = 0; i < 6 * this.info.settings.gauge.scale(); i++) {
                Vec3i main = track.getPos().up(i);
                if (!Track.isRail(this.world, main) && !this.world.isSnow(main)) {
                    this.world.setToAir(main);
                }
                if (this.info.settings.gauge.isModelGauge() && ConfigDamage.enableSideBlockClearing && this.info.settings.type != TrackItems.SLOPE && this.info.settings.type != TrackItems.TURNTABLE) {
                    for (Facing facing : Facing.HORIZONTALS) {
                        Vec3i pos = main.offset(facing);
                        if (!Track.isRail(this.world, pos)) {
                            this.world.setToAir(pos);
                        }
                    }
                }
            }
            if (BlockUtil.canBeReplaced(this.world, track.getPos().down(), false)) {
                this.world.setToAir(track.getPos().down());
            }
        }
    }

    public class VecYawPitch extends Vec3d {
        public final float yaw;
        public final float pitch;
        public final float length;
        public final List<String> groups;

        public VecYawPitch(double xIn, double yIn, double zIn, float yaw, String... groups) {
            this(xIn, yIn, zIn, yaw, 0, groups);
        }

        public VecYawPitch(double xIn, double yIn, double zIn, float yaw, float pitch, String... groups) {
            this(xIn, yIn, zIn, yaw, pitch, -1, groups);
        }

        public VecYawPitch(double xIn, double yIn, double zIn, float yaw, float pitch, float length, String... groups) {
            super(xIn, yIn, zIn);
            this.yaw = yaw;
            this.groups = Arrays.asList(groups);
            this.pitch = pitch;
            this.length = length;
        }

        public float getYaw() {
            return this.yaw;
        }

        public float getPitch() {
            return this.pitch;
        }

        public float getLength() {
            return this.length;
        }

        public List<String> getGroups() {
            return this.groups;
        }
    }
}

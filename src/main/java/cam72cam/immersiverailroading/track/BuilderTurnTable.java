package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.PlacementInfo;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Rotation;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BuilderTurnTable extends BuilderBase {
    protected HashSet<Pair<Integer, Integer>> positions;
    private Vec3i offset;

    public BuilderTurnTable(RailInfo infoIn, World world, Vec3i pos) {
        super(infoIn.withSettings(b -> b.length = Math.min(infoIn.settings.length, BuilderTurnTable.maxLength(infoIn.settings.gauge))), world, pos);

        this.positions = new HashSet<>();

        this.offset = new Vec3i(0, 1, this.info.settings.length);
        this.offset = this.offset.rotate(Rotation.from(this.info.placementInfo.facing()));

        double radius = this.info.settings.length;

        for (double irad = 1; irad <= radius + 1; irad++) {
            for (double angle = 0; angle < 360; angle += 0.5) {
                Vec3d gagPos = VecUtil.fromYaw(irad, (float) angle);
                this.positions.add(Pair.of((int) gagPos.x, (int) gagPos.z));
            }
        }

        this.setParentPos(this.offset.down());
        TrackRail main = new TrackRail(this, this.offset.down());
        this.tracks.add(main);

        for (Pair<Integer, Integer> pair : this.positions) {
            double toCenter = new Vec3d(pair.getLeft(), 0, pair.getRight()).length();

            if (toCenter > this.info.settings.length + 0.5) {
                continue;
            }

            if (!(pair.getLeft() == 0 && pair.getRight() == 0)) {
                TrackGag tgu = new TrackGag(this, new Vec3i(pair.getLeft() + this.offset.x, this.offset.y - 1, pair.getRight() + this.offset.z));
                if (toCenter > this.info.settings.length - 0.5) {
                    tgu.setHeight(1);
                    tgu.setFlexible();
                }
                this.tracks.add(tgu);
            }

            TrackBase tg = new TrackGag(this, new Vec3i(pair.getLeft() + this.offset.x, this.offset.y, pair.getRight() + this.offset.z));
            tg.setHeight(0.000001f);
            tg.solidNotRequired = true;
            if (toCenter > this.info.settings.length - 0.5) {
                tg.setHeight(0);
            }
            if (toCenter > this.info.settings.length - 1.5) {
                tg.setFlexible();
            }
            this.tracks.add(tg);
        }
    }

    public static int maxLength(Gauge gauge) {
        return (int) (30 * gauge.scale());
    }

    @Override
    public List<VecYawPitch> getRenderData() {
        List<VecYawPitch> data = new ArrayList<>();

        if (this.info.itemHeld) {
            for (float angle = 0; angle < 360; angle += (90f / PlacementInfo.segmentation())) {
                Vec3d gagPos = VecUtil.rotateWrongYaw(new Vec3d(0, 0, this.info.settings.length), angle - 90);
                data.add(new VecYawPitch(gagPos.x + this.offset.x, gagPos.y + this.offset.y, gagPos.z + this.offset.z, -angle));
            }
        }

        float angle = (float) this.info.tablePos - this.info.placementInfo.facing().getAngle();
        data.add(new VecYawPitch(this.offset.x, this.offset.y, this.offset.z, -angle, 0, this.info.settings.length * 2, "RAIL_RIGHT", "RAIL_LEFT"));

        return data;
    }

    @Override
    public List<TrackBase> getTracksForRender() {
        return this.tracks;
    }

    public int costTies() {
        return (int) Math.ceil((this.info.settings.length + 8) * ConfigBalance.TieCostMultiplier);
    }

    public int costRails() {
        return (int) Math.ceil((this.info.settings.length + 8) * 2 / 3 * ConfigBalance.RailCostMultiplier);
    }

    public int costBed() {
        //TODO more accurate
        return (int) Math.ceil(this.tracks.size() / 2.0 * 0.1 * ConfigBalance.BedCostMultiplier);
    }

    public int costFill() {
        int fillCount = 0;
        for (TrackBase track : this.tracks) {
            if (track.rel.y == 1) {
                continue;
            }
            if (BlockUtil.canBeReplaced(this.world, track.getPos().down(), false)) {
                fillCount += 1;
            }
        }
        return (int) Math.ceil(!this.info.settings.railBedFill.isEmpty() ? fillCount : 0);
    }
}

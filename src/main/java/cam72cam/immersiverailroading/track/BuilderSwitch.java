package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class BuilderSwitch extends BuilderBase implements IIterableTrack {

    private final BuilderStraight straightBuilderReal;
    private final BuilderIterator turnBuilder;
    private BuilderStraight straightBuilder;
    private final BuilderStraight realStraightBuilder;

    public BuilderSwitch(RailInfo info, World world, Vec3i pos) {
        super(info, world, pos);

        RailInfo turnInfo = info.withSettings(b -> b.setType(info.customInfo.placementPosition.equals(info.placementInfo.placementPosition) ? TrackItems.TURN : TrackItems.CUSTOM));
        RailInfo straightInfo = info;

        {
            this.turnBuilder = (BuilderIterator) turnInfo.getBuilder(world, pos);
            this.straightBuilder = new BuilderStraight(straightInfo, world, pos, true);
            this.realStraightBuilder = new BuilderStraight(straightInfo, world, pos, true);

            straightInfo = straightInfo.withSettings(b -> {
                double maxOverlap = 0;

                this.straightBuilder.positions.retainAll(this.turnBuilder.positions);

                for (Pair<Integer, Integer> straight : this.straightBuilder.positions) {
                    maxOverlap = Math.max(maxOverlap, new Vec3d(straight.getKey(), 0, straight.getValue()).length());
                }

                maxOverlap *= 1.2;
                b.setLength((int) Math.ceil(maxOverlap) + 3);
            });
        }


        this.straightBuilder = new BuilderStraight(straightInfo, world, pos, true);
        this.straightBuilderReal = new BuilderStraight(straightInfo.withSettings(b -> b.setType(TrackItems.STRAIGHT)), world, pos, true);

        this.turnBuilder.overrideFlexible = true;

        for (TrackBase turn : this.turnBuilder.tracks) {
            if (turn instanceof TrackRail) {
                turn.overrideParent(this.straightBuilder.getParentPos());
            }
        }
        for (TrackBase straight : this.straightBuilder.tracks) {
            if (straight instanceof TrackGag) {
                straight.setFlexible();
            }
        }
    }

    @Override
    public List<VecYawPitch> getRenderData() {
        List<VecYawPitch> data = this.straightBuilder.getRenderData();
        data.addAll(this.turnBuilder.getRenderData());
        return data;
    }

    @Override
    public boolean canBuild() {
        return this.straightBuilder.canBuild() && this.turnBuilder.canBuild();
    }

    @Override
    public void build() {
        this.straightBuilder.build();
        this.turnBuilder.build();
    }

    @Override
    public List<TrackBase> getTracksForRender() {
        List<TrackBase> data = this.straightBuilder.getTracksForRender();
        data.addAll(this.turnBuilder.getTracksForRender());
        return data;
    }

    @Override
    public int costTies() {
        return this.straightBuilder.costTies() + this.turnBuilder.costTies();
    }

    @Override
    public int costRails() {
        return this.straightBuilder.costRails() + this.turnBuilder.costRails();
    }

    @Override
    public int costBed() {
        return this.straightBuilder.costBed() + this.turnBuilder.costBed();
    }

    @Override
    public int costFill() {
        return this.straightBuilder.costFill() + this.turnBuilder.costFill();
    }

    @Override
    public void setDrops(List<ItemStack> drops) {
        this.straightBuilder.setDrops(drops);
    }

    @Override
    public void clearArea() {
        this.straightBuilder.clearArea();
        this.turnBuilder.clearArea();
    }

    @Override
    public List<PosStep> getPath(double stepSize) {
        return this.realStraightBuilder.getPath(stepSize);
    }

    @Override
    public List<BuilderBase> getSubBuilders() {
        List<BuilderBase> subTurns = this.turnBuilder.getSubBuilders();
        List<BuilderBase> subStraights = this.straightBuilderReal.getSubBuilders();

        if (subTurns == null && subStraights == null) {
            return null;
        }

        List<BuilderBase> res = new ArrayList<>();
        if (subTurns == null) {
            res.add(this.turnBuilder);
        } else {
            res.addAll(subTurns);
        }
        if (subStraights == null) {
            res.add(this.straightBuilderReal);
        } else {
            res.addAll(subStraights);
        }
        return res;
    }
}

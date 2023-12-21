package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.util.PlacementInfo;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class BuilderCubicCurve extends BuilderIterator {
    private List<BuilderBase> subBuilders;
    private HashMap<Double, List<PosStep>> cache;

    public BuilderCubicCurve(RailInfo info, World world, Vec3i pos) {
        this(info, world, pos, false);
    }

    public BuilderCubicCurve(RailInfo info, World world, Vec3i pos, boolean endOfTrack) {
        super(info, world, pos, endOfTrack);
        CubicCurve curve = this.getCurve();
        List<CubicCurve> subCurves = curve.subsplit((int) (101 * 2 * 3.1415f / 4));
        if (subCurves.size() > 1) {
            this.subBuilders = new ArrayList<>();
            for (CubicCurve subCurve : subCurves) {
                // main pos -> subCurve's start pos
                Vec3d relOff = info.placementInfo.placementPosition.add(subCurve.p1);
                Vec3i relPos = new Vec3i(relOff);
                Vec3i sPos = pos.add(relPos);
                // The block remainder of curve position, with the subCurve move to origin block included
                Vec3d delta = relOff.subtract(relPos).subtract(subCurve.p1);
                //delta = delta.subtract(new Vec3i(delta)); // Relative position within the block
                PlacementInfo startPos = new PlacementInfo(subCurve.p1.add(delta), info.placementInfo.direction, subCurve.angleStart(), subCurve.ctrl1.add(delta));
                PlacementInfo endPos = new PlacementInfo(subCurve.p2.add(delta), info.placementInfo.direction, subCurve.angleStop(), subCurve.ctrl2.add(delta));
                RailInfo subInfo = new RailInfo(info.settings.with(b -> b.setType(TrackItems.CUSTOM)), startPos, endPos, SwitchState.NONE, SwitchState.NONE, 0);

                BuilderCubicCurve subBuilder = new BuilderCubicCurve(subInfo, world, sPos);
                if (this.subBuilders.size() != 0) {
                    for (TrackBase track : subBuilder.tracks) {
                        if (track instanceof TrackRail) {
                            track.overrideParent(this.subBuilders.get(0).getParentPos());
                        }
                    }
                } else {
                    this.tracks = subBuilder.tracks;
                }
                this.subBuilders.add(subBuilder);
            }
        }
    }

    public CubicCurve getCurve() {
        Vec3d nextPos = new Vec3d(new Vec3i(VecUtil.fromYaw(this.info.settings.length, this.info.placementInfo.yaw + 45)));

        boolean isDefault = this.info.customInfo.placementPosition.equals(this.info.placementInfo.placementPosition);
        if (!isDefault) {
            nextPos = this.info.customInfo.placementPosition.subtract(this.info.placementInfo.placementPosition);
        }

        double ctrlGuess = nextPos.length() / 2 * Math.max(0.1, this.info.settings.curvosity);
        float angle = this.info.placementInfo.yaw;

        float angle2 = angle + 180;

        if (!isDefault) {
            angle2 = this.info.customInfo.yaw;
        }

        Vec3d ctrl1 = VecUtil.fromYaw(ctrlGuess, angle);
        Vec3d ctrl2 = nextPos.add(VecUtil.fromYaw(ctrlGuess, angle2));

        CubicCurve adjusted = new CubicCurve(Vec3d.ZERO, ctrl1, ctrl2, nextPos).linearize(this.info.settings.smoothing);
        ctrl1 = adjusted.ctrl1;
        ctrl2 = adjusted.ctrl2;

        if (this.info.placementInfo.control != null) {
            ctrl1 = this.info.placementInfo.control.subtract(this.info.placementInfo.placementPosition);
        }
        if (this.info.customInfo.control != null && !isDefault) {
            ctrl2 = this.info.customInfo.control.subtract(this.info.placementInfo.placementPosition);
        }

        return new CubicCurve(Vec3d.ZERO, ctrl1, ctrl2, nextPos);
    }

    @Override
    public List<BuilderBase> getSubBuilders() {
        return this.subBuilders;
    }

    @Override
    public List<PosStep> getPath(double stepSize) {
        if (this.cache == null) {
            this.cache = new HashMap<>();
        }

        if (this.cache.containsKey(stepSize)) {
            return this.cache.get(stepSize);
        }

        List<PosStep> res = new ArrayList<>();
        CubicCurve curve = this.getCurve();

        // HACK for super long curves
        // Skip the super long calculation since it'll be overridden anyways
        curve = curve.subsplit(200).get(0);

        List<Vec3d> points = curve.toList(stepSize);
        for (int i = 0; i < points.size(); i++) {
            Vec3d p = points.get(i);
            float yaw;
            float pitch;
            if (points.size() == 1) {
                yaw = this.info.placementInfo.yaw;
                pitch = 0;
            } else if (i == points.size() - 1) {
                Vec3d next = points.get(i - 1);
                pitch = (float) Math.toDegrees(Math.atan2(next.y - p.y, next.distanceTo(p)));
                yaw = curve.angleStop();
            } else if (i == 0) {
                Vec3d next = points.get(i + 1);
                pitch = (float) -Math.toDegrees(Math.atan2(next.y - p.y, next.distanceTo(p)));
                yaw = curve.angleStart();
            } else {
                Vec3d prev = points.get(i - 1);
                Vec3d next = points.get(i + 1);
                pitch = (float) -Math.toDegrees(Math.atan2(next.y - prev.y, next.distanceTo(prev)));
                yaw = VecUtil.toYaw(points.get(i + 1).subtract(points.get(i - 1)));
            }
            res.add(new PosStep(p, yaw, pitch));
        }
        this.cache.put(stepSize, res);
        return this.cache.get(stepSize);
    }

    /* OVERRIDES */

    @Override
    public List<VecYawPitch> getRenderData() {
        if (this.subBuilders == null) {
            return super.getRenderData();
        } else {
            List<VecYawPitch> data = new ArrayList<>();
            for (BuilderBase curve : this.subBuilders.subList(0, Math.min(this.subBuilders.size(), 3))) {
                Vec3d offset = new Vec3d(curve.pos.subtract(this.pos));
                for (VecYawPitch rd : curve.getRenderData()) {
                    rd = new VecYawPitch(rd.x + offset.x, rd.y + offset.y, rd.z + offset.z, rd.yaw, rd.pitch, rd.length);
                    data.add(rd);
                }
            }
            return data;
        }
    }

    @Override
    public List<TrackBase> getTracksForRender() {
        if (this.subBuilders == null) {
            return super.getTracksForRender();
        } else {
            return this.subBuilders.subList(0, Math.min(this.subBuilders.size(), 3))
                    .stream()
                    .map(BuilderBase::getTracksForRender)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public boolean canBuild() {
        if (this.subBuilders == null) {
            return super.canBuild();
        } else {
            return this.subBuilders.stream().allMatch(BuilderBase::canBuild);
        }
    }

    @Override
    public void build() {
        if (this.subBuilders == null) {
            super.build();
        } else {
            this.subBuilders.forEach(BuilderBase::build);
        }
    }

    @Override
    public List<TrackBase> getTracksForFloating() {
        if (this.subBuilders == null) {
            return super.getTracksForFloating();
        }
        return Collections.emptyList();
    }

    @Override
    public int costTies() {
        if (this.subBuilders == null) {
            return super.costTies();
        } else {
            return this.subBuilders.stream().mapToInt((BuilderBase::costTies)).sum();
        }
    }

    @Override
    public int costRails() {
        if (this.subBuilders == null) {
            return super.costRails();
        } else {
            return this.subBuilders.stream().mapToInt((BuilderBase::costRails)).sum();
        }
    }

    @Override
    public int costBed() {
        if (this.subBuilders == null) {
            return super.costBed();
        } else {
            return this.subBuilders.stream().mapToInt((BuilderBase::costBed)).sum();
        }
    }

    @Override
    public int costFill() {
        if (this.subBuilders == null) {
            return super.costFill();
        } else {
            return this.subBuilders.stream().mapToInt((BuilderBase::costFill)).sum();
        }
    }

    @Override
    public void setDrops(List<ItemStack> drops) {
        if (this.subBuilders == null) {
            super.setDrops(drops);
        } else {
            this.subBuilders.get(0).setDrops(drops);
        }
    }

    @Override
    public void clearArea() {
        if (this.subBuilders == null) {
            super.clearArea();
        } else {
            this.subBuilders.forEach(BuilderBase::clearArea);
        }
    }
}

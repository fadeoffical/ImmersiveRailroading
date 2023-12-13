package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.library.TrackDirection;
import cam72cam.immersiverailroading.util.MathUtil;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.SerializationException;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagSerializer;
import cam72cam.mod.util.Facing;
import cam72cam.mod.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public abstract class BuilderIterator extends BuilderBase implements IIterableTrack {
    protected HashSet<Pair<Integer, Integer>> positions;

    public BuilderIterator(RailInfo info, World world, Vec3i pos) {
        this(info, world, pos, false);
    }

    public BuilderIterator(RailInfo info, World world, Vec3i pos, boolean endOfTrack) {
        super(info, world, pos);

        this.positions = new HashSet<>();
        HashMap<Pair<Integer, Integer>, Float> bedHeights = new HashMap<>();
        HashMap<Pair<Integer, Integer>, Float> railHeights = new HashMap<>();
        HashMap<Pair<Integer, Integer>, Integer> yOffset = new HashMap<>();
        HashSet<Pair<Integer, Integer>> flexPositions = new HashSet<>();

        double horiz = info.settings.gauge.scale() * 1.1;
        if (Config.ConfigDebug.oldNarrowWidth && info.settings.gauge.getRailDistance() < 1) {
            horiz /= 2;
        }
        if (info.settings.isGradeCrossing) {
            horiz += 2f * info.settings.gauge.scale();
        }
        double clamp = 0.17 * info.settings.gauge.scale();
        float heightOffset = (float) ((info.placementInfo.placementPosition.y) % 1);

        List<PosStep> path = this.getPath(0.25);
        PosStep start = path.get(0);
        PosStep end = path.get(path.size() - 1);

        Vec3d placeOff = new Vec3d(
                Math.abs(MathUtil.trueModulus(info.placementInfo.placementPosition.x, 1)),
                0,
                Math.abs(MathUtil.trueModulus(info.placementInfo.placementPosition.z, 1))
        );
        int mainX = (int) Math.floor(path.get(path.size() / 2).x + placeOff.x);
        int mainZ = (int) Math.floor(path.get(path.size() / 2).z + placeOff.z);
        int flexDist = (int) Math.max(1, 3 * (0.5 + info.settings.gauge.scale() / 2));

        for (PosStep cur : path) {
            Vec3d gagPos = cur;

            boolean isFlex = gagPos.distanceTo(start) < flexDist || gagPos.distanceTo(end) < flexDist;

            gagPos = gagPos.add(0, heightOffset, 0);

            for (double q = -horiz; q <= horiz; q += 0.1) {
                Vec3d nextUp = VecUtil.fromYaw(q, 90 + cur.yaw);
                int posX = (int) Math.floor(gagPos.x + nextUp.x + placeOff.x);
                int posZ = (int) Math.floor(gagPos.z + nextUp.z + placeOff.z);
                double height = 0;
                if (info.settings.isGradeCrossing) {
                    height = (1 - Math.abs((int) q) / horiz) / 3 - 0.05;
                    height *= info.settings.gauge.scale();
                    height = Math.min(height, clamp);
                }

                double relHeight = gagPos.y % 1;
                if (gagPos.y < 0) {
                    relHeight += 1;
                }

                Pair<Integer, Integer> gag = Pair.of(posX, posZ);
                if (!this.positions.contains(gag)) {
                    this.positions.add(gag);
                    bedHeights.put(gag, (float) (height + Math.max(0, relHeight - 0.1)));
                    railHeights.put(gag, (float) relHeight);
                    yOffset.put(gag, (int) (gagPos.y - relHeight));
                }
                if (isFlex || Math.abs(q) > info.settings.gauge.getRailDistance()) {
                    flexPositions.add(gag);
                }
            }
            if (!isFlex && endOfTrack) {
                mainX = (int) Math.floor(gagPos.x + placeOff.x);
                mainZ = (int) Math.floor(gagPos.z + placeOff.z);
            }
        }

        if (!yOffset.containsKey(Pair.of(mainX, mainZ))) {
            // Try a few different offsets
            for (Facing value : Facing.values()) {
                if (yOffset.containsKey(Pair.of(mainX + value.getXMultiplier(), mainZ + value.getZMultiplier()))) {
                    mainX += value.getXMultiplier();
                    mainZ += value.getZMultiplier();
                    break;
                }
            }
        }
        if (!yOffset.containsKey(Pair.of(mainX, mainZ))) {
            // No luck, code is really borked now.  Throw an exception to help track this.
            TagCompound debug = new TagCompound();
            try {
                TagSerializer.serialize(debug, info);
            } catch (SerializationException e) {
                throw new RuntimeException("Invalid track builder", e);
            }
            throw new RuntimeException("Invalid track builder " + debug);
        }

        Vec3i mainPos = new Vec3i(mainX, yOffset.get(Pair.of(mainX, mainZ)), mainZ);
        this.setParentPos(mainPos);
        TrackRail main = new TrackRail(this, mainPos);
        this.tracks.add(main);
        main.setRailHeight(railHeights.get(Pair.of(mainX, mainZ)));
        main.setBedHeight(bedHeights.get(Pair.of(mainX, mainZ)));

        for (Pair<Integer, Integer> pair : this.positions) {
            if (pair.getLeft() == mainX && pair.getRight() == mainZ) {
                // Skip parent block
                continue;
            }
            TrackBase tg = new TrackGag(this, new Vec3i(pair.getLeft(), yOffset.get(pair), pair.getRight()));
            if (flexPositions.contains(pair)) {
                tg.setFlexible();
            }
            tg.setRailHeight(railHeights.get(pair));
            tg.setBedHeight(bedHeights.get(pair));
            this.tracks.add(tg);
        }
    }

    public abstract List<PosStep> getPath(double stepSize);

    @Override
    public List<VecYawPitch> getRenderData() {
        List<VecYawPitch> data = new ArrayList<VecYawPitch>();

        double scale = this.info.settings.gauge.scale();
        List<PosStep> points = this.getPath(scale * this.info.getTrackModel().spacing);

        boolean switchStraight = this.info.switchState == SwitchState.STRAIGHT;
        int switchSize = 0;
        TrackDirection direction = this.info.placementInfo.direction;
        if (switchStraight) {
            for (int i = 0; i < points.size(); i++) {
                PosStep cur = points.get(i);
                Vec3d flatPos = VecUtil.rotateYaw(cur, -this.info.placementInfo.yaw);
                if (Math.abs(flatPos.z) >= 0.5 * scale) {
                    switchSize = i;
                    break;
                }
            }
        }

        for (int i = 0; i < points.size(); i++) {
            PosStep cur = points.get(i);
            PosStep switchPos = cur;
            if (switchStraight) {
                double switchOffset = 1 - (i / (double) switchSize);
                if (switchOffset > 0) {
                    double dist = 0.2 * switchOffset * scale * this.info.getTrackModel().spacing;
                    Vec3d offset = VecUtil.fromYaw(dist, cur.yaw + 90 + this.info.placementInfo.direction.toYaw());
                    double offsetAngle = Math.toDegrees(0.2 / switchSize); // This line took a whole page of scribbled math
                    if (direction == TrackDirection.RIGHT) {
                        offsetAngle = -offsetAngle;
                    }
                    switchPos = new PosStep(cur.add(offset), cur.yaw + (float) offsetAngle, cur.pitch);
                }
            }

            float angle;
            if (points.size() == 1) {
                angle = 0;
            } else if (i + 1 == points.size()) {
                PosStep next = points.get(i - 1);
                angle = delta(next.yaw, cur.yaw);
                angle *= 2;
            } else if (i == 0) {
                PosStep next = points.get(i + 1);
                angle = delta(cur.yaw, next.yaw);
                angle *= 2;
            } else {
                PosStep prev = points.get(i - 1);
                PosStep next = points.get(i + 1);
                angle = delta(prev.yaw, next.yaw);
            }
            if (angle != 0) {
                if (direction == TrackDirection.RIGHT) {
                    data.add(new VecYawPitch(switchPos.x, switchPos.y, switchPos.z, switchPos.yaw, switchPos.pitch, (1 - angle / 180) * (float) this.info.settings.gauge.scale(), "RAIL_LEFT"));
                    data.add(new VecYawPitch(cur.x, cur.y, cur.z, cur.yaw, cur.pitch, (1 + angle / 180) * (float) this.info.settings.gauge.scale(), "RAIL_RIGHT"));
                } else {
                    data.add(new VecYawPitch(cur.x, cur.y, cur.z, cur.yaw, cur.pitch, (1 - angle / 180) * (float) this.info.settings.gauge.scale(), "RAIL_LEFT"));
                    data.add(new VecYawPitch(switchPos.x, switchPos.y, switchPos.z, switchPos.yaw, switchPos.pitch, (1 + angle / 180) * (float) this.info.settings.gauge.scale(), "RAIL_RIGHT"));
                }
                data.add(new VecYawPitch(cur.x, cur.y, cur.z, cur.yaw, cur.pitch, "RAIL_BASE"));
            } else {
                data.add(new VecYawPitch(cur.x, cur.y, cur.z, cur.yaw, cur.pitch));
            }
        }

        return data;
    }

    @Override
    public List<TrackBase> getTracksForRender() {
        return this.tracks;
    }

    private static float delta(float a, float b) {
        float angle = (float) Math.toDegrees(Math.toRadians(a) - Math.toRadians(b));
        if (angle > 180) {
            angle -= 360;
        }
        if (angle < -180) {
            angle += 360;
        }
        return angle;
    }
}

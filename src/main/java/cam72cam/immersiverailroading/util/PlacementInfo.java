package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.items.nbt.RailSettings;
import cam72cam.immersiverailroading.library.TrackDirection;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapped;
import cam72cam.mod.util.Facing;

@TagMapped(PlacementInfo.TagMapper.class)
public class PlacementInfo {
    public final Vec3d placementPosition; // relative
    public final TrackDirection direction;
    public final float yaw;
    public final Vec3d control;

    public PlacementInfo(Vec3d placementPosition, TrackDirection direction, float yaw, Vec3d control) {
        this.placementPosition = placementPosition;
        this.direction = direction;
        this.yaw = yaw;
        this.control = control;
    }

    public PlacementInfo(ItemStack stack, float yawHead, Vec3d hit) {
        yawHead = ((-yawHead % 360) + 360) % 360;
        this.yaw = ((int) ((yawHead + 90 / (segmentation() * 2f)) * segmentation())) / 90 * 90 / (segmentation() * 1f);

        RailSettings settings = RailSettings.from(stack);
        TrackDirection direction = settings.direction;
        if (direction == TrackDirection.NONE) {
            direction = (yawHead % 90 < 45) ? TrackDirection.RIGHT : TrackDirection.LEFT;
        }

        int quarter = this.rotationQuarter();


        double hitX = hit.x % 1;
        double hitZ = hit.z % 1;

        switch (settings.posType) {
            case FIXED:
                hitX = 0.5f;
                hitZ = 0.5f;
                break;
            case PIXELS:
                hitX = ((int) (hitX * 16)) / 16f;
                hitZ = ((int) (hitZ * 16)) / 16f;
                break;
            case PIXELS_LOCKED:
                hitX = ((int) (hitX * 16)) / 16f;
                hitZ = ((int) (hitZ * 16)) / 16f;

                if (quarter != 0) {
                    break;
                }

                switch (this.facing()) {
                    case EAST:
                    case WEST:
                        hitZ = 0.5f;
                        break;
                    case NORTH:
                    case SOUTH:
                        hitX = 0.5f;
                        break;
                    default:
                        break;
                }
                break;
            case SMOOTH:
                // NOP
                break;
            case SMOOTH_LOCKED:
                if (quarter != 0) {
                    break;
                }

                switch (this.facing()) {
                    case EAST:
                    case WEST:
                        hitZ = 0.5f;
                        break;
                    case NORTH:
                    case SOUTH:
                        hitX = 0.5f;
                        break;
                    default:
                        break;
                }
                break;
        }

        this.placementPosition = new Vec3d(new Vec3i(hit)).add(hitX, 0, hitZ);
        this.direction = direction;
        this.control = null;
    }

    public static int segmentation() {
        return Math.min(90, Math.max(1, Config.ConfigBalance.AnglePlacementSegmentation));
    }

    public int rotationQuarter() {
        return (int) ((this.yaw % 90) * segmentation() / 90);
    }

    public Facing facing() {
        return Facing.fromAngle(180 - this.yaw);
    }

    @Deprecated
    public PlacementInfo(TagCompound nbt) {
        this.placementPosition = nbt.getVec3d("placementPosition");
        this.direction = TrackDirection.values()[nbt.getInteger("direction")];
        if (nbt.hasKey("yaw")) {
            this.yaw = nbt.getFloat("yaw");
        } else {
            int rotationQuarter = nbt.getInteger("rotationQuarter");
            Facing facing = Facing.from(nbt.getByte("facing"));
            float facingAngle = 180 - facing.getAngle();
            float rotAngle = rotationQuarter / 4f * 90;
            if (this.direction != TrackDirection.RIGHT) {
                rotAngle = -rotAngle;
            }
            this.yaw = facingAngle + rotAngle;
        }
        if (nbt.hasKey("control")) {
            this.control = nbt.getVec3d("control");
        } else {
            this.control = null;
        }
    }

    public TagCompound toNBT() {
        TagCompound nbt = new TagCompound();
        nbt.setVec3d("placementPosition", this.placementPosition);
        nbt.setFloat("yaw", this.yaw);
        nbt.setInteger("direction", this.direction.ordinal());
        if (this.control != null) {
            nbt.setVec3d("control", this.control);
        }
        return nbt;
    }

    public float partialAngle() {
        return this.yaw % 90;
    }

    public PlacementInfo offset(Vec3i offset) {
        return new PlacementInfo(this.placementPosition.add(offset), this.direction, this.yaw, this.control != null ? this.control.add(offset) : null);
    }

    public PlacementInfo withDirection(TrackDirection direction) {
        return new PlacementInfo(this.placementPosition, direction, this.yaw, this.control);
    }

    static class TagMapper implements cam72cam.mod.serialization.TagMapper<PlacementInfo> {
        @Override
        public TagAccessor<PlacementInfo> apply(Class<PlacementInfo> type, String fieldName, TagField tag) {
            return new TagAccessor<>(
                    (d, o) -> {
                        if (o == null) {
                            d.remove(fieldName);
                        } else {
                            d.set(fieldName, o.toNBT());
                        }
                    },
                    d -> d.hasKey(fieldName) ? new PlacementInfo(d.get(fieldName)) : null
            );
        }
    }
}

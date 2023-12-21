package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.library.ValveGearConfig;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;

public class DrivingAssembly {
    public final WheelSet wheels;
    private final ValveGear right;
    private final ValveGear inner_right;
    private final ValveGear center;
    private final ValveGear inner_left;
    private final ValveGear left;
    private final ModelComponent steamChest;

    public DrivingAssembly(WheelSet wheels, ValveGear right, ValveGear inner_right, ValveGear center, ValveGear inner_left, ValveGear left, ModelComponent steamChest) {
        this.wheels = wheels;
        this.right = right;
        this.inner_right = inner_right;
        this.center = center;
        this.inner_left = inner_left;
        this.left = left;
        this.steamChest = steamChest;
    }

    public static DrivingAssembly get(ValveGearConfig type, ComponentProvider provider, ModelState state, float angleOffset, WheelSet... backups) {
        return get(type, provider, state, null, angleOffset, backups);
    }

    public static DrivingAssembly get(ValveGearConfig type, ComponentProvider provider, ModelState state, ModelPosition pos, float angleOffset, WheelSet... backups) {
        WheelSet wheels = WheelSet.get(provider, state, pos == null ? ModelComponentType.WHEEL_DRIVER_X : ModelComponentType.WHEEL_DRIVER_POS_X, pos, angleOffset);
        if (wheels == null) {
            for (WheelSet backup : backups) {
                if (backup != null) {
                    wheels = backup;
                    break;
                }
            }
        }
        if (wheels == null) {
            return null;
        }

        ValveGear left = ValveGear.get(wheels, type, provider, state, ModelPosition.LEFT.and(pos), 0);
        ValveGear inner_left = ValveGear.get(wheels, type, provider, state, ModelPosition.INNER_LEFT.and(pos), 180);
        ValveGear center = ValveGear.get(wheels, type, provider, state, ModelPosition.CENTER.and(pos), -120);
        ValveGear inner_right = ValveGear.get(wheels, type, provider, state, ModelPosition.INNER_RIGHT.and(pos), 90);
        ValveGear right = ValveGear.get(wheels, type, provider, state, ModelPosition.RIGHT.and(pos), center == null ? -90 : -240);

        ModelComponent steamChest = pos == null ?
                provider.parse(ModelComponentType.STEAM_CHEST) :
                provider.parse(ModelComponentType.STEAM_CHEST_POS, pos);

        // TODO this should rock
        state.include(steamChest);

        return new DrivingAssembly(wheels, right, inner_right, center, inner_left, left, steamChest);
    }

    public boolean isEndStroke(EntityMovableRollingStock stock) {
        boolean isEndStroke = this.right != null && this.right.isEndStroke(stock);
        isEndStroke |= this.inner_right != null && this.inner_right.isEndStroke(stock);
        isEndStroke |= this.center != null && this.center.isEndStroke(stock);
        isEndStroke |= this.inner_left != null && this.inner_left.isEndStroke(stock);
        isEndStroke |= this.left != null && this.left.isEndStroke(stock);
        return isEndStroke;
    }

    public void effects(EntityMovableRollingStock stock) {
        if (this.right != null) {
            this.right.effects(stock);
        }
        if (this.inner_right != null) {
            this.inner_right.effects(stock);
        }
        if (this.center != null) {
            this.center.effects(stock);
        }
        if (this.inner_left != null) {
            this.inner_left.effects(stock);
        }
        if (this.left != null) {
            this.left.effects(stock);
        }
    }

    public void removed(EntityMovableRollingStock stock) {
        if (this.right != null) {
            this.right.removed(stock);
        }
        if (this.inner_right != null) {
            this.inner_right.removed(stock);
        }
        if (this.center != null) {
            this.center.removed(stock);
        }
        if (this.inner_left != null) {
            this.inner_left.removed(stock);
        }
        if (this.left != null) {
            this.left.removed(stock);
        }
    }
}

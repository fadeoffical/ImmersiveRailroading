package cam72cam.immersiverailroading.model.components;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.model.obj.OBJGroup;
import cam72cam.mod.model.obj.OBJModel;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ModelComponent {
    public final ModelComponentType type;
    public final ModelPosition pos;
    public final Integer id;
    public final String key;

    public final Set<String> modelIDs;
    public final Vec3d min;
    public final Vec3d max;
    public final Vec3d center;
    public final boolean wooden;
    private final OBJModel model;

    public ModelComponent(ModelComponentType type, ModelPosition pos, Integer id, OBJModel model, Set<String> modelIDs) {
        this.type = type;
        this.pos = pos;
        this.id = id;
        this.modelIDs = modelIDs;
        this.key = String.join(" ", modelIDs);
        this.model = model;
        this.min = model.minOfGroup(this.modelIDs);
        this.max = model.maxOfGroup(this.modelIDs);
        this.center = new Vec3d((this.min.x + this.max.x) / 2, (this.min.y + this.max.y) / 2, (this.min.z + this.max.z) / 2);
        this.wooden = modelIDs.stream().anyMatch(g -> g.contains("WOOD"));
    }

    public static Vec3d center(List<ModelComponent> components) {
        double minX = components.get(0).min.x;
        double minY = components.get(0).min.y;
        double minZ = components.get(0).min.z;
        double maxX = components.get(0).max.x;
        double maxY = components.get(0).max.y;
        double maxZ = components.get(0).max.z;

        for (ModelComponent rc : components) {
            minX = Math.min(minX, rc.min.x);
            minY = Math.min(minY, rc.min.y);
            minZ = Math.min(minZ, rc.min.z);
            maxX = Math.max(maxX, rc.max.x);
            maxY = Math.max(maxY, rc.max.y);
            maxZ = Math.max(maxZ, rc.max.z);
        }
        return new Vec3d((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    public double length() {
        return this.max.x - this.min.x;
    }

    public double height() {
        return this.max.y - this.min.y;
    }

    public double width() {
        return this.max.z - this.min.z;
    }

    public Vec3d min(EntityRollingStock stock) {
        return worldPosition(this.min, stock);
    }

    public static Vec3d worldPosition(Vec3d pos, EntityRollingStock stock) {
        return stock.getModelMatrix().apply(pos);
    }

    public Vec3d center(EntityRollingStock stock) {
        return worldPosition(this.center, stock);
    }

    public Vec3d max(EntityRollingStock stock) {
        return worldPosition(this.max, stock);
    }

    public List<OBJGroup> groups() {
        return this.modelIDs.stream().map(this.model.groups::get).collect(Collectors.toList());
    }
}

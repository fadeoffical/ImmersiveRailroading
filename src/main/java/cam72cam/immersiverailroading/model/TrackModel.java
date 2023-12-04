package cam72cam.immersiverailroading.model;

import cam72cam.mod.model.obj.OBJModel;
import cam72cam.mod.resource.Identifier;
import trackapi.lib.Gauges;

import java.util.ArrayList;

public class TrackModel extends OBJModel {
    public final double spacing;
    private final String compare;
    private final double size;
    private final double height;

    public TrackModel(String condition, Identifier resource, double model_gauge_m, double spacing) throws Exception {
        super(resource, 0, Gauges.STANDARD / model_gauge_m);
        this.compare = condition.substring(0, 1);
        this.size = Double.parseDouble(condition.substring(1));
        ArrayList<String> groups = new ArrayList<>();
        for (String group : this.groups()) {
            if (group.contains("RAIL_LEFT") || group.contains("RAIL_RIGHT")) {
                groups.add(group);
            }
        }
        this.height = this.maxOfGroup(groups).y;
        this.spacing = spacing * (Gauges.STANDARD / model_gauge_m);
    }

    public boolean canRender(double gauge) {
        switch (this.compare) {
            case ">":
                return gauge > this.size;
            case "<":
                return gauge < this.size;
            case "=":
                return gauge == this.size;
            default:
                return true;
        }
    }

    public double getHeight() {
        return this.height;
    }
}

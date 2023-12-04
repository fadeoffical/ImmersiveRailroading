package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.items.ItemRail;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.TrackComponent;
import cam72cam.immersiverailroading.model.TrackModel;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import trackapi.lib.Gauges;

import java.util.*;
import java.util.stream.Collectors;

public class TrackDefinition {
    public final String trackID;
    public final String name;
    public final String modelerName;
    public final String packName;
    public final List<TrackModel> models;
    public final boolean clack;
    public final Map<TrackComponent, List<TrackMaterial>> materials = new HashMap<>();
    public final float bumpiness;
    public final boolean cog;
    public final double model_gauge_m;

    TrackDefinition(String trackID, DataBlock object) throws Exception {
        this.trackID = trackID;
        this.name = object.getValue("name").asString();
        this.modelerName = object.getValue("modeler").asString();
        this.packName = object.getValue("pack").asString();

        this.clack = object.getValue("clack").asBoolean(true);
        this.bumpiness = object.getValue("bumpiness").asFloat(this.clack ? 1f : 0f);
        this.cog = object.getValue("cog").asBoolean(false);
        this.model_gauge_m = object.getValue("model_gauge_m").asDouble(Gauges.STANDARD);
        double spacing = object.getValue("model_spacing_m").asDouble(this.model_gauge_m / Gauges.STANDARD);

        this.models = new ArrayList<>();
        DataBlock models = object.getBlock("models");
        for (Map.Entry<String, DataBlock.Value> entry : models.getValueMap().entrySet()) {
            this.models.add(new TrackModel(entry.getKey(), entry.getValue().asIdentifier(), this.model_gauge_m, spacing));
        }

        DataBlock mats = object.getBlock("materials");
        for (TrackComponent comp : TrackComponent.values()) {
            List<DataBlock> blocks = mats.getBlocks(comp.name());
            if (blocks != null) {
                List<TrackMaterial> parts = new ArrayList<>();
                for (DataBlock part : blocks) {
                    parts.add(new TrackMaterial(
                            part.getValue("item").asString(),
                            part.getValue("cost").asFloat()
                    ));
                }
                if (parts.size() > 0) {
                    this.materials.put(comp, parts);
                }
            }
        }
    }

    public TrackModel getTrackForGauge(double gauge) {
        for (TrackModel model : this.models) {
            if (model.canRender(gauge)) {
                return model;
            }
        }
        ImmersiveRailroading.warn("Bad track gauge def for %s - %s", this.trackID, gauge);
        return this.models.get(0);
    }

    public static class ItemType {
        final String item;
        final int meta;

        public ItemType(String item) {
            if (item.contains("|")) {
                this.item = item.split("\\|")[0];
                this.meta = Integer.parseInt(item.split("\\|")[1]);
            } else {
                this.item = item;
                this.meta = 0;
            }
        }

        public List<ItemStack> examples(Gauge gauge) {
            List<ItemStack> examples = new ArrayList<>();

            if (this.item.startsWith("ore:")) {
                String oreName = this.item.replace("ore:", "");
                examples.addAll(Fuzzy.get(oreName).enumerate());
                for (ItemStack example : examples) {
                    if (example.is(IRItems.ITEM_RAIL)) {
                        ItemRail.Data data = new ItemRail.Data(example);
                        data.gauge = gauge;
                        data.write();
                    }
                }
            } else {
                examples.add(new ItemStack(this.item, 1, this.meta));
            }
            return examples;
        }

        public boolean matches(ItemStack stack) {
            if (this.item.startsWith("ore:")) {
                String oreName = this.item.replace("ore:", "");
                return Fuzzy.get(oreName).matches(stack);
            }
            return stack.is(new ItemStack(this.item, 1, this.meta));
        }
    }

    public static class TrackMaterial {
        public final float cost;
        private final List<ItemType> items;

        TrackMaterial(String item, float cost) {
            this.items = Arrays.stream(item.split(","))
                    .map(s -> s.trim())
                    .map(ItemType::new)
                    .collect(Collectors.toList());
            this.cost = cost;
        }

        public List<ItemStack> examples(Gauge gauge) {
            return this.items.stream().flatMap(i -> i.examples(gauge).stream()).collect(Collectors.toList());
        }

        public boolean matches(ItemStack stack) {
            return this.items.stream().anyMatch(i -> i.matches(stack));
        }
    }
}

package cam72cam.immersiverailroading.model.components;

import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.model.StockModel;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ComponentProvider {
    public final StockModel<?, ?> model;
    private final Set<String> groups;
    private final List<ModelComponent> components;
    public double internal_model_scale;

    public ComponentProvider(StockModel<?, ?> model, double internal_model_scale) {
        this.model = model;
        this.groups = new HashSet<>(model.groups());
        this.components = new ArrayList<>();
        this.internal_model_scale = internal_model_scale;
    }

    public List<ModelComponent> parse(ModelComponentType... types) {
        return Arrays.stream(types).map(this::parse).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public ModelComponent parse(ModelComponentType type) {
        Set<String> ids = this.modelIDs(type.regex);
        if (!ids.isEmpty()) {
            ModelComponent component = new ModelComponent(type, null, null, this.model, ids);
            this.components.add(component);
            return component;
        }
        return null;
    }

    private Set<String> modelIDs(String pattern) {
        Pattern regex = Pattern.compile(pattern);
        Set<String> modelIDs = this.groups.stream()
                .filter(group -> regex.matcher(group).matches())
                .collect(Collectors.toSet());
        this.groups.removeAll(modelIDs);

        return modelIDs;
    }

    public List<ModelComponent> parse(ModelPosition pos, ModelComponentType... types) {
        return Arrays.stream(types).map(type -> this.parse(type, pos)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public ModelComponent parse(ModelComponentType type, ModelPosition pos) {
        Set<String> ids = this.modelIDs(type.regex.replace("#POS#", pos.toString()).replace("#SIDE#", pos.toString()));
        if (!ids.isEmpty()) {
            ModelComponent component = new ModelComponent(type, pos, null, this.model, ids);
            this.components.add(component);
            return component;
        }
        return null;
    }

    public List<ModelComponent> parseAll(ModelComponentType... types) {
        return Arrays.stream(types)
                .flatMap(type -> this.parseAll(type).stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<ModelComponent> parseAll(ModelComponentType type) {
        return this.modelIDMap(
                type.regex.replace("#ID#", "([\\d]+)")
        ).entrySet().stream().map(e -> {
            ModelComponent component = new ModelComponent(type, null, Integer.parseInt(e.getKey()), this.model, e.getValue());
            this.components.add(component);
            return component;
        }).collect(Collectors.toList());
    }

    private Map<String, Set<String>> modelIDMap(String pattern) {
        Pattern rgx = Pattern.compile(pattern);
        Map<String, Set<String>> modelIDs = this.groups.stream()
                .map(rgx::matcher)
                .filter(Matcher::matches)
                .collect(
                        Collectors.groupingBy(
                                m -> m.group(m.groupCount()),
                                Collectors.mapping(m -> m.group(0), Collectors.toSet())
                        )
                );
        modelIDs.forEach((k, v) -> this.groups.removeAll(v));
        return modelIDs;
    }

    public List<ModelComponent> parseAll(ModelComponentType type, ModelPosition pos) {
        String re = type.regex;
        re = re.replace("#POS#", pos.toString()).replace("#SIDE#", pos.toString());
        if (!re.equals(type.regex)) {
            // POS or SIDE found
            re = re.replace("#ID#", "([\\d]+)");
        } else {
            // Hack pos into #ID# slot
            re = re.replace("#ID#", pos + "_([\\d]+)");
        }
        return this.modelIDMap(re).entrySet().stream().map(e -> {
            ModelComponent component = new ModelComponent(type, pos, Integer.parseInt(e.getKey()), this.model, e.getValue());
            this.components.add(component);
            return component;
        }).collect(Collectors.toList());
    }

    public List<ModelComponent> components() {
        return this.components;
    }

}

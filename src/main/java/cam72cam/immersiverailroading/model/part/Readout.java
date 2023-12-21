package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.gui.overlay.Readouts;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Readout<T extends EntityMovableRollingStock> extends Control<T> {
    private final Map<UUID, Float> positions = new HashMap<>();
    private final Function<T, Float> position;
    private final float rangeMin;
    private final float rangeMax;

    public Readout(ModelComponent part, Function<T, Float> position, ModelState state, double internal_model_scale) {
        super(part, state, internal_model_scale);
        this.position = position;

        float min = 0;
        float max = 1;
        Pattern pattern = Pattern.compile("_RANGE_([^_]*)_([^_]*)");
        for (String modelID : part.modelIDs) {
            Matcher matcher = pattern.matcher(modelID);
            while (matcher.find()) {
                min = Float.parseFloat(matcher.group(1));
                max = Float.parseFloat(matcher.group(2));
            }
        }
        this.rangeMin = min;
        this.rangeMax = max;
    }

    public static <T extends EntityMovableRollingStock> List<Readout<T>> getReadouts(ComponentProvider provider, ModelState state, ModelComponentType type, Readouts value) {
        return provider.parseAll(type)
                .stream()
                .map(p -> new Readout<T>(p, value::getValue, state, provider.internal_model_scale))
                .collect(Collectors.toList());
    }

    public static <T extends EntityMovableRollingStock> List<Readout<T>> getReadouts(ComponentProvider provider, ModelState state, ModelComponentType type, ModelPosition pos, Readouts value) {
        return provider.parseAll(type, pos)
                .stream()
                .map(p -> new Readout<T>(p, value::getValue, state, provider.internal_model_scale))
                .collect(Collectors.toList());
    }

    @Override
    public float getValue(EntityMovableRollingStock stock) {
        float pos = this.positions.getOrDefault(stock.getUUID(), 0f);
        pos = Math.min(1, Math.max(0, (pos - this.rangeMin) / (this.rangeMax - this.rangeMin)));
        pos += this.offset;
        return this.invert ? 1 - pos : pos;
    }

    @Override
    public void effects(T stock) {
        super.effects(stock);
        this.positions.put(stock.getUUID(), this.position.apply(stock));
    }
}

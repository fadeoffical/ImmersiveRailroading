package cam72cam.immersiverailroading.model.animation;

import cam72cam.mod.resource.Identifier;
import util.Matrix4;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AnimatrixSet {
    private final float min;
    private final float max;
    Map<Float, Animatrix> steps;

    public AnimatrixSet(Map<Float, Identifier> input, double internal_model_scale) throws IOException {
        this.steps = new HashMap<>();
        for (Map.Entry<Float, Identifier> entry : input.entrySet()) {
            this.steps.put(entry.getKey(), new Animatrix(entry.getValue().getResourceStream(), internal_model_scale));
        }

        if (this.steps.isEmpty()) {
            throw new RuntimeException("Invalid Animatrix Configuration (empty)");
        }

        this.min = (float) this.steps.keySet().stream().mapToDouble(x -> x).min().getAsDouble();
        this.max = (float) this.steps.keySet().stream().mapToDouble(x -> x).max().getAsDouble();
    }


    public Matrix4 getMatrix(String group, float index, float percent, boolean looping) {
        float min = this.min;
        float max = this.max;

        for (Float step : this.steps.keySet()) {
            if (step < index && step > min) {
                min = step;
            }
            if (step > index && step < max) {
                max = step;
            }
        }

        if (min == max) {
            return this.steps.get(min).getMatrix(group, percent, looping);
        }

        Matrix4 ms = this.steps.get(min).getMatrix(group, percent, looping);
        Matrix4 me = this.steps.get(max).getMatrix(group, percent, looping);
        if (ms == null) {
            return me;
        }
        if (me == null) {
            return ms;
        }
        float lerp = (index - min) / (max - min);
        return ms.slerp(me, lerp);
    }
}

package cam72cam.immersiverailroading.model;

import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.mod.render.obj.OBJRender;
import org.apache.commons.lang3.tuple.Pair;
import util.Matrix4;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ModelState {
    // TODO Lighting rework...
    public static final Pattern lcgPattern = Pattern.compile("_LCG_([^_]+)");
    private static final Map<String, String> lcgCache = new HashMap<>();
    private static final Map<String, Boolean> linvertCache = new HashMap<>();
    private static final Map<String, Boolean> interiorCache = new HashMap<>();
    private static final Map<String, Boolean> fullbrightCache = new HashMap<>();
    private final Animator animator;
    private final GroupAnimator groupAnimator;
    private final GroupVisibility groupVisibility;
    private final Lighter lighter;
    private final List<ModelComponent> components;
    private final List<ModelState> children = new ArrayList<>();

    private ModelState(Animator animator, GroupAnimator groupAnimator, GroupVisibility groupVisibility, Lighter lighter) {
        this.components = new ArrayList<>();
        this.animator = animator;
        this.groupAnimator = groupAnimator;
        this.groupVisibility = groupVisibility;
        this.lighter = lighter;
    }

    public static ModelState construct(Consumer<Builder> settings) {
        Builder builder = new Builder();
        settings.accept(builder);
        return builder.build();
    }

    public ModelState push(Consumer<Builder> fn) {
        Builder builder = new Builder(this);
        fn.accept(builder);
        ModelState created = builder.build();
        this.children.add(created);
        return created;
    }

    public Matrix4 getGroupMatrix(EntityMovableRollingStock stock, String group) {
        Matrix4 groupMatrix = this.groupAnimator != null ? this.groupAnimator.getMatrix(stock, group) : null;
        Matrix4 baseMatrix = this.getMatrix(stock);
        if (groupMatrix == null) {
            return baseMatrix;
        }
        if (baseMatrix == null) {
            return groupMatrix;
        }
        return baseMatrix.copy().multiply(groupMatrix);
    }

    public Matrix4 getMatrix(EntityMovableRollingStock stock) {
        return this.animator != null ? this.animator.getMatrix(stock) : null;
    }

    public void include(ModelComponent component) {
        if (component != null) {
            this.components.add(component);
        }
    }

    public void include(Collection<ModelComponent> components) {
        this.components.addAll(components);
    }

    private boolean hasGroupFlag(String group, String filter) {
        for (String x : group.split("_")) {
            if (x.equals(filter)) {
                return true;
            }
        }
        return false;
    }

    // TODO check performance impact of streams
    public void render(OBJRender.Binding vbo, EntityMovableRollingStock stock, List<ModelComponentType> available) {
        // Get all groups that we can render from components that are available
        List<String> groups = new ArrayList<>();
        for (ModelComponent component : this.components) {
            if (available == null) {
                groups.addAll(component.modelIDs);
            } else if (available.contains(component.type)) {
                available.remove(component.type);
                groups.addAll(component.modelIDs);
            }
        }

        // Filter out groups that aren't currently visible
        if (this.groupVisibility != null) {
            groups = groups.stream().filter(group -> {
                Boolean visible = this.groupVisibility.visible(stock, group);
                return visible == null || visible;
            }).collect(Collectors.toList());
        }

        Matrix4 matrix = this.animator != null ? this.animator.getMatrix(stock) : null;

        Map<String, Matrix4> animatedGroups = new HashMap<>();
        if (this.groupAnimator != null) {
            groups.forEach(group -> {
                Matrix4 groupAnimatorMatrix = this.groupAnimator.getMatrix(stock, group);
                if (groupAnimatorMatrix != null) {
                    animatedGroups.put(group, groupAnimatorMatrix);
                }
            });
        }

        // Required, TODO upstream checking or optional
        LightState lighting = this.lighter.get(stock);
        boolean fullBright = lighting.fullBright != null && lighting.fullBright;
        boolean hasInterior = lighting.hasInterior != null && lighting.hasInterior;


        Map<Pair<Float, Float>, List<String>> levels = new HashMap<>();
        for (String group : groups) {
            if (!lcgCache.containsKey(group)) {
                Matcher matcher = lcgPattern.matcher(group);
                lcgCache.put(group, matcher.find() ? matcher.group(1) : null);
            }
            String lcg = lcgCache.get(group);

            boolean invertGroup = linvertCache.computeIfAbsent(group, g -> this.hasGroupFlag(g, "LINVERT"));
            boolean interiorGroup = interiorCache.computeIfAbsent(group, g -> this.hasGroupFlag(g, "INTERIOR"));
            boolean fullbrightGroup = fullbrightCache.computeIfAbsent(group, g -> this.hasGroupFlag(g, "FULLBRIGHT"));

            Float lcgValue = lcg != null ? stock.getControlPosition(lcg) : null;
            lcgValue = lcgValue == null ? null : invertGroup ? 1 - lcgValue : lcgValue;
            Pair<Float, Float> key = null;

            // TODO additional null checks around lighting fields
            if (lcgValue == null || lcgValue > 0) {
                if (fullBright && fullbrightGroup) {
                    key = Pair.of(1f, 1f);
                } else if (lighting.interiorLight != null) {
                    if (!hasInterior || interiorGroup) {
                        if (lcgValue != null) {
                            key = Pair.of(lighting.interiorLight * lcgValue, lighting.skyLight);
                        } else {
                            key = Pair.of(lighting.interiorLight, lighting.skyLight);
                        }
                    }
                }
            }

            levels.computeIfAbsent(key, p -> new ArrayList<>()).add(group);
        }

        levels.forEach((level, litGroups) -> {
            List<String> animated = animatedGroups.isEmpty() ? Collections.emptyList() :
                    litGroups.stream().filter(g -> animatedGroups.containsKey(g)).collect(Collectors.toList());
            List<String> notAnimated = animatedGroups.isEmpty() ? litGroups :
                    litGroups.stream().filter(g -> !animatedGroups.containsKey(g)).collect(Collectors.toList());

            if (!notAnimated.isEmpty()) {
                vbo.draw(notAnimated, state -> {
                    if (matrix != null) {
                        state.model_view().multiply(matrix);
                    }
                    if (level != null) {
                        state.lightmap(level.getKey(), level.getValue());
                    }
                });
            }
            if (!animated.isEmpty()) {
                animated.forEach(group -> {
                    vbo.draw(Collections.singletonList(group), state -> {
                        if (matrix != null) {
                            state.model_view().multiply(matrix);
                        }
                        state.model_view().multiply(animatedGroups.get(group));
                        if (level != null) {
                            state.lightmap(level.getKey(), level.getValue());
                        }
                    });
                });
            }
        });

        for (ModelState child : this.children) {
            child.render(vbo, stock, available);
        }
    }
    @FunctionalInterface
    public interface Animator {
        default Animator merge(Animator other) {
            return (EntityMovableRollingStock stock) -> {
                Matrix4 ourMatrix = this.getMatrix(stock);
                Matrix4 newMatrix = other.getMatrix(stock);
                if (ourMatrix == null) {
                    return newMatrix;
                }
                if (newMatrix == null) {
                    return ourMatrix;
                }
                return ourMatrix.copy().multiply(newMatrix);
            };
        }

        Matrix4 getMatrix(EntityMovableRollingStock stock);
    }
    @FunctionalInterface
    public interface GroupAnimator {
        default GroupAnimator merge(GroupAnimator other) {
            return (stock, g) -> {
                Matrix4 ourMatrix = this.getMatrix(stock, g);
                Matrix4 newMatrix = other.getMatrix(stock, g);
                if (ourMatrix == null) {
                    return newMatrix;
                }
                if (newMatrix == null) {
                    return ourMatrix;
                }
                return ourMatrix.copy().multiply(newMatrix);
            };
        }

        Matrix4 getMatrix(EntityMovableRollingStock stock, String group);
    }
    @FunctionalInterface
    public interface GroupVisibility {
        default GroupVisibility merge(GroupVisibility other) {
            return (stock, group) -> {
                Boolean ourVisible = this.visible(stock, group);
                Boolean otherVisible = other.visible(stock, group);
                if (ourVisible == null) {
                    return otherVisible;
                }
                if (otherVisible == null) {
                    return ourVisible;
                }
                return ourVisible && otherVisible;// TODO || or && ??
            };
        }

        Boolean visible(EntityMovableRollingStock stock, String group);
    }
    @FunctionalInterface
    public interface Lighter {
        default Lighter merge(Lighter lighter) {
            return stock -> this.get(stock).merge(lighter.get(stock));
        }

        LightState get(EntityMovableRollingStock stock);
    }

    public static class LightState {
        public static final LightState FULLBRIGHT = new LightState(null, null, true, null);
        private final Float interiorLight;
        private final Float skyLight;
        private final Boolean fullBright;
        private final Boolean hasInterior;

        public LightState(Float interiorLight, Float skyLight, Boolean fullBright, Boolean hasInterior) {
            this.interiorLight = interiorLight;
            this.skyLight = skyLight;
            this.fullBright = fullBright;
            this.hasInterior = hasInterior;
        }

        public LightState merge(LightState other) {
            return new LightState(
                    other.interiorLight != null ? other.interiorLight : this.interiorLight,
                    other.skyLight != null ? other.skyLight : this.skyLight,
                    other.fullBright != null ? other.fullBright : this.fullBright,
                    other.hasInterior != null ? other.hasInterior : this.hasInterior
            );
        }
    }

    public static final class Builder {
        public static Consumer<Builder> FULLBRIGHT = builder -> builder.lighter(stock -> LightState.FULLBRIGHT);

        private Animator animator;
        private GroupAnimator groupAnimator;
        private GroupVisibility groupVisibility;
        private Lighter lighter;

        private Builder() {
        }

        private Builder(ModelState parent) {
            this.animator = parent.animator;
            this.groupAnimator = parent.groupAnimator;
            this.groupVisibility = parent.groupVisibility;
            this.lighter = parent.lighter;
        }

        public Builder animator(Animator animator) {
            this.animator = this.animator != null ? this.animator.merge(animator) : animator;
            return this;
        }

        public Builder groupAnimator(GroupAnimator groupAnimator) {
            this.groupAnimator = this.groupAnimator != null ? this.groupAnimator.merge(groupAnimator) : groupAnimator;
            return this;
        }

        public Builder groupVisibility(GroupVisibility groupVisibility) {
            this.groupVisibility = this.groupVisibility != null ? this.groupVisibility.merge(groupVisibility) : groupVisibility;
            return this;
        }

        public Builder lighter(Lighter lighter) {
            this.lighter = this.lighter != null ? this.lighter.merge(lighter) : lighter;
            return this;
        }

        private ModelState build() {
            return new ModelState(this.animator, this.groupAnimator, this.groupVisibility, this.lighter);
        }
    }
}

package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition.ControlSoundsDefinition;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.ModCore;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.model.obj.OBJGroup;
import cam72cam.mod.render.GlobalRender;
import cam72cam.mod.render.opengl.RenderState;
import cam72cam.mod.text.TextColor;
import cam72cam.mod.util.Axis;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.text.WordUtils;
import util.Matrix4;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Control<T extends EntityMovableRollingStock> extends Interactable<T> {
    public final String controlGroup;
    public final String label;
    public final boolean toggle;
    public final boolean press;
    public final boolean global;
    protected final boolean invert;
    protected final float offset;
    protected final ModelState state;
    private final boolean hide;
    private final Vec3d center;
    private final String modelId;
    private final Map<Axis, Float> rotations = new HashMap<>();
    private final Map<Axis, Float> translations = new HashMap<>();
    private final Map<Axis, Float> scales = new HashMap<>();
    private final Map<Axis, Float> scaleRot = new HashMap<>();
    private Vec3d rotationPoint = null;
    private float rotationDegrees = 0;
    /**
     * Client only!
     */
    private Vec3d lastClientLook = null;

    public Control(ModelComponent part, ModelState state, double internal_model_scale) {
        super(part);
        this.controlGroup = part.modelIDs.stream().map(group -> {
            Matcher matcher = Pattern.compile("_CG_([^_]+)").matcher(group);
            return matcher.find() ? matcher.group(1) : null;
        }).filter(Objects::nonNull).findFirst().orElse(part.key);
        this.label = part.modelIDs.stream().map(group -> {
            Matcher matcher = Pattern.compile("_LABEL_([^_]+)").matcher(group);
            return matcher.find() ? matcher.group(1).replaceAll("\\^", " ") : null;
        }).filter(Objects::nonNull).findFirst().orElse(null);
        this.toggle = part.modelIDs.stream()
                .anyMatch(g -> g.contains("_TOGGLE_") || g.startsWith("TOGGLE_") || g.endsWith("_TOGGLE"));
        this.press = part.modelIDs.stream()
                .anyMatch(g -> g.contains("_PRESS_") || g.startsWith("PRESS_") || g.endsWith("_PRESS"));
        this.global = part.modelIDs.stream()
                .anyMatch(g -> g.contains("_GLOBAL_") || g.startsWith("GLOBAL_") || g.endsWith("_GLOBAL"));
        this.invert = part.modelIDs.stream()
                .anyMatch(g -> g.contains("_INVERT_") || g.startsWith("INVERT_") || g.endsWith("_INVERT"));
        this.hide = part.modelIDs.stream()
                .anyMatch(g -> g.contains("_HIDE_") || g.startsWith("HIDE_") || g.endsWith("_HIDE"));
        this.offset = part.modelIDs.stream().map(group -> {
            Matcher matcher = Pattern.compile("_OFFSET_([^_]+)").matcher(group);
            return matcher.find() ? Float.parseFloat(matcher.group(1)) : null;
        }).filter(Objects::nonNull).findFirst().orElse(0f);

        // This is terrible
        String rotpat = part.pos != null && !part.type.regex.contains("#POS#") ?
                part.type.regex.replaceAll("#ID#", part.pos + "_" + part.id + "_ROT") :
                part.pos != null ?
                        part.type.regex.replaceAll("#POS#", part.pos.toString()).replaceAll("#ID#", part.id + "_ROT") :
                        part.type.regex.replaceAll("#ID#", part.id + "_ROT");
        OBJGroup rot = part.groups().stream()
                .filter(g -> Pattern.matches(rotpat, g.name))
                .findFirst().orElse(null);
        if (rot != null && rot.normal != null) {
            this.rotationPoint = rot.max.add(rot.min).scale(0.5);
            String[] split = rot.name.split("_");
            int idx = ArrayUtils.indexOf(split, "ROT");
            if (idx != ArrayUtils.INDEX_NOT_FOUND) {
                String degrees = split[idx + 1];
                try {
                    this.rotationDegrees = Float.parseFloat(degrees);
                } catch (NumberFormatException e) {
                    ModCore.error("Unable to parse rotation point '%s': %s", rot.name, e);
                }
            }

            this.rotations.put(Axis.X, (float) rot.normal.x);
            this.rotations.put(Axis.Y, (float) rot.normal.y);
            this.rotations.put(Axis.Z, (float) rot.normal.z);

            List<Vec3d> nonRotGroups = part.groups()
                    .stream()
                    .filter(g -> !g.name.contains("_ROT"))
                    .map(g -> g.max.add(g.min).scale(0.5))
                    .collect(Collectors.toList());
            this.center = nonRotGroups.isEmpty() ? part.center : nonRotGroups.stream()
                    .reduce(Vec3d.ZERO, Vec3d::add)
                    .scale(1.0 / nonRotGroups.size());
        } else {
            this.center = part.center;
        }

        Pattern pattern = Pattern.compile("TL_([^_]*)_([^_])");
        for (String modelID : part.modelIDs) {
            Matcher matcher = pattern.matcher(modelID);
            while (matcher.find()) {
                this.translations.put(Axis.valueOf(matcher.group(2)), Float.parseFloat(matcher.group(1)) * (float) internal_model_scale);
            }
        }
        pattern = Pattern.compile("SCALE_([^_]*)_([^_]+)");
        for (String modelID : part.modelIDs) {
            Matcher matcher = pattern.matcher(modelID);
            while (matcher.find()) {
                if (matcher.group(2).startsWith("R")) {
                    this.scaleRot.put(Axis.valueOf(matcher.group(2).substring(1)), Float.parseFloat(matcher.group(1)));
                } else {
                    this.scales.put(Axis.valueOf(matcher.group(2)), Float.parseFloat(matcher.group(1)));
                }
            }
        }

        if (this.hide) {
            state = state.push(builder ->
                    builder.groupVisibility((ModelState.GroupVisibility) (stock, group) -> this.getValue(stock) != 1)
            );
        }

        if (!(this.rotationPoint == null && this.translations.isEmpty() && this.scales.isEmpty())) {
            state = state.push(builder -> {
                builder.groupAnimator((ModelState.GroupAnimator) (stock, group) -> {
                    float valuePercent = this.getValue(stock);

                    Matrix4 m = new Matrix4();

                    for (Map.Entry<Axis, Float> entry : this.translations.entrySet()) {
                        Axis axis = entry.getKey();
                        Float val = entry.getValue();
                        m = m.translate(
                                axis == Axis.X ? val * valuePercent : 0,
                                axis == Axis.Y ? val * valuePercent : 0,
                                axis == Axis.Z ? val * valuePercent : 0
                        );
                    }

                    if (this.rotationPoint != null) {
                        m = m.translate(this.rotationPoint.x, this.rotationPoint.y, this.rotationPoint.z);
                        m = m.rotate(
                                Math.toRadians(valuePercent * this.rotationDegrees),
                                this.rotations.getOrDefault(Axis.X, 0f),
                                this.rotations.getOrDefault(Axis.Y, 0f),
                                this.rotations.getOrDefault(Axis.Z, 0f)
                        );
                        m = m.translate(-this.rotationPoint.x, -this.rotationPoint.y, -this.rotationPoint.z);
                    }
                    if (!this.scales.isEmpty()) {
                        m = m.translate(part.center.x, part.center.y, part.center.z);
                        for (Map.Entry<Axis, Float> entry : this.scaleRot.entrySet()) {
                            Axis axis = entry.getKey();
                            m = m.rotate(Math.toRadians(
                                            entry.getValue()),
                                    axis == Axis.X ? 1 : 0,
                                    axis == Axis.Y ? 1 : 0,
                                    axis == Axis.Z ? 1 : 0
                            );
                        }
                        m = m.scale(
                                this.scales.containsKey(Axis.X) ? (1 - this.scales.get(Axis.X)) + (this.scales.get(Axis.X) * valuePercent) : 1,
                                this.scales.containsKey(Axis.Y) ? (1 - this.scales.get(Axis.Y)) + (this.scales.get(Axis.Y) * valuePercent) : 1,
                                this.scales.containsKey(Axis.Z) ? (1 - this.scales.get(Axis.Z)) + (this.scales.get(Axis.Z) * valuePercent) : 1
                        );
                        for (Map.Entry<Axis, Float> entry : this.scaleRot.entrySet()) {
                            Axis axis = entry.getKey();
                            m = m.rotate(Math.toRadians(
                                            -entry.getValue()),
                                    axis == Axis.X ? 1 : 0,
                                    axis == Axis.Y ? 1 : 0,
                                    axis == Axis.Z ? 1 : 0
                            );
                        }
                        m = m.translate(-part.center.x, -part.center.y, -part.center.z);
                    }
                    return m;
                });
            });
        }
        this.state = state;
        this.state.include(part);
        this.modelId = part.modelIDs.stream().findFirst().get();
    }

    public float getValue(EntityMovableRollingStock stock) {
        float pos = stock.getControlPosition(this) + this.offset;
        return (this.invert ? 1 - pos : pos) - (this.part.type == ModelComponentType.REVERSER_X || this.part.type == ModelComponentType.THROTTLE_BRAKE_X ? 0.5f : 0);
    }

    public static <T extends EntityMovableRollingStock> List<Control<T>> get(ComponentProvider provider, ModelState state, ModelComponentType type, ModelPosition pos) {
        return provider.parseAll(type, pos)
                .stream()
                .map(part1 -> new Control<T>(part1, state, provider.internal_model_scale))
                .collect(Collectors.toList());
    }

    public static <T extends EntityMovableRollingStock> List<Control<T>> get(ComponentProvider provider, ModelState state, ModelComponentType type) {
        return provider.parseAll(type)
                .stream()
                .map(part1 -> new Control<T>(part1, state, provider.internal_model_scale))
                .collect(Collectors.toList());
    }

    public void postRender(T stock, RenderState state) {
        if (!ConfigGraphics.interactiveComponentsOverlay) {
            return;
        }

        if (!stock.playerCanDrag(MinecraftClient.getPlayer(), this)) {
            return;
        }

        if (MinecraftClient.getPlayer().getPosition().distanceTo(stock.getPosition()) > stock.getDefinition()
                .getLength(stock.getGauge())) {
            return;
        }

        boolean isPressed = stock.getControlPressed(this);
        if (!isPressed && Math.abs(this.lookedAt - stock.getWorld().getTicks()) > 2) {
            return;
        }

        Matrix4 m = this.state.getGroupMatrix(stock, this.modelId);
        Vec3d pos = m == null ? this.center : m.apply(this.center);
        String labelstate = "";
        float percent = this.getValue(stock) - this.offset;
        switch (this.part.type) {
            case TRAIN_BRAKE_X:
            case INDEPENDENT_BRAKE_X:
                if (!stock.getDefinition().isLinearBrakeControl()) {
                    break;
                }
                // Fallthrough
            case THROTTLE_X:
            case REVERSER_X:
            case THROTTLE_BRAKE_X:
            case BELL_CONTROL_X:
            case WHISTLE_CONTROL_X:
            case HORN_CONTROL_X:
            case ENGINE_START_X:
            case CYLINDER_DRAIN_CONTROL_X:
                if (this.part.type == ModelComponentType.REVERSER_X) {
                    percent *= -2;
                }
                if (this.toggle || this.press) {
                    labelstate = percent == 1 ? " (On)" : " (Off)";
                } else {
                    labelstate = String.format(" (%d%%)", (int) (percent * 100));
                }
                break;
            default:
                if (this.label == null || this.label.trim().isEmpty()) {
                    return;
                }
                if (this.toggle || this.press) {
                    labelstate = percent == 1 ? " (On)" : " (Off)";
                } else {
                    labelstate = String.format(" (%d%%)", (int) (percent * 100));
                }
        }
        String str = (this.label != null ? this.label : formatLabel(this.part.type)) + labelstate;
        if (isPressed) {
            str = TextColor.BOLD.wrap(str);
        }
        GlobalRender.drawText(str, state, pos, 0.2f, 180 - stock.getRotationYaw() - 90);
    }

    private static String formatLabel(ModelComponentType label) {
        return WordUtils.capitalizeFully(label.name()
                .replace("_X", "")
                .replaceAll("_CONTROL", "")
                .replaceAll("_", " ")
                .toLowerCase(Locale.ROOT));
    }

    public float clientMovementDelta(Player player, EntityRollingStock stockRaw) {
        /*
          -X
        -Z * +Z
          +X
         */

        if (this.press) {
            return 1;
        }

        T stock = (T) stockRaw;

        float delta = 0;

        Vec3d partPos = this.transform(this.center, stock).subtract(stock.getPosition());
        Vec3d current = player.getPositionEyes().subtract(stock.getPosition());
        Vec3d look = player.getLookVector();
        // Rescale along look vector
        double len = 1 + current.add(look).distanceTo(partPos);
        current = current.add(look.scale(len));
        current = current.rotateYaw(stock.getRotationYaw());

        if (this.lastClientLook != null) {
            Vec3d movement = current.subtract(this.lastClientLook);
            movement = movement.rotateYaw(-stock.getRotationYaw());
            float applied = Math.min(0.1f, (float) (movement.length() * 1));
            if (this.rotationDegrees <= 180) {
                float value = stock.getControlPosition(this);//getValue(stock);  // Does this work with invert???
                Vec3d grabComponent = this.transform(this.center, stock).add(movement);

                stock.setControlPosition(this, value + applied);
                Vec3d grabComponentNext = this.transform(this.center, stock);
                stock.setControlPosition(this, value - applied);
                Vec3d grabComponentPrev = this.transform(this.center, stock);

                stock.setControlPosition(this, value);

                if (grabComponent.distanceTo(grabComponentNext) < grabComponent.distanceTo(grabComponentPrev)) {
                    delta += applied * movement.length() / grabComponent.distanceTo(grabComponentNext);
                } else {
                    delta -= applied * movement.length() / grabComponent.distanceTo(grabComponentPrev);
                }
            } else {
                // hack spinning wheels
                if (movement.x * (1 - this.rotationPoint.x) + movement.y * (1 - this.rotationPoint.y) + movement.z * (1 - this.rotationPoint.z) > 0) {
                    delta += applied;
                } else {
                    delta -= applied;
                }
            }
        }
        this.lastClientLook = current;

        return Float.isNaN(delta) ? 0 : delta;
    }

    public void stopClientDragging() {
        this.lastClientLook = null;
    }

    public void effects(T stock) {
        ControlSoundsDefinition sounds = stock.getDefinition()
                .getControlSound(this.part.type.name().replace("_X", "_" + this.part.id));
        if (sounds != null) {
            sounds.effects(stock, stock.getControlPressed(this), stock.getControlPosition(this), this.center(stock));
        }
    }

    @Override
    public Vec3d center(EntityRollingStock stock) {
        return this.transform(this.part.center, (T) stock);
    }

    @Override
    public IBoundingBox getBoundingBox(EntityRollingStock stock) {
        return IBoundingBox.from(
                this.transform(this.part.min, (T) stock),
                this.transform(this.part.max, (T) stock)
        );
    }

    public Vec3d transform(Vec3d point, T stock) {
        Matrix4 m = this.state.getGroupMatrix(stock, this.modelId);
        if (m == null) {
            m = stock.getModelMatrix();
        } else {
            m = stock.getModelMatrix().multiply(m);
        }
        return m.apply(point);
    }

    public void removed(T stock) {
        ControlSoundsDefinition sounds = stock.getDefinition()
                .getControlSound(this.part.type.name().replace("_X", "_" + this.part.id));
        if (sounds != null) {
            sounds.removed(stock);
        }
    }
}

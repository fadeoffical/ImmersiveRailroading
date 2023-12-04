package cam72cam.immersiverailroading.model.part;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.model.ModelState;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition.LightDefinition;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.render.Light;
import cam72cam.mod.render.opengl.BlendMode;
import cam72cam.mod.render.opengl.DirectDraw;
import cam72cam.mod.render.opengl.RenderState;
import cam72cam.mod.render.opengl.Texture;
import cam72cam.mod.resource.Identifier;
import util.Matrix4;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cam72cam.immersiverailroading.model.ModelState.lcgPattern;

public class LightFlare<T extends EntityMoveableRollingStock> {
    private static final Pattern rgb = Pattern.compile(String.format(".*_0[xX](%s%<s)(%<s%<s)(%<s%<s).*", "[0-9A-Fa-f]"));
    private final ModelComponent component;
    private final boolean forward;
    private final Map<UUID, List<Light>> castLights = new HashMap<>();
    private final Map<UUID, List<Vec3d>> castPositions = new HashMap<>();
    private final float red;
    private final float green;
    private final float blue;
    private final Identifier lightTex;
    private final int blinkIntervalTicks;
    private final int blinkOffsetTicks;
    private final boolean castsLights;
    private final boolean blinkFullBright;
    private final String controlGroup;
    private final boolean invert;
    private final Function<T, Matrix4> location;
    private float redReverse;
    private float greenReverse;
    private float blueReverse;

    private LightFlare(EntityRollingStockDefinition def, ModelState state, ModelComponent component) {
        this.component = component;
        this.forward = component.center.x < 0;
        Matcher rgbValues = component.modelIDs.stream()
                .map(rgb::matcher)
                .filter(Matcher::matches)
                .findFirst().orElse(null);
        if (rgbValues != null) {
            this.red = Integer.parseInt(rgbValues.group(1), 16) / 255f;
            this.green = Integer.parseInt(rgbValues.group(2), 16) / 255f;
            this.blue = Integer.parseInt(rgbValues.group(3), 16) / 255f;
        } else {
            this.red = 1;
            this.green = 1;
            this.blue = 1;
        }
        this.redReverse = this.red;
        this.greenReverse = this.green;
        this.blueReverse = this.blue;
        this.controlGroup = component.modelIDs.stream()
                .map(lcgPattern::matcher).filter(Matcher::find).map(m -> m.group(1)).findFirst().orElse(null);

        this.invert = component.modelIDs.stream()
                .anyMatch(g -> g.contains("_LINVERT_") || g.startsWith("LINVERT_") || g.endsWith("_LINVERT"));

        // This is bad...
        LightDefinition config = def.getLight(component.type.toString()
                .replace("_X", "_" + component.id)
                .replace("_POS_", "_" + component.pos + "_")
        );

        if (config != null) {
            this.lightTex = config.lightTex;
            this.blinkIntervalTicks = (int) (config.blinkIntervalSeconds * 20);
            this.blinkOffsetTicks = (int) (config.blinkOffsetSeconds * 20);
            this.blinkFullBright = config.blinkFullBright;
            this.castsLights = config.castsLight;
            if (config.reverseColor != null) {
                rgbValues = rgb.matcher("_" + config.reverseColor);
                if (rgbValues.matches()) {
                    this.redReverse = Integer.parseInt(rgbValues.group(1), 16) / 255f;
                    this.greenReverse = Integer.parseInt(rgbValues.group(2), 16) / 255f;
                    this.blueReverse = Integer.parseInt(rgbValues.group(3), 16) / 255f;
                }
            }
        } else {
            this.lightTex = LightDefinition.default_light_tex;
            this.blinkIntervalTicks = 0;
            this.blinkOffsetTicks = 0;
            this.blinkFullBright = true;
            this.castsLights = true;
        }

        ModelState mystate = state.push(builder -> builder
                .add((ModelState.Lighter) (stock) ->
                        new ModelState.LightState(null, null, this.blinkFullBright ? !this.isBlinkOff(stock) : !this.isLightOff(stock), null)
                )
        );
        mystate.include(component);
        this.location = mystate::getMatrix;
    }

    private boolean isBlinkOff(EntityRollingStock stock) {
        return this.isLightOff(stock) || this.blinkIntervalTicks > 0 && (stock.getTickCount() + this.blinkOffsetTicks) % (this.blinkIntervalTicks * 2) > this.blinkIntervalTicks;
    }

    private boolean isLightOff(EntityRollingStock stock) {
        return !stock.externalLightsEnabled() || (this.controlGroup != null && stock.getControlPosition(this.controlGroup) == (this.invert ? 1 : 0));
    }

    public static <T extends EntityMoveableRollingStock> List<LightFlare<T>> get(EntityRollingStockDefinition def, ComponentProvider provider, ModelState state, ModelComponentType type) {
        return provider.parseAll(type).stream().map(c -> new LightFlare<T>(def, state, c)).collect(Collectors.toList());
    }

    public static <T extends EntityMoveableRollingStock> List<LightFlare<T>> get(EntityRollingStockDefinition def, ComponentProvider provider, ModelState state, ModelComponentType type, ModelPosition pos) {
        return provider.parseAll(type, pos)
                .stream()
                .map(c -> new LightFlare<T>(def, state, c))
                .collect(Collectors.toList());
    }

    public void postRender(T stock, RenderState state) {
        //GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        //GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        boolean reverse = stock.getCurrentSpeed().minecraft() < 0;
        float red = reverse ? this.redReverse : this.red;
        float green = reverse ? this.greenReverse : this.green;
        float blue = reverse ? this.blueReverse : this.blue;

        if (red == 0 && green == 0 && blue == 0) {
            return;
        }

        if (this.isBlinkOff(stock)) {
            return;
        }

        Vec3d flareOffset = new Vec3d(this.forward ? this.component.min.x - 0.02 : this.component.max.x + 0.02, (this.component.min.y + this.component.max.y) / 2, (this.component.min.z + this.component.max.z) / 2);
        if (this.location != null) {
            // TODO this does not actually work
            Matrix4 m = this.location.apply(stock);
            if (m != null) {
                flareOffset = m.apply(flareOffset);
            }
        }

        Vec3d playerOffset = VecUtil.rotateWrongYaw(stock.getPosition()
                        .subtract(MinecraftClient.getPlayer().getPosition()), 180 - (stock.getRotationYaw())).
                subtract(flareOffset).scale(this.forward ? 1 : -1);

        int viewAngle = 45;
        float intensity = 1 - Math.abs(Math.max(-viewAngle, Math.min(viewAngle, VecUtil.toWrongYaw(playerOffset) - 90))) / viewAngle;
        intensity *= Math.abs(playerOffset.x / (50 * stock.gauge.scale()));
        intensity = Math.min(intensity, 1.5f);

        state = state.clone()
                .texture(Texture.wrap(this.lightTex))
                .lightmap(1, 1)
                .depth_test(true)
                .depth_mask(false)
                .alpha_test(false).blend(new BlendMode(BlendMode.GL_SRC_ALPHA, BlendMode.GL_ONE_MINUS_SRC_ALPHA));

        if (intensity > 0.01) {
            RenderState matrix = state.clone();
            matrix.translate(flareOffset.x - (intensity / 2) * (this.forward ? 3 : -3), flareOffset.y, flareOffset.z);
            matrix.rotate(90, 0, 1, 0);
            double scale = Math.max((this.component.max.z - this.component.min.z) * 0.5, intensity * 2);
            matrix.scale(scale, scale, scale);
            if (!this.forward) {
                matrix.rotate(180, 0, 1, 0);
            }

            matrix.color(red, green, blue, 1 - (intensity / 3f));

            DirectDraw buffer = new DirectDraw();
            buffer.vertex(-1, -1, 0).uv(0, 0);
            buffer.vertex(-1, 1, 0).uv(0, 1);
            buffer.vertex(1, 1, 0).uv(1, 1);
            buffer.vertex(1, -1, 0).uv(1, 0);
            buffer.draw(matrix);
        }

        RenderState matrix = state.clone();
        matrix.translate(flareOffset.x, flareOffset.y, flareOffset.z);
        matrix.rotate(90, 0, 1, 0);
        double scale = (this.component.max.z - this.component.min.z) / 1.5;
        matrix.scale(scale, scale, scale);
        if (!this.forward) {
            matrix.rotate(180, 0, 1, 0);
        }
        matrix.color((float) Math.sqrt(red), (float) Math.sqrt(green), (float) Math.sqrt(blue), 1 - (intensity / 3f));

        DirectDraw buffer = new DirectDraw();
        buffer.vertex(-1, -1, 0).uv(0, 0);
        buffer.vertex(-1, 1, 0).uv(0, 1);
        buffer.vertex(1, 1, 0).uv(1, 1);
        buffer.vertex(1, -1, 0).uv(1, 0);
        buffer.draw(matrix);
    }

    public void effects(T stock) {
        if (!this.castsLights) {
            return;
        }

        if (!Light.enabled() || this.isBlinkOff(stock)) {
            this.removed(stock);
            return;
        }

        int lightDistance = (int) (15 * stock.gauge.scale());
        if (!this.castLights.containsKey(stock.getUUID())) {
            Vec3d flareOffset = new Vec3d(-this.component.min.x, (this.component.min.y + this.component.max.y) / 2, (this.component.min.z + this.component.max.z) / 2).scale(stock.gauge.scale());

            this.castLights.put(stock.getUUID(), new ArrayList<>());
            this.castPositions.put(stock.getUUID(), new ArrayList<>());
            for (int i = 0; i < lightDistance; i++) {
                for (int j = 0; j < 5; j++) {
                    this.castLights.get(stock.getUUID())
                            .add(new Light(stock.getWorld(), stock.getPosition(), 1 - i / (float) lightDistance));
                }
                double xOff = 4;
                double yOff = -(i / (float) lightDistance) * flareOffset.y;
                int sign = this.forward ? 1 : -1;
                this.castPositions.get(stock.getUUID()).add(flareOffset.add((i * 2 + xOff) * sign, 0 + yOff, 0));
                this.castPositions.get(stock.getUUID()).add(flareOffset.add((i * 2 + xOff) * sign, i / 2f + yOff, 0));
                this.castPositions.get(stock.getUUID()).add(flareOffset.add((i * 2 + xOff) * sign, -i / 2f + yOff, 0));
                this.castPositions.get(stock.getUUID()).add(flareOffset.add((i * 2 + xOff) * sign, 0 + yOff, i / 2f));
                this.castPositions.get(stock.getUUID()).add(flareOffset.add((i * 2 + xOff) * sign, 0 + yOff, -i / 2f));
            }
        }
        Vec3d[] collided = new Vec3d[5];
        Vec3d nop = null;
        for (int i = 0; i < this.castLights.get(stock.getUUID()).size(); i++) {
            if (collided[i % 5] != null) {
                this.castLights.get(stock.getUUID()).get(i).setPosition(collided[i % 5]);
            } else {
                Vec3d cpos = this.castPositions.get(stock.getUUID()).get(i);
                if (this.location != null) {
                    Matrix4 m = this.location.apply(stock);
                    if (m != null) {
                        cpos = m.apply(cpos);
                    }
                }
                Vec3d pos = stock.getPosition().add(VecUtil.rotateWrongYaw(cpos, stock.getRotationYaw()));
                if (nop == null) {
                    nop = pos;
                }
                if (!stock.getWorld().isReplaceable(new Vec3i(pos).up())) {
                    collided[i % 5] = nop;
                    this.castLights.get(stock.getUUID()).get(i).setPosition(nop);
                } else {
                    this.castLights.get(stock.getUUID()).get(i).setPosition(pos);
                }
            }
        }
    }

    public void removed(T stock) {
        if (this.castLights.containsKey(stock.getUUID())) {
            this.castLights.get(stock.getUUID()).forEach(Light::remove);
            this.castLights.remove(stock.getUUID());
            this.castPositions.remove(stock.getUUID());
        }
    }
}

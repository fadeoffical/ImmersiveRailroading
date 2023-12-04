package cam72cam.immersiverailroading.model;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.gui.overlay.Readouts;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.model.animation.StockAnimation;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.model.part.*;
import cam72cam.immersiverailroading.model.part.TrackFollower.TrackFollowers;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition.SoundDefinition;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.model.obj.OBJModel;
import cam72cam.mod.render.OptiFine;
import cam72cam.mod.render.obj.OBJRender;
import cam72cam.mod.render.opengl.RenderState;
import util.Matrix4;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class StockModel<ENTITY extends EntityMoveableRollingStock, DEFINITION extends EntityRollingStockDefinition> extends OBJModel {
    public static final int LOD_LARGE = 1024;
    public static final int LOD_SMALL = 512;
    public final List<ModelComponent> allComponents;
    protected final List<Door<ENTITY>> doors;
    protected final List<Control<ENTITY>> controls;
    protected final List<Readout<ENTITY>> gauges;
    protected final List<Seat<ENTITY>> seats;
    private final DEFINITION def;
    private final TrackFollowers frontTrackers;
    private final TrackFollowers rearTrackers;
    private final List<StockAnimation> animations;
    private final float sndRand;
    private final PartSound wheel_sound;
    private final PartSound slidingSound;
    private final FlangeSound flangeSound;
    private final SwaySimulator sway;
    protected ModelState base;
    protected ModelState rocking;
    protected ModelState front;
    protected ModelState frontRocking;
    protected ModelState rear;
    protected ModelState rearRocking;
    protected Frame frame;
    protected Bogey bogeyFront;
    protected Bogey bogeyRear;
    protected List<LightFlare<ENTITY>> headlights;
    private ModelComponent shell;
    private final ModelComponent remaining;
    private int lod_level = LOD_LARGE;
    private int lod_tick = 0;

    public StockModel(DEFINITION def) throws Exception {
        super(def.modelLoc, def.darken, def.internal_model_scale, def.textureNames.keySet(), ConfigGraphics.textureCacheSeconds, i -> {
            List<Integer> lodSizes = new ArrayList<>();
            lodSizes.add(LOD_LARGE);
            lodSizes.add(LOD_SMALL);
            return lodSizes;
        });

        this.def = def;
        boolean hasInterior = this.groups().stream().anyMatch(x -> x.contains("INTERIOR"));

        this.doors = new ArrayList<>();
        this.seats = new ArrayList<>();
        this.controls = new ArrayList<>();
        this.gauges = new ArrayList<>();
        this.headlights = new ArrayList<>();

        ModelState.LightState base = new ModelState.LightState(null, null, null, hasInterior);

        float interiorLight = def.interiorLightLevel();
        ModelState.Lighter interiorLit = stock -> {
            if (!stock.internalLightsEnabled()) {
                return base;
            }
            float blockLight = stock.getWorld().getBlockLightLevel(stock.getBlockPosition());
            float skyLight = stock.getWorld().getSkyLightLevel(stock.getBlockPosition());
            boolean brighter = blockLight < interiorLight;
            return base.merge(new ModelState.LightState(brighter ? interiorLight : null, brighter ? skyLight : null, true, null));
        };

        this.animations = new ArrayList<>();
        for (EntityRollingStockDefinition.AnimationDefinition animDef : def.animations) {
            if (animDef.valid()) {
                this.animations.add(new StockAnimation(animDef, def.internal_model_scale));
            }
        }
        ModelState.GroupAnimator animators = (stock, group) -> {
            Matrix4 m = null;
            for (StockAnimation animation : this.animations) {
                Matrix4 found = animation.getMatrix(stock, group);
                if (found != null) {
                    if (m == null) {
                        m = found;
                    } else {
                        m.multiply(found);
                    }
                }
            }
            return m;
        };
        this.base = ModelState.construct(settings -> settings.add(animators).add(interiorLit));

        ComponentProvider provider = new ComponentProvider(this, def.internal_model_scale);
        this.initStates();
        this.parseControllable(provider, def);

        // Shay Hack...
        // A proper dependency tree would be ideal...
        this.bogeyFront = Bogey.get(provider, this.front, this.unifiedBogies(), ModelPosition.FRONT);
        this.bogeyRear = Bogey.get(provider, this.rear, this.unifiedBogies(), ModelPosition.REAR);

        this.parseComponents(provider, def);
        provider.parse(ModelComponentType.IMMERSIVERAILROADING_BASE_COMPONENT);
        this.remaining = provider.parse(ModelComponentType.REMAINING);
        this.rocking.include(this.remaining);
        this.allComponents = provider.components();

        this.frontTrackers = new TrackFollowers(s -> new TrackFollower(s, this.bogeyFront != null ? this.bogeyFront.bogey : null, this.bogeyFront != null ? this.bogeyFront.wheels : null, true));
        this.rearTrackers = new TrackFollowers(s -> new TrackFollower(s, this.bogeyRear != null ? this.bogeyRear.bogey : null, this.bogeyRear != null ? this.bogeyRear.wheels : null, false));

        this.sndRand = (float) Math.random() / 10;
        this.wheel_sound = new PartSound(new SoundDefinition(def.wheel_sound), true, 40, ConfigSound.SoundCategories.RollingStock::wheel);
        this.slidingSound = new PartSound(new SoundDefinition(def.sliding_sound), true, 40, ConfigSound.SoundCategories.RollingStock::sliding);
        this.flangeSound = new FlangeSound(def.flange_sound, true, 40);
        this.sway = new SwaySimulator();
    }

    protected void initStates() {
        this.rocking = this.addRoll(this.base);
        this.front = this.base.push(settings -> settings.add(this::getFrontBogeyMatrix));
        this.frontRocking = this.addRoll(this.front);
        this.rear = this.base.push(settings -> settings.add(this::getRearBogeyMatrix));
        this.rearRocking = this.addRoll(this.rear);
    }

    protected void parseControllable(ComponentProvider provider, DEFINITION def) {
        this.gauges.addAll(Readout.getReadouts(provider, this.frontRocking, ModelComponentType.COUPLED_X, ModelPosition.BOGEY_FRONT, Readouts.COUPLED_FRONT));
        this.gauges.addAll(Readout.getReadouts(provider, this.rearRocking, ModelComponentType.COUPLED_X, ModelPosition.BOGEY_REAR, Readouts.COUPLED_REAR));
        this.gauges.addAll(Readout.getReadouts(provider, this.rocking, ModelComponentType.COUPLED_X, ModelPosition.FRONT, Readouts.COUPLED_FRONT));
        this.gauges.addAll(Readout.getReadouts(provider, this.rocking, ModelComponentType.COUPLED_X, ModelPosition.REAR, Readouts.COUPLED_REAR));

        this.addControl(provider, ModelComponentType.COUPLER_ENGAGED_X);

        if (def.hasIndependentBrake()) {
            this.addGauge(provider, ModelComponentType.GAUGE_INDEPENDENT_BRAKE_X, Readouts.INDEPENDENT_BRAKE);
        }
        this.addGauge(provider, ModelComponentType.BRAKE_PRESSURE_X, Readouts.BRAKE_PRESSURE);
        this.addControl(provider, ModelComponentType.WINDOW_X);
        this.addControl(provider, ModelComponentType.WIDGET_X);

        if (def.hasIndependentBrake()) {
            this.addControl(provider, ModelComponentType.INDEPENDENT_BRAKE_X);
        }

        this.addDoor(provider);
        this.seats.addAll(Seat.get(provider, this.rocking));

        this.addHeadlight(def, provider, ModelComponentType.HEADLIGHT_X);
    }

    protected boolean unifiedBogies() {
        return true;
    }

    protected void parseComponents(ComponentProvider provider, DEFINITION def) {
        this.frame = new Frame(provider, this.base, this.rocking, def.defID, def.getValveGear());
        this.shell = provider.parse(ModelComponentType.SHELL);
        this.rocking.include(this.shell);
    }

    public ModelState addRoll(ModelState state) {
        return state.push(builder -> builder.add((ModelState.Animator) stock ->
                new Matrix4().rotate(Math.toRadians(this.sway.getRollDegrees(stock)), 1, 0, 0)));
    }

    // TODO invert -> reinvert sway
    private Matrix4 getFrontBogeyMatrix(EntityMoveableRollingStock stock) {
        return this.frontTrackers.get(stock).getMatrix();
    }

    private Matrix4 getRearBogeyMatrix(EntityMoveableRollingStock stock) {
        return this.rearTrackers.get(stock).getMatrix();
    }

    protected void addControl(ComponentProvider provider, ModelComponentType type) {
        this.controls.addAll(Control.get(provider, this.frontRocking, type, ModelPosition.BOGEY_FRONT));
        this.controls.addAll(Control.get(provider, this.rearRocking, type, ModelPosition.BOGEY_REAR));
        this.controls.addAll(Control.get(provider, this.rocking, type, ModelPosition.FRONT));
        this.controls.addAll(Control.get(provider, this.rocking, type, ModelPosition.REAR));
        this.controls.addAll(Control.get(provider, this.rocking, type));
    }

    protected void addGauge(ComponentProvider provider, ModelComponentType type, Readouts value) {
        this.gauges.addAll(Readout.getReadouts(provider, this.frontRocking, type, ModelPosition.BOGEY_FRONT, value));
        this.gauges.addAll(Readout.getReadouts(provider, this.rearRocking, type, ModelPosition.BOGEY_REAR, value));
        this.gauges.addAll(Readout.getReadouts(provider, this.rocking, type, ModelPosition.FRONT, value));
        this.gauges.addAll(Readout.getReadouts(provider, this.rocking, type, ModelPosition.REAR, value));
        this.gauges.addAll(Readout.getReadouts(provider, this.rocking, type, value));
    }

    protected void addDoor(ComponentProvider provider) {
        this.doors.addAll(Door.get(provider, this.frontRocking, ModelPosition.BOGEY_FRONT));
        this.doors.addAll(Door.get(provider, this.rearRocking, ModelPosition.BOGEY_REAR));
        this.doors.addAll(Door.get(provider, this.rocking));
    }

    protected void addHeadlight(DEFINITION def, ComponentProvider provider, ModelComponentType type) {
        this.headlights.addAll(LightFlare.get(def, provider, this.frontRocking, type, ModelPosition.BOGEY_FRONT));
        this.headlights.addAll(LightFlare.get(def, provider, this.rearRocking, type, ModelPosition.BOGEY_REAR));
        this.headlights.addAll(LightFlare.get(def, provider, this.rocking, type));
    }

    public final void onClientTick(EntityMoveableRollingStock stock) {
        this.effects((ENTITY) stock);
    }

    protected void effects(ENTITY stock) {
        this.headlights.forEach(x -> x.effects(stock));
        this.controls.forEach(c -> c.effects(stock));
        this.doors.forEach(c -> c.effects(stock));
        this.gauges.forEach(c -> c.effects(stock));
        this.animations.forEach(c -> c.effects(stock));


        float adjust = (float) Math.abs(stock.getCurrentSpeed().metric()) / 300;
        float pitch = adjust + 0.7f;
        if (stock.getDefinition().shouldScalePitch()) {
            // TODO this is probably wrong...
            pitch /= stock.gauge.scale();
        }
        float volume = 0.01f + adjust;

        this.wheel_sound.effects(stock, Math.abs(stock.getCurrentSpeed().metric()) > 1 ? volume : 0, pitch + this.sndRand);
        this.slidingSound.effects(stock, stock.sliding ? Math.min(1, adjust * 4) : 0);
        this.flangeSound.effects(stock);
        this.sway.effects(stock);
    }

    public final void onClientRemoved(EntityMoveableRollingStock stock) {
        this.removed((ENTITY) stock);
    }

    protected void removed(ENTITY stock) {
        this.headlights.forEach(x -> x.removed(stock));
        this.controls.forEach(c -> c.removed(stock));
        this.doors.forEach(c -> c.removed(stock));
        this.gauges.forEach(c -> c.removed(stock));
        this.animations.forEach(c -> c.removed(stock));

        this.wheel_sound.removed(stock);
        this.slidingSound.removed(stock);
        this.flangeSound.removed(stock);
        this.sway.removed(stock);
    }

    public final void render(EntityMoveableRollingStock stock, RenderState state, float partialTicks) {
        List<ModelComponentType> available = stock.isBuilt() ? null : stock.getItemComponents()
                .stream().flatMap(x -> x.render.stream())
                .collect(Collectors.toList());

        state.lighting(true)
                .cull_face(false)
                .rescale_normal(true)
                .scale(stock.gauge.scale(), stock.gauge.scale(), stock.gauge.scale());

        if ((ConfigGraphics.OptifineEntityShaderOverrideAll || !this.normals.isEmpty() || !this.speculars.isEmpty()) &&
                ConfigGraphics.OptiFineEntityShader != OptiFine.Shaders.Entities) {
            state = state.shader(ConfigGraphics.OptiFineEntityShader);
        }

        // Refresh LOD every 0.5s
        if (this.lod_tick + 10 < stock.getTickCount() || this.lod_tick > stock.getTickCount()) {
            this.lod_tick = stock.getTickCount();

            double playerDistanceSq = stock.getWorld()
                    .getEntities(stock.getClass())
                    .stream()
                    .filter(x -> Objects.equals(x.getDefinitionID(), stock.getDefinitionID()) && Objects.equals(x.getTexture(), stock.getTexture()))
                    .mapToDouble(x -> x.getPosition().distanceToSquared(MinecraftClient.getPlayer().getPosition()))
                    .min()
                    .orElse(0);

            if (playerDistanceSq > ConfigGraphics.StockLODDistance * 2 * ConfigGraphics.StockLODDistance * 2) {
                this.lod_level = LOD_SMALL;
            } else if (playerDistanceSq > ConfigGraphics.StockLODDistance * ConfigGraphics.StockLODDistance) {
                this.lod_level = LOD_LARGE;
            } else {
                this.lod_level = cam72cam.mod.Config.MaxTextureSize;
            }
        }

        Binder binder = this.binder().texture(stock.getTexture()).lod(this.lod_level);
        try (
                OBJRender.Binding bound = binder.bind(state)
        ) {
            double backup = stock.distanceTraveled;

            if (!stock.isSliding()) {
                stock.distanceTraveled += stock.getCurrentSpeed()
                        .minecraft() * stock.getTickSkew() * partialTicks * 1.1;
            }
            stock.distanceTraveled /= stock.gauge.scale();


            this.base.render(bound, stock, available);

            stock.distanceTraveled = backup;
        }
    }

    public void postRender(EntityMoveableRollingStock stock, RenderState state, float partialTicks) {
        this.postRender((ENTITY) stock, state);
    }

    protected void postRender(ENTITY stock, RenderState state) {
        state.scale(stock.gauge.scale(), stock.gauge.scale(), stock.gauge.scale());
        state.rotate(this.sway.getRollDegrees(stock), 1, 0, 0);
        this.controls.forEach(c -> c.postRender(stock, state));
        this.doors.forEach(c -> c.postRender(stock, state));
        this.gauges.forEach(c -> c.postRender(stock, state));
        this.headlights.forEach(x -> x.postRender(stock, state));
    }

    public float getFrontYaw(EntityMoveableRollingStock stock) {
        return this.frontTrackers.get(stock).getYawReadout();
    }

    public float getRearYaw(EntityMoveableRollingStock stock) {
        return this.rearTrackers.get(stock).getYawReadout();
    }

    public List<Control<ENTITY>> getControls() {
        return this.controls;
    }

    public List<Door<ENTITY>> getDoors() {
        return this.doors;
    }

    public List<Interactable<ENTITY>> getInteractable() {
        List<Interactable<ENTITY>> interactable = new ArrayList<>(this.getDraggable());
        interactable.addAll(this.seats);
        return interactable;
    }

    public List<Control<ENTITY>> getDraggable() {
        List<Control<ENTITY>> draggable = new ArrayList<>();
        draggable.addAll(this.controls);
        draggable.addAll(this.doors);
        return draggable;
    }

    public List<Seat<ENTITY>> getSeats() {
        return this.seats;
    }
}

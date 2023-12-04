package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityBuildableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.gui.overlay.GuiBuilder;
import cam72cam.immersiverailroading.gui.overlay.Readouts;
import cam72cam.immersiverailroading.library.*;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.util.*;
import cam72cam.mod.entity.EntityRegistry;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.model.obj.OBJGroup;
import cam72cam.mod.model.obj.VertexBuffer;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.serialization.ResourceCache;
import cam72cam.mod.serialization.ResourceCache.GenericByteBuffer;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapped;
import cam72cam.mod.sound.ISound;
import cam72cam.mod.text.TextUtil;
import cam72cam.mod.world.World;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@TagMapped(EntityRollingStockDefinition.TagMapper.class)
public abstract class EntityRollingStockDefinition {
    private static final Identifier DEFAULT_PARTICLE_TEXTURE = new Identifier(ImmersiveRailroading.MODID, "textures/light.png");

    public final String defID;
    public final List<String> itemGroups;
    protected final Map<String, ControlSoundsDefinition> controlSounds = new HashMap<>();
    private final Class<? extends EntityRollingStock> type;
    private final Map<ModelComponentType, List<ModelComponent>> renderComponents;
    private final List<ItemComponentType> itemComponents;
    private final Function<EntityBuildableRollingStock, float[][]> heightmap;
    private final Map<String, LightDefinition> lights = new HashMap<>();
    public Map<String, String> textureNames;
    public float dampeningAmount;
    public Gauge recommended_gauge;
    public Boolean shouldSit;
    public Identifier wheel_sound;
    public Identifier clackFront;
    public Identifier clackRear;
    public double internal_model_scale;
    public Identifier couple_sound;
    public Identifier sliding_sound;
    public Identifier flange_sound;
    public Identifier collision_sound;
    public double flange_min_yaw;
    public float darken;
    public Identifier modelLoc;
    public Identifier smokeParticleTexture;
    public Identifier steamParticleTexture;
    public double rollingResistanceCoefficient;
    public double directFrictionCoefficient;
    public List<AnimationDefinition> animations;
    public Map<String, Float> cgDefaults;
    protected StockModel<?, ?> model;
    double internal_inv_scale;
    private String name;
    private String modelerName;
    private String packName;
    private ValveGearConfig valveGear;
    private Vec3d passengerCenter;
    private float bogeyFront;
    private float bogeyRear;
    private float couplerOffsetFront;
    private float couplerOffsetRear;
    private float couplerSlackFront;
    private float couplerSlackRear;
    private boolean scalePitch;
    private final double frontBounds;
    private final double rearBounds;
    private final double heightBounds;
    private final double widthBounds;
    private double passengerCompartmentLength;
    private double passengerCompartmentWidth;
    private double weight;
    private int maxPassengers;
    private float interiorLightLevel;
    private boolean hasIndependentBrake;
    private boolean hasPressureBrake;
    private boolean isLinearBrakeControl;
    private GuiBuilder overlay;
    private List<String> extraTooltipInfo;
    private boolean hasInternalLighting;
    private double swayMultiplier;
    private double tiltMultiplier;
    private float brakeCoefficient;

    public EntityRollingStockDefinition(Class<? extends EntityRollingStock> type, String defID, DataBlock data) throws Exception {
        this.type = type;
        this.defID = defID;


        this.loadData(this.transformData(data));

        this.model = this.createModel();
        this.itemGroups = this.model.groups.keySet()
                .stream()
                .filter(x -> !ModelComponentType.shouldRender(x))
                .collect(Collectors.toList());

        this.renderComponents = new HashMap<>();
        for (ModelComponent component : this.model.allComponents) {
            this.renderComponents.computeIfAbsent(component.type, v -> new ArrayList<>())
                    .add(0, component);
        }

        this.itemComponents = this.model.allComponents.stream()
                .map(component -> component.type)
                .map(ItemComponentType::from)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        this.frontBounds = -this.model.minOfGroup(this.model.groups()).x;
        this.rearBounds = this.model.maxOfGroup(this.model.groups()).x;
        this.widthBounds = this.model.widthOfGroups(this.model.groups());

        // Bad hack for height bounds
        ArrayList<String> heightGroups = new ArrayList<>();
        for (String group : this.model.groups()) {
            boolean ignore = false;
            for (ModelComponentType rct : ModelComponentType.values()) {
                if (rct.collisionsEnabled) {
                    continue;
                }
                for (int i = 0; i < 10; i++) {
                    if (Pattern.matches(rct.regex.replace("#ID#", "" + i), group)) {
                        ignore = true;
                        break;
                    }
                }
                if (ignore) {
                    break;
                }
            }
            if (!ignore) {
                heightGroups.add(group);
            }
        }
        this.heightBounds = this.model.heightOfGroups(heightGroups);

        this.heightmap = this.initHeightmap();
    }

    public final EntityRollingStock spawn(World world, Vec3d pos, float yaw, Gauge gauge, String texture) {
        EntityRollingStock stock = (EntityRollingStock) EntityRegistry.create(world, this.type);
        stock.setPosition(pos);
        stock.setRotationYaw(yaw);
        // Override prev
        stock.setRotationYaw(yaw);
        stock.setup(this, gauge, texture);

        return stock;
    }

    public boolean shouldScalePitch() {
        return ConfigSound.scaleSoundToGauge && this.scalePitch;
    }

    protected Identifier defaultDataLocation() {
        return new Identifier(ImmersiveRailroading.MODID, "rolling_stock/default/base.caml");
    }

    private DataBlock withImports(DataBlock data) throws IOException {
        List<DataBlock.Value> imports = data.getValues("import");
        if (imports != null) {
            for (DataBlock.Value toImport : imports) {
                DataBlock loaded = DataBlock.load(toImport.asIdentifier());
                loaded = this.withImports(loaded);
                // Graft data on TOP of loaded
                data = new MergedBlocks(loaded, data);
            }
        }
        return data;
    }

    private DataBlock transformData(DataBlock data) throws IOException {
        DataBlock base = DataBlock.load(this.defaultDataLocation());
        return new MergedBlocks(this.withImports(base), this.withImports(data));
    }

    public void loadData(DataBlock data) throws Exception {
        this.name = data.getValue("name").asString();
        this.modelerName = data.getValue("modeler").asString();
        this.packName = data.getValue("pack").asString();
        this.darken = data.getValue("darken_model").asFloat();
        this.internal_model_scale = 1;
        this.internal_inv_scale = 1;
        // TODO Gauge.from(Gauge.STANDARD).value() what happens when != Gauge.STANDARD
        this.recommended_gauge = Gauge.from(Gauge.STANDARD);
        Double model_gauge_m = data.getValue("model_gauge_m").asDouble();
        if (model_gauge_m != null) {
            this.recommended_gauge = Gauge.from(model_gauge_m);
            this.internal_model_scale = Gauge.STANDARD / model_gauge_m;
        }
        Double recommended_gauge_m = data.getValue("recommended_gauge_m").asDouble();
        if (recommended_gauge_m != null) {
            this.recommended_gauge = Gauge.from(recommended_gauge_m);
        }
        if (this.recommended_gauge != Gauge.from(Gauge.STANDARD)) {
            this.internal_inv_scale = Gauge.STANDARD / this.recommended_gauge.value();
        }

        this.textureNames = new LinkedHashMap<>();
        //textureNames.put("", "Default");
        DataBlock tex_variants = data.getBlock("tex_variants");
        if (tex_variants != null) {
            tex_variants.getValueMap().forEach((key, value) -> this.textureNames.put(value.asString(), key));
        }

        try {
            List<DataBlock> alternates = new ArrayList<>();

            Identifier alt_textures = new Identifier(ImmersiveRailroading.MODID, this.defID.replace(".caml", ".json")
                    .replace(".json", "_variants.json"));
            List<InputStream> alts = alt_textures.getResourceStreamAll();
            for (InputStream input : alts) {
                alternates.add(JSON.parse(input));
            }

            alt_textures = new Identifier(alt_textures.getDomain(), alt_textures.getPath().replace(".json", ".caml"));
            alts = alt_textures.getResourceStreamAll();
            for (InputStream input : alts) {
                alternates.add(CAML.parse(input));
            }
            for (DataBlock alternate : alternates) {
                alternate.getValueMap().forEach((key, value) -> this.textureNames.put(value.asString(), key));
            }
        } catch (java.io.FileNotFoundException ex) {
            ImmersiveRailroading.catching(ex);
        }

        this.modelLoc = data.getValue("model").asIdentifier();

        DataBlock passenger = data.getBlock("passenger");
        this.passengerCenter = new Vec3d(0, passenger.getValue("center_y").asDouble() - 0.35, passenger.getValue("center_x")
                .asDouble()).scale(this.internal_model_scale);
        this.passengerCompartmentLength = passenger.getValue("length").asDouble() * this.internal_model_scale;
        this.passengerCompartmentWidth = passenger.getValue("width").asDouble() * this.internal_model_scale;
        this.maxPassengers = passenger.getValue("slots").asInteger();
        this.shouldSit = passenger.getValue("should_sit").asBoolean();

        DataBlock pivot = data.getBlock("trucks"); // Legacy
        if (pivot == null) {
            pivot = data.getBlock("pivot");
        }
        this.bogeyFront = pivot.getValue("front").asFloat() * (float) this.internal_model_scale;
        this.bogeyRear = pivot.getValue("rear").asFloat() * (float) this.internal_model_scale;

        this.dampeningAmount = data.getValue("sound_dampening_percentage").asFloat();
        if (this.dampeningAmount < 0 || this.dampeningAmount > 1) {
            this.dampeningAmount = 0.75f;
        }
        this.scalePitch = data.getValue("scale_pitch").asBoolean();

        DataBlock couplers = data.getBlock("couplers");
        this.couplerOffsetFront = couplers.getValue("front_offset").asFloat() * (float) this.internal_model_scale;
        this.couplerOffsetRear = couplers.getValue("rear_offset").asFloat() * (float) this.internal_model_scale;
        this.couplerSlackFront = couplers.getValue("front_slack").asFloat() * (float) this.internal_model_scale;
        this.couplerSlackRear = couplers.getValue("rear_slack").asFloat() * (float) this.internal_model_scale;

        DataBlock properties = data.getBlock("properties");
        this.weight = properties.getValue("weight_kg").asInteger() * this.internal_inv_scale;
        this.valveGear = ValveGearConfig.get(properties, "valve_gear");
        this.hasIndependentBrake = properties.getValue("independent_brake").asBoolean();
        this.hasPressureBrake = properties.getValue("pressure_brake").asBoolean();
        // Locomotives default to linear brake control
        this.isLinearBrakeControl = properties.getValue("linear_brake_control").asBoolean();

        this.brakeCoefficient = PhysicalMaterials.STEEL.kineticFriction(PhysicalMaterials.CAST_IRON);
        try {
            this.brakeCoefficient = PhysicalMaterials.STEEL.kineticFriction(PhysicalMaterials.valueOf(properties.getValue("brake_shoe_material")
                    .asString()));
        } catch (Exception ex) {
            ImmersiveRailroading.warn("Invalid brake_shoe_material, possible values are: %s", Arrays.toString(PhysicalMaterials.values()));
        }
        this.brakeCoefficient = properties.getValue("brake_friction_coefficient").asFloat(this.brakeCoefficient);
        // https://en.wikipedia.org/wiki/Rolling_resistance#Rolling_resistance_coefficient_examples
        this.rollingResistanceCoefficient = properties.getValue("rolling_resistance_coefficient").asDouble();
        this.directFrictionCoefficient = properties.getValue("direct_friction_coefficient").asDouble();

        this.swayMultiplier = properties.getValue("swayMultiplier").asDouble();
        this.tiltMultiplier = properties.getValue("tiltMultiplier").asDouble();

        this.interiorLightLevel = properties.getValue("interior_light_level").asFloat();
        this.hasInternalLighting = properties.getValue("internalLighting").asBoolean();

        DataBlock lights = data.getBlock("lights");
        if (lights != null) {
            lights.getBlockMap().forEach((key, block) -> this.lights.put(key, new LightDefinition(block)));
        }

        DataBlock sounds = data.getBlock("sounds");
        this.wheel_sound = sounds.getValue("wheels").asIdentifier();
        this.clackFront = this.clackRear = sounds.getValue("clack").asIdentifier();
        this.clackFront = sounds.getValue("clack_front").asIdentifier(this.clackFront);
        this.clackRear = sounds.getValue("clack_rear").asIdentifier(this.clackRear);
        this.couple_sound = sounds.getValue("couple").asIdentifier();
        this.sliding_sound = sounds.getValue("sliding").asIdentifier();
        this.flange_sound = sounds.getValue("flange").asIdentifier();
        this.flange_min_yaw = sounds.getValue("flange_min_yaw").asDouble();
        this.collision_sound = sounds.getValue("collision").asIdentifier();
        DataBlock soundControls = sounds.getBlock("controls");
        if (soundControls != null) {
            soundControls.getBlockMap()
                    .forEach((key, block) -> this.controlSounds.put(key, new ControlSoundsDefinition(block)));
        }

        Identifier overlay = data.getValue("overlay").asIdentifier();
        this.overlay = overlay != null ? GuiBuilder.parse(overlay) : this.getDefaultOverlay(data);

        this.extraTooltipInfo = new ArrayList<>();
        List<DataBlock.Value> extra_tooltip_info = data.getValues("extra_tooltip_info");
        if (extra_tooltip_info != null) {
            extra_tooltip_info.forEach(value -> this.extraTooltipInfo.add(value.asString()));
        }

        this.smokeParticleTexture = this.steamParticleTexture = DEFAULT_PARTICLE_TEXTURE;
        DataBlock particles = data.getBlock("particles");
        if (particles != null) {
            DataBlock smoke = particles.getBlock("smoke");
            if (smoke != null) {
                this.smokeParticleTexture = new Identifier(smoke.getValue("texture").asString());
            }
            DataBlock steam = particles.getBlock("steam");
            if (steam != null) {
                this.steamParticleTexture = new Identifier(steam.getValue("texture").asString());
            }
        }

        this.animations = new ArrayList<>();
        List<DataBlock> aobjs = data.getBlocks("animations");
        if (aobjs == null) {
            aobjs = data.getBlocks("animation");
        }
        if (aobjs != null) {
            for (DataBlock entry : aobjs) {
                this.animations.add(new AnimationDefinition(entry));
            }
        }

        this.cgDefaults = new HashMap<>();
        DataBlock controls = data.getBlock("controls");
        if (controls != null) {
            controls.getBlockMap().forEach((key, block) ->
                    this.cgDefaults.put(key, block.getValue("default").asFloat(0))
            );
        }
    }

    public List<ModelComponent> getComponents(ModelComponentType name) {
        if (!this.renderComponents.containsKey(name)) {
            return null;
        }
        return this.renderComponents.get(name);
    }

    public Vec3d correctPassengerBounds(Gauge gauge, Vec3d pos, boolean shouldSit) {
        double gs = gauge.scale();
        Vec3d passengerCenter = this.passengerCenter.scale(gs);
        pos = pos.subtract(passengerCenter);
        if (pos.z > this.passengerCompartmentLength * gs) {
            pos = new Vec3d(pos.x, pos.y, this.passengerCompartmentLength * gs);
        }

        if (pos.z < -this.passengerCompartmentLength * gs) {
            pos = new Vec3d(pos.x, pos.y, -this.passengerCompartmentLength * gs);
        }

        if (Math.abs(pos.x) > this.passengerCompartmentWidth / 2 * gs) {
            pos = new Vec3d(Math.copySign(this.passengerCompartmentWidth / 2 * gs, pos.x), pos.y, pos.z);
        }

        pos = new Vec3d(pos.x, passengerCenter.y - (shouldSit ? 0.75 : 0), pos.z + passengerCenter.z);

        return pos;
    }

    public boolean isAtFront(Gauge gauge, Vec3d pos) {
        pos = pos.subtract(this.passengerCenter.scale(gauge.scale()));
        return pos.z >= this.passengerCompartmentLength * gauge.scale();
    }

    public boolean isAtRear(Gauge gauge, Vec3d pos) {
        pos = pos.subtract(this.passengerCenter.scale(gauge.scale()));
        return pos.z <= -this.passengerCompartmentLength * gauge.scale();
    }

    public List<ItemComponentType> getItemComponents() {
        return this.itemComponents;
    }

    public float getBogeyFront(Gauge gauge) {
        return (float) gauge.scale() * this.bogeyFront;
    }

    public float getBogeyRear(Gauge gauge) {
        return (float) gauge.scale() * this.bogeyRear;
    }

    public double getCouplerPosition(CouplerType coupler, Gauge gauge) {
        switch (coupler) {
            default:
            case FRONT:
                return gauge.scale() * (this.frontBounds + this.couplerOffsetFront);
            case BACK:
                return gauge.scale() * (this.rearBounds + this.couplerOffsetRear);
        }
    }

    public double getCouplerSlack(CouplerType coupler, Gauge gauge) {
        if (!Config.ImmersionConfig.slackEnabled) {
            return 0;
        }
        switch (coupler) {
            default:
            case FRONT:
                return gauge.scale() * (this.couplerSlackFront);
            case BACK:
                return gauge.scale() * (this.couplerSlackRear);
        }
    }

    public boolean hasIndependentBrake() {
        return this.hasIndependentBrake;
    }

    public boolean hasPressureBrake() {
        return this.hasPressureBrake;
    }

    private Function<EntityBuildableRollingStock, float[][]> initHeightmap() {
        String key = String.format(
                "%s-%s-%s-%s-%s-%s",
                this.model.hash, this.frontBounds, this.rearBounds, this.widthBounds, this.heightBounds, this.renderComponents.size());
        try {
            ResourceCache<HeightMapData> cache = new ResourceCache<>(
                    new Identifier(this.modelLoc.getDomain(), this.modelLoc.getPath() + "_heightmap_" + key.hashCode()),
                    provider -> new HeightMapData(this)
            );
            Supplier<GenericByteBuffer> data = cache.getResource("data.bin", builder -> new GenericByteBuffer(builder.data));
            Supplier<GenericByteBuffer> meta = cache.getResource("meta.nbt", builder -> {
                try {
                    return new GenericByteBuffer(new TagCompound()
                            .setInteger("xRes", builder.xRes)
                            .setInteger("zRes", builder.zRes)
                            .setList("components", builder.components, v -> new TagCompound().setString("key", v.key))
                            .toBytes()
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            cache.close();

            return (stock) -> {
                try {
                    float[] raw = data.get().floats();
                    TagCompound tc = new TagCompound(meta.get().bytes());

                    int xRes = tc.getInteger("xRes");
                    int zRes = tc.getInteger("zRes");
                    List<String> componentKeys = tc.getList("components", v -> v.getString("key"));

                    float[][] heightMap = new float[xRes][zRes];

                    List<ModelComponentType> availComponents = new ArrayList<>();
                    for (ItemComponentType item : stock.getItemComponents()) {
                        availComponents.addAll(item.render);
                    }

                    for (List<ModelComponent> rcl : this.renderComponents.values()) {
                        for (ModelComponent rc : rcl) {
                            if (!rc.type.collisionsEnabled) {
                                continue;
                            }

                            if (availComponents.contains(rc.type)) {
                                availComponents.remove(rc.type);
                            } else if (rc.type == ModelComponentType.REMAINING && stock.isBuilt()) {
                                //pass
                            } else {
                                continue;
                            }

                            int idx = componentKeys.indexOf(rc.key) * xRes * zRes;
                            if (idx < 0) {
                                // Code changed, we should probably invalidate this cache key...
                                continue;
                            }
                            for (int x = 0; x < xRes; x++) {
                                for (int z = 0; z < zRes; z++) {
                                    heightMap[x][z] = Math.max(heightMap[x][z], raw[idx + x * zRes + z]);
                                }
                            }
                        }
                    }

                    return heightMap;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public float[][] createHeightMap(EntityBuildableRollingStock stock) {
        return this.heightmap.apply(stock);
    }

    public RealBB getBounds(float yaw, Gauge gauge) {
        return new RealBB(gauge.scale() * this.frontBounds, gauge.scale() * -this.rearBounds, gauge.scale() * this.widthBounds,
                gauge.scale() * this.heightBounds, yaw);
    }

    public String name() {
        String[] sp = this.defID.replaceAll(".json", "").split("/");
        String localStr = String.format("%s:entity.%s.%s", ImmersiveRailroading.MODID, sp[sp.length - 2], sp[sp.length - 1]);
        String transStr = TextUtil.translate(localStr);
        return !localStr.equals(transStr) ? transStr : this.name;
    }

    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = new ArrayList<>();
        tips.add(GuiText.WEIGHT_TOOLTIP.toString(this.getWeight(gauge)));
        tips.add(GuiText.MODELER_TOOLTIP.toString(this.modelerName));
        tips.add(GuiText.PACK_TOOLTIP.toString(this.packName));
        return tips;
    }

    /**
     * @return Stock Weight in Kg
     */
    public int getWeight(Gauge gauge) {
        return (int) Math.ceil(gauge.scale() * this.weight);
    }

    protected StockModel<?, ?> createModel() throws Exception {
        return new StockModel<>(this);
    }

    public StockModel<?, ?> getModel() {
        return this.model;
    }

    public double getHeight(Gauge gauge) {
        return gauge.scale() * this.heightBounds;
    }

    public double getWidth(Gauge gauge) {
        return gauge.scale() * this.widthBounds;
    }

    public double getLength(Gauge gauge) {
        return gauge.scale() * this.frontBounds + this.rearBounds;
    }

    public int getMaxPassengers() {
        return this.maxPassengers;
    }

    public boolean acceptsPassengers() {
        return false;
    }

    public boolean acceptsLivestock() {
        return false;
    }

    public ValveGearConfig getValveGear() {
        return this.valveGear;
    }

    public LightDefinition getLight(String name) {
        return this.lights.get(name);
    }

    public ControlSoundsDefinition getControlSound(String name) {
        return this.controlSounds.get(name);
    }

    public float interiorLightLevel() {
        return this.interiorLightLevel;
    }

    public boolean isLinearBrakeControl() {
        return this.isLinearBrakeControl;
    }

    protected GuiBuilder getDefaultOverlay(DataBlock data) throws IOException {
        return this.hasIndependentBrake() ? GuiBuilder.parse(new Identifier(ImmersiveRailroading.MODID, "gui/default/independent.caml")) : null;
    }

    public GuiBuilder getOverlay() {
        return this.overlay;
    }

    public List<String> getExtraTooltipInfo() {
        return this.extraTooltipInfo;
    }

    public boolean hasInternalLighting() {
        return this.hasInternalLighting;
    }

    public double getSwayMultiplier() {
        return this.swayMultiplier;
    }

    public double getTiltMultiplier() {
        return this.tiltMultiplier;
    }

    public double getBrakeShoeFriction() {
        return this.brakeCoefficient;
    }

    public static class SoundDefinition {
        public final Identifier start;
        public final Identifier main;
        public final boolean looping;
        public final Identifier stop;
        public final Float distance;
        public final float volume;

        public SoundDefinition(Identifier fallback) {
            // Simple
            this.start = null;
            this.main = fallback;
            this.looping = true;
            this.stop = null;
            this.distance = null;
            this.volume = 1;
        }

        public SoundDefinition(DataBlock obj) {
            this.start = obj.getValue("start").asIdentifier();
            this.main = obj.getValue("main").asIdentifier();
            this.looping = obj.getValue("looping").asBoolean(true);
            this.stop = obj.getValue("stop").asIdentifier();
            this.distance = obj.getValue("distance").asFloat();
            this.volume = obj.getValue("volume").asFloat(1.0f);
        }

        public static SoundDefinition getOrDefault(DataBlock block, String key) {
            DataBlock found = block.getBlock(key);
            if (found != null) {
                return new SoundDefinition(found);
            }
            Identifier ident = block.getValue(key).asIdentifier();
            if (ident != null && ident.canLoad()) {
                return new SoundDefinition(ident);
            }
            return null;
        }
    }

    public static class AnimationDefinition {
        public final String control_group;
        public final AnimationMode mode;
        public final Readouts readout;
        public final Identifier animatrix;
        public final float offset;
        public final boolean invert;
        public final float frames_per_tick;
        public final SoundDefinition sound;
        public AnimationDefinition(DataBlock obj) {
            this.control_group = obj.getValue("control_group").asString();
            String readout = obj.getValue("readout").asString();
            this.readout = readout != null ? Readouts.valueOf(readout.toUpperCase(Locale.ROOT)) : null;
            if (this.control_group == null && readout == null) {
                throw new IllegalArgumentException("Must specify either a control group or a readout for an animation");
            }
            this.animatrix = obj.getValue("animatrix").asIdentifier();
            this.mode = AnimationMode.valueOf(obj.getValue("mode").asString().toUpperCase(Locale.ROOT));
            this.offset = obj.getValue("offset").asFloat(0f);
            this.invert = obj.getValue("invert").asBoolean(false);
            this.frames_per_tick = obj.getValue("frames_per_tick").asFloat(1f);
            this.sound = SoundDefinition.getOrDefault(obj, "sound");
        }

        public boolean valid() {
            return this.animatrix != null && (this.control_group != null || this.readout != null);
        }

        public enum AnimationMode {
            VALUE,
            PLAY_FORWARD,
            PLAY_REVERSE,
            PLAY_BOTH,
            LOOP,
            LOOP_SPEED
        }
    }

    public static class LightDefinition {
        public static final Identifier default_light_tex = new Identifier(ImmersiveRailroading.MODID, "textures/light.png");

        public final float blinkIntervalSeconds;
        public final float blinkOffsetSeconds;
        public final boolean blinkFullBright;
        public final String reverseColor;
        public final Identifier lightTex;
        public final boolean castsLight;

        private LightDefinition(DataBlock data) {
            this.blinkIntervalSeconds = data.getValue("blinkIntervalSeconds").asFloat(0f);
            this.blinkOffsetSeconds = data.getValue("blinkOffsetSeconds").asFloat(0f);
            this.blinkFullBright = data.getValue("blinkFullBright").asBoolean(true);
            this.reverseColor = data.getValue("reverseColor").asString();
            this.lightTex = data.getValue("texture").asIdentifier(default_light_tex);
            this.castsLight = data.getValue("castsLight").asBoolean(true);
        }
    }

    public static class ControlSoundsDefinition {
        private static final List<ISound> toStop = new ArrayList<>();
        public final Identifier engage;
        public final Identifier move;
        public final Float movePercent;
        public final Identifier disengage;
        private final Map<UUID, List<ISound>> sounds = new HashMap<>();
        private final Map<UUID, Float> lastMoveSoundValue = new HashMap<>();
        private final Map<UUID, Boolean> wasSoundPressed = new HashMap<>();

        public ControlSoundsDefinition(Identifier engage, Identifier move, Float movePercent, Identifier disengage) {
            this.engage = engage;
            this.move = move;
            this.movePercent = movePercent;
            this.disengage = disengage;
        }

        public ControlSoundsDefinition(DataBlock data) {
            this.engage = data.getValue("engage").asIdentifier();
            this.move = data.getValue("move").asIdentifier();
            this.movePercent = data.getValue("movePercent").asFloat();
            this.disengage = data.getValue("disengage").asIdentifier();
        }

        public static void cleanupStoppedSounds() {
            if (toStop.isEmpty()) {
                return;
            }
            for (ISound sound : toStop) {
                sound.stop();
            }
            toStop.clear();
        }

        public void effects(EntityRollingStock stock, boolean isPressed, float value, Vec3d pos) {
            if (this.sounds.containsKey(stock.getUUID())) {
                for (ISound snd : new ArrayList<>(this.sounds.get(stock.getUUID()))) {
                    if (snd.isPlaying()) {
                        snd.setVelocity(stock.getVelocity());
                        snd.setPosition(pos);
                    }
                }
            }

            Boolean wasPressed = this.wasSoundPressed.getOrDefault(stock.getUUID(), false);
            this.wasSoundPressed.put(stock.getUUID(), isPressed);

            float lastValue = this.lastMoveSoundValue.computeIfAbsent(stock.getUUID(), k -> value);

            if (!wasPressed && isPressed) {
                // Start
                this.createSound(stock, this.engage, pos, false);
                if (this.move != null && this.movePercent == null) {
                    // Start move repeat
                    this.createSound(stock, this.move, pos, true);
                }
            } else if (wasPressed && !isPressed) {
                // Release
                if (this.sounds.containsKey(stock.getUUID())) {
                    // Start and Stop may have happend between ticks, we want to wait till the next tick to stop the sound
                    toStop.addAll(this.sounds.remove(stock.getUUID()));
                }
                this.createSound(stock, this.disengage, pos, false);
            } else if (this.move != null && this.movePercent != null) {
                // Move
                if (Math.abs(lastValue - value) > this.movePercent) {
                    this.createSound(stock, this.move, pos, false);
                    this.lastMoveSoundValue.put(stock.getUUID(), value);
                }
            }
        }

        private void createSound(EntityRollingStock stock, Identifier sound, Vec3d pos, boolean repeats) {
            if (sound == null) {
                return;
            }
            ISound snd = stock.createSound(sound, repeats, 10, ConfigSound.SoundCategories::controls);
            snd.setVelocity(stock.getVelocity());
            snd.setVolume(1);
            snd.setPitch(1f);
            snd.play(pos);
            this.sounds.computeIfAbsent(stock.getUUID(), k -> new ArrayList<>()).add(snd);
        }

        public <T extends EntityMoveableRollingStock> void removed(T stock) {
            List<ISound> removed = this.sounds.remove(stock.getUUID());
            if (removed != null) {
                for (ISound sound : removed) {
                    sound.stop();
                }
            }
        }
    }

    private static class HeightMapData {
        final int xRes;
        final int zRes;
        final List<ModelComponent> components;
        final float[] data;

        HeightMapData(EntityRollingStockDefinition def) {
            ImmersiveRailroading.info("Generating heightmap %s", def.defID);

            double ratio = 8;
            int precision = (int) Math.ceil(def.heightBounds * 4);

            this.xRes = (int) Math.ceil((def.frontBounds + def.rearBounds) * ratio);
            this.zRes = (int) Math.ceil(def.widthBounds * ratio);
            this.components = def.renderComponents.values().stream()
                    .flatMap(Collection::stream)
                    .filter(rc -> rc.type.collisionsEnabled)
                    .collect(Collectors.toList());
            this.data = new float[this.components.size() * this.xRes * this.zRes];

            VertexBuffer vb = def.model.vbo.buffer.get();

            for (int i = 0; i < this.components.size(); i++) {
                ModelComponent rc = this.components.get(i);
                int idx = i * this.xRes * this.zRes;
                for (String group : rc.modelIDs) {
                    OBJGroup faces = def.model.groups.get(group);

                    for (int face = faces.faceStart; face <= faces.faceStop; face++) {
                        Path2D path = new Path2D.Float();
                        float fheight = 0;
                        boolean first = true;
                        for (int point = 0; point < vb.vertsPerFace; point++) {
                            int vertex = face * vb.vertsPerFace * vb.stride + point * vb.stride;
                            float vertX = vb.data[vertex];
                            float vertY = vb.data[vertex + 1];
                            float vertZ = vb.data[vertex + 2];
                            vertX += def.frontBounds;
                            vertZ += def.widthBounds / 2;
                            if (first) {
                                path.moveTo(vertX * ratio, vertZ * ratio);
                                first = false;
                            } else {
                                path.lineTo(vertX * ratio, vertZ * ratio);
                            }
                            fheight += vertY / vb.vertsPerFace;
                        }
                        Rectangle2D bounds = path.getBounds2D();
                        if (bounds.getWidth() * bounds.getHeight() < 1) {
                            continue;
                        }
                        for (int x = 0; x < this.xRes; x++) {
                            for (int z = 0; z < this.zRes; z++) {
                                float relX = ((this.xRes - 1) - x);
                                float relZ = z;
                                if (bounds.contains(relX, relZ) && path.contains(relX, relZ)) {
                                    float relHeight = fheight / (float) def.heightBounds;
                                    relHeight = ((int) Math.ceil(relHeight * precision)) / (float) precision;
                                    this.data[idx + x * this.zRes + z] = Math.max(this.data[idx + x * this.zRes + z], relHeight);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static class TagMapper implements cam72cam.mod.serialization.TagMapper<EntityRollingStockDefinition> {
        @Override
        public TagAccessor<EntityRollingStockDefinition> apply(Class<EntityRollingStockDefinition> type, String fieldName, TagField tag) {
            return new TagAccessor<>(
                    (d, o) -> d.setString(fieldName, o == null ? null : o.defID),
                    d -> DefinitionManager.getDefinition(d.getString(fieldName))
            );
        }
    }

}

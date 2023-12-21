package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config.ConfigDamage;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.items.ItemPaintBrush;
import cam72cam.immersiverailroading.library.*;
import cam72cam.immersiverailroading.model.part.Control;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.mod.entity.CustomEntity;
import cam72cam.mod.entity.DamageType;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.custom.IClickable;
import cam72cam.mod.entity.custom.IKillable;
import cam72cam.mod.entity.custom.ITickable;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.serialization.*;
import cam72cam.mod.sound.Audio;
import cam72cam.mod.sound.ISound;
import cam72cam.mod.sound.SoundCategory;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.util.SingleCache;
import org.apache.commons.lang3.tuple.Pair;
import util.Matrix4;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class EntityRollingStock extends CustomEntity implements ITickable, IClickable, IKillable {

    @TagField("gauge")
    private Gauge gauge;

    @TagField("tag")
    @TagSync
    public String tag = "";

    @TagSync
    @TagField(value = "controlPositions", mapper = ControlPositionMapper.class)
    protected Map<String, Pair<Boolean, Float>> controlPositions = new HashMap<>();

    @TagField("defID")
    private String definitionId;

    @TagSync
    @TagField(value = "texture", mapper = StrictTagMapper.class)
    private String texture = null;

    private final SingleCache<Vec3d, Matrix4> modelMatrix = new SingleCache<>(v -> new Matrix4().translate(this.getPosition().x, this.getPosition().y, this.getPosition().z)
            .rotate(Math.toRadians(180 - this.getRotationYaw()), 0, 1, 0)
            .rotate(Math.toRadians(this.getRotationPitch()), 1, 0, 0)
            .rotate(Math.toRadians(-90), 0, 1, 0)
            .scale(this.getGauge().scale(), this.getGauge().scale(), this.getGauge().scale()));


    public void setup(EntityRollingStockDefinition rollingStockDefinition, Gauge gauge, String texture) {
        this.definitionId = rollingStockDefinition.defID;
        this.setGauge(gauge);
        this.texture = texture;
        rollingStockDefinition.cgDefaults.forEach(this::setControlPosition);
    }

    public void setControlPosition(String control, float val) {
        this.controlPositions.put(control, Pair.of(false, Math.min(1, Math.max(0, val))));
    }

    public boolean isImmuneToFire() {
        return true;
    }

    public float getCollisionReduction() {
        return 1;
    }

    public boolean canBePushed() {
        return false;
    }


	/* TODO?
	@Override
	public String getName() {
		return this.getDefinition().name();
	}
	*/

    public boolean allowsDefaultMovement() {
        return false;
    }

    public String tryJoinWorld() {
        if (DefinitionManager.getDefinition(this.definitionId) == null) {
            String error = String.format("Missing definition %s, do you have all of the required resource packs?", this.definitionId);
            ImmersiveRailroading.error(error);
            return error;
        }
        return null;
    }

    public String getDefinitionId() {
        return this.definitionId;
    }

    @Override
    public void onTick() {
        if (this.getWorld().isClient) return;
        if (this.getTickCount() % 5 != 0) return;

        // todo: maybe check this on entity load instead of every 5 ticks?
        //       this just seems way too hacky
        EntityRollingStockDefinition definition = DefinitionManager.getDefinition(this.definitionId);
        if (definition == null) this.kill();
    }

    /*
     * Player Interactions
     */

    @Override
    public ClickResult onClick(Player player, Player.Hand hand) {
        if (player.getHeldItem(hand).is(IRItems.ITEM_PAINT_BRUSH) && player.hasPermission(Permissions.PAINT_BRUSH)) {
            openPaintbrushGui(this, player, hand);
            return ClickResult.ACCEPTED;
        }

        if (player.getHeldItem(hand).is(Fuzzy.NAME_TAG) && player.hasPermission(Permissions.STOCK_ASSEMBLY)) {
            if (this.getWorld().isClient) {
                return ClickResult.ACCEPTED;
            }
            this.tag = player.getHeldItem(hand).getDisplayName();
            player.sendMessage(PlayerMessage.direct(this.tag));
            return ClickResult.ACCEPTED;
        }
        return ClickResult.PASS;
    }

    private static void openPaintbrushGui(EntityRollingStock stock, Player player, Player.Hand hand) {
        if (!player.getWorld().isClient) return;

        ItemPaintBrush.Data data = new ItemPaintBrush.Data(player.getHeldItem(hand));
        switch (data.mode) {
            case GUI:
                GuiTypes.PAINT_BRUSH.open(player);
                break;
            case RANDOM_SINGLE:
            case RANDOM_COUPLED:
                new ItemPaintBrush.PaintBrushPacket(stock, data.mode, null, false).sendToServer();
                break;
        }
    }

    @Override
    public void onDamage(DamageType type, Entity source, float amount, boolean bypassesArmor) {
        if (this.getWorld().isClient) return;

        if (type == DamageType.EXPLOSION) {
            if ((source == null || !source.isMob()) && amount > 5 && ConfigDamage.trainMobExplosionDamage) {
                this.kill();
            }
        }

        if (type == DamageType.OTHER && source != null && source.isPlayer()) {
            Player player = source.asPlayer();
            if (player.isCrouching()) this.kill();
        }
    }

    @Override
    public void onRemoved() {

    }

    protected boolean shouldDropItems(DamageType type, float amount) {
        return type != DamageType.EXPLOSION || amount < 20;
    }

    public void handleKeyPress(Player source, KeyTypes key, boolean disableIndependentThrottle) {

    }

    /**
     * @return Stock Weight in Kg
     */
    public double getWeight() {
        return this.getDefinition().getWeight(this.getGauge());
    }

    public EntityRollingStockDefinition getDefinition() {
        return this.getDefinition(EntityRollingStockDefinition.class);
    }

    public <T extends EntityRollingStockDefinition> T getDefinition(Class<T> type) {
        EntityRollingStockDefinition def = DefinitionManager.getDefinition(this.definitionId);
        if (def == null) {
            // This should not be hit, entity should be removed handled by tryJoinWorld
            throw new RuntimeException(String.format("Definition %s has been removed!  This stock will not function!", this.definitionId));
        }
        return (T) def;
    }

    /*
     * Helpers
     */
	/* TODO RENDER

	@SideOnly(Side.CLIENT)
	@Override
	public boolean isInRangeToRenderDist(double distance)
    {
        return true;
    }

	@Override
	public boolean shouldRenderInPass(int pass) {
		return false;
	}
	*/

    public double getMaxWeight() {
        return this.getDefinition().getWeight(this.getGauge());
    }

    public ISound createSound(Identifier oggLocation, boolean repeats, double attenuationDistance, Supplier<Float> category) {
        ISound snd = Audio.newSound(oggLocation, SoundCategory.MASTER, repeats, (float) (attenuationDistance * ConfigSound.soundDistanceScale * this.getGauge()
                .scale()), this.soundScale());
        return new ISound() {
            @Override
            public void play(Vec3d pos) {
                snd.play(pos);
            }

            @Override
            public void stop() {
                snd.stop();
            }

            @Override
            public void setPosition(Vec3d pos) {
                snd.setPosition(pos);
            }

            @Override
            public void setPitch(float f) {
                snd.setPitch(f);
            }

            @Override
            public void setVelocity(Vec3d vel) {
                snd.setVelocity(vel);
            }

            @Override
            public void setVolume(float f) {
                snd.setVolume(f * category.get());
            }

            @Override
            public boolean isPlaying() {
                return snd.isPlaying();
            }
        };
    }

    public float soundScale() {
        if (this.getDefinition().shouldScalePitch()) {
            double scale = this.getGauge().scale() * this.getDefinition().internal_model_scale;
            return (float) Math.sqrt(Math.sqrt(scale));
        }
        return 1;
    }

    public String getTexture() {
        return this.texture;
    }

    public void setTexture(String variant) {
        if (this.getDefinition().textureNames.containsKey(variant)) {
            this.texture = variant;
        }
    }

    public boolean externalLightsEnabled() {
        return this.internalLightsEnabled();
    }

    public boolean internalLightsEnabled() {
        return false;
    }

    public Matrix4 getModelMatrix() {
        return this.modelMatrix.get(this.getPosition()).copy();
    }

    public void onDragStart(Control<?> control) {
        this.setControlPressed(control, true);
    }

    public void setControlPressed(Control<?> control, boolean pressed) {
        this.controlPositions.put(control.controlGroup, Pair.of(pressed, this.getControlPosition(control)));
    }

    public float getControlPosition(Control<?> control) {
        return this.getControlData(control).getRight();
    }

    public Pair<Boolean, Float> getControlData(Control<?> control) {
        return this.controlPositions.getOrDefault(control.controlGroup, Pair.of(false, this.defaultControlPosition(control)));
    }

    protected float defaultControlPosition(Control<?> control) {
        return 0;
    }

    public void onDrag(Control<?> control, double newValue) {
        this.setControlPressed(control, true);
        this.setControlPosition(control, (float) newValue);
    }

    public void onDragRelease(Control<?> control) {
        this.setControlPressed(control, false);

        if (control.toggle) {
            this.setControlPosition(control, Math.abs(this.getControlPosition(control) - 1));
        }
        if (control.press) {
            this.setControlPosition(control, 0);
        }
    }

    public float getControlPosition(String control) {
        return this.getControlData(control).getRight();
    }

    public Pair<Boolean, Float> getControlData(String control) {
        return this.controlPositions.getOrDefault(control, Pair.of(false, 0f));
    }

    public void setControlPositions(ModelComponentType type, float value) {
        this.getDefinition()
                .getModel()
                .getControls()
                .stream()
                .filter(control -> control.part.type == type)
                .forEach(control -> this.setControlPosition(control, value));
    }

    public void setControlPosition(Control<?> control, float val) {
        val = Math.min(1, Math.max(0, val));
        this.controlPositions.put(control.controlGroup, Pair.of(this.getControlPressed(control), val));
    }

    public boolean getControlPressed(Control<?> control) {
        return this.getControlData(control).getLeft();
    }

    public boolean playerCanDrag(Player player, Control<?> control) {
        return control.part.type != ModelComponentType.INDEPENDENT_BRAKE_X || player.hasPermission(Permissions.BRAKE_CONTROL);
    }

    public Gauge getGauge() {
        return gauge;
    }

    public void setGauge(Gauge gauge) {
        this.gauge = gauge;
    }

    private static class ControlPositionMapper implements TagMapper<Map<String, Pair<Boolean, Float>>> {
        @Override
        public TagAccessor<Map<String, Pair<Boolean, Float>>> apply(Class<Map<String, Pair<Boolean, Float>>> type, String fieldName, TagField tag) throws SerializationException {
            return new TagAccessor<>((d, o) -> d.setMap(fieldName, o, Function.identity(), x -> new TagCompound().setBoolean("pressed", x.getLeft())
                    .setFloat("pos", x.getRight())), d -> d.getMap(fieldName, Function.identity(), x -> Pair.of(x.hasKey("pressed") && x.getBoolean("pressed"), x.getFloat("pos"))));
        }
    }
}

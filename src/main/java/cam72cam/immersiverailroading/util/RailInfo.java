package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.Config.ConfigDamage;
import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.items.ItemRail;
import cam72cam.immersiverailroading.items.nbt.RailSettings;
import cam72cam.immersiverailroading.library.*;
import cam72cam.immersiverailroading.model.TrackModel;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.TrackDefinition;
import cam72cam.immersiverailroading.render.ExpireableMap;
import cam72cam.immersiverailroading.track.*;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.*;
import cam72cam.mod.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@TagMapped(RailInfo.TagMapper.class)
public class RailInfo {
    public final RailSettings settings;
    public final PlacementInfo placementInfo;
    public final PlacementInfo customInfo;

    // Used for tile rendering only
    public final SwitchState switchState;
    public final SwitchState switchForced;
    public final double tablePos;

    public final String uniqueID;
    public final boolean itemHeld;
    public ExpireableMap<Vec3i, BuilderBase> builders = new ExpireableMap<>();
    private double trackHeight = -1;

    public RailInfo(ItemStack settings, PlacementInfo placementInfo, PlacementInfo customInfo) {
        this(RailSettings.from(settings), placementInfo, customInfo, SwitchState.NONE, SwitchState.NONE, 0);
    }

    public RailInfo(RailSettings settings, PlacementInfo placementInfo, PlacementInfo customInfo, SwitchState switchState, SwitchState switchForced, double tablePos) {
        this(settings, placementInfo, customInfo, switchState, switchForced, tablePos, false);
    }

    public RailInfo(RailSettings settings, PlacementInfo placementInfo, PlacementInfo customInfo, SwitchState switchState, SwitchState switchForced, double tablePos, boolean itemHeld) {
        if (customInfo == null) {
            customInfo = placementInfo;
        }

        this.settings = settings;
        this.placementInfo = placementInfo;
        this.customInfo = customInfo;
        this.switchState = switchState;
        this.switchForced = switchForced;
        this.tablePos = tablePos;
        this.itemHeld = itemHeld;
        this.uniqueID = this.generateID();
    }

    private String generateID() {
        Object[] props = new Object[]{
                this.settings.type,
                this.settings.length,
                this.settings.degrees,
                this.settings.curvosity,
                this.settings.railBed,
                this.settings.gauge,
                this.settings.track,
                this.settings.smoothing,
                this.settings.isGradeCrossing,
                this.switchState,
                this.switchForced,
                this.tablePos,
                this.placementInfo.yaw,
                this.placementInfo.direction,
                this.customInfo.yaw,
                this.customInfo.direction,
        };
        String id = Arrays.toString(props);
        if (!this.placementInfo.placementPosition.equals(this.customInfo.placementPosition) || this.settings.posType != TrackPositionType.FIXED) {
            id += this.placementInfo.placementPosition.subtract(this.customInfo.placementPosition);
        }
        if (this.placementInfo.control != null) {
            id += this.placementInfo.control;
        }
        if (this.customInfo.control != null) {
            id += this.customInfo.control;
        }
        if (this.settings.type == TrackItems.TURNTABLE) {
            id += Config.ConfigBalance.AnglePlacementSegmentation;
            id += this.itemHeld;
        }
        return id;
    }

    public RailInfo withSettings(Consumer<RailSettings.Mutable> mod) {
        return this.with(b -> b.settings = b.settings.with(mod));
    }

    public RailInfo with(Consumer<Mutable> mod) {
        Mutable mut = new Mutable(this);
        mod.accept(mut);
        return mut.immutable();
    }

    public RailInfo offset(Vec3i offset) {
        return this.with(b -> {
            b.placementInfo = this.placementInfo.offset(offset);
            b.customInfo = b.customInfo != null ? b.customInfo.offset(offset) : null;
        });
    }

    public BuilderBase getBuilder(World world) {
        return this.getBuilder(world, Vec3i.ZERO);
    }

    public BuilderBase getBuilder(World world, Vec3i pos) {
        BuilderBase builder = this.builders.get(pos);
        if (builder == null) {
            builder = this.constructBuilder(world, pos);
            this.builders.put(pos, builder);
        }
        return builder;
    }

    private BuilderBase constructBuilder(World world, Vec3i pos) {
        switch (this.settings.type) {
            case STRAIGHT:
                return new BuilderStraight(this, world, pos);
            case CROSSING:
                return new BuilderCrossing(this, world, pos);
            case SLOPE:
                return new BuilderSlope(this, world, pos);
            case TURN:
                return new BuilderTurn(this, world, pos);
            case SWITCH:
                return new BuilderSwitch(this, world, pos);
            case TURNTABLE:
                return new BuilderTurnTable(this, world, pos);
            case CUSTOM:
                return new BuilderCubicCurve(this, world, pos);
        }
        return null;
    }

    public boolean build(Player player, Vec3i pos) {
        return this.build(player, pos, true) != null;
    }

    public List<ItemStack> build(Player player, Vec3i pos, boolean placeTrack) {
        BuilderBase builder = this.getBuilder(player.getWorld(), pos);

        if (!player.hasPermission(Permissions.BUILD_TRACK)) {
            return null;
        }

        if (player.isCreative() && ConfigDamage.creativePlacementClearsBlocks && placeTrack) {
            if (player.getWorld().isServer) {
                builder.clearArea();
            }
        }

        if (!placeTrack || (placeTrack && builder.canBuild())) {
            if (player.getWorld().isServer) {
                if (player.isCreative() && placeTrack) {
                    builder.build();
                    return Collections.emptyList();
                }

                // Survival check

                TrackDefinition def = this.getDefinition();

                List<MaterialManager> materials = new ArrayList<>();

                if (!this.settings.railBed.isEmpty()) {
                    materials.add(new MaterialManager(true, builder.costBed(), this.settings.railBed::is, this.settings.railBed));
                }
                if (!this.settings.railBedFill.isEmpty()) {
                    materials.add(new MaterialManager(false, builder.costFill(), this.settings.railBedFill::is, this.settings.railBedFill));
                }

                List<TrackDefinition.TrackMaterial> tieParts = def.materials.get(TrackComponent.TIE);
                List<TrackDefinition.TrackMaterial> railParts = def.materials.get(TrackComponent.RAIL);
                List<TrackDefinition.TrackMaterial> bedParts = def.materials.get(TrackComponent.BED);

                if (tieParts != null) {
                    for (TrackDefinition.TrackMaterial tiePart : tieParts) {
                        materials.add(new MaterialManager((int) Math.ceil(builder.costTies() * tiePart.cost), tiePart::matches, tiePart.examples(this.settings.gauge)));
                    }
                }
                if (railParts != null) {
                    for (TrackDefinition.TrackMaterial railPart : railParts) {
                        materials.add(new MaterialManager((int) Math.ceil(builder.costRails() * railPart.cost), railPart::matches, railPart.examples(this.settings.gauge)));
                    }
                }
                if (bedParts != null) {
                    for (TrackDefinition.TrackMaterial bedPart : bedParts) {
                        materials.add(new MaterialManager((int) Math.ceil(builder.costBed() * bedPart.cost), bedPart::matches, bedPart.examples(this.settings.gauge)));
                    }
                }

                boolean isOk = true;
                for (MaterialManager material : materials) {
                    isOk &= material.checkMaterials(player);
                }
                if (!isOk) {
                    return null;
                }

                List<ItemStack> drops = new ArrayList<>();

                for (MaterialManager material : materials) {
                    drops.addAll(material.useMaterials(player));
                }

                builder.setDrops(drops);
                if (placeTrack) builder.build();
                return drops;
            }
        }
        return null;
    }

    public TrackDefinition getDefinition() {
        return DefinitionManager.getTrack(this.settings.track);
    }

    public double getTrackHeight() {
        if (this.trackHeight == -1) {
            TrackModel model = this.getTrackModel();
            this.trackHeight = model.getHeight();
        }
        return this.trackHeight;
    }

    public TrackModel getTrackModel() {
        return DefinitionManager.getTrack(this.settings.track, this.settings.gauge.value());
    }

    public static class Mutable {
        @TagField("settings")
        public RailSettings settings;
        @TagField("placement")
        public PlacementInfo placementInfo;
        @TagField("custom")
        public PlacementInfo customInfo;
        @TagField("switchState")
        public SwitchState switchState;
        @TagField("switchForced")
        public SwitchState switchForced;
        @TagField("tablePos")
        public double tablePos;

        // Not serialized
        public boolean itemHeld;

        private Mutable(RailInfo info) {
            this.settings = info.settings;
            this.placementInfo = info.placementInfo;
            this.customInfo = info.customInfo;
            this.switchState = info.switchState;
            this.switchForced = info.switchForced;
            this.tablePos = info.tablePos;
            this.itemHeld = info.itemHeld;
        }

        private Mutable(TagCompound data) throws SerializationException {
            // Defaults
            this.tablePos = 0;
            this.itemHeld = false;

            TagSerializer.deserialize(data, this);
        }

        public RailInfo immutable() {
            return new RailInfo(
                    this.settings,
                    this.placementInfo,
                    this.customInfo,
                    this.switchState,
                    this.switchForced,
                    this.tablePos,
                    this.itemHeld
            );
        }
    }

    public static class TagMapper implements cam72cam.mod.serialization.TagMapper<RailInfo> {
        @Override
        public TagAccessor<RailInfo> apply(Class<RailInfo> type, String fieldName, TagField tag) {
            return new TagAccessor<>(
                    (d, o) -> {
                        if (o == null) {
                            d.remove(fieldName);
                            return;
                        }
                        TagCompound info = new TagCompound();
                        TagSerializer.serialize(info, new Mutable(o));
                        d.set(fieldName, info);
                    },
                    (d, w) -> d.hasKey(fieldName) ? new Mutable(d.get(fieldName)).immutable() : legacy(d)
            );
        }

        private static RailInfo legacy(TagCompound nbt) {
            // LEGACY
            // TODO REMOVE 2.0

            if (!nbt.hasKey("type") || !nbt.hasKey("turnQuarters")) {
                return null;
            }

            TrackItems type = TrackItems.valueOf(nbt.getString("type"));
            int length = nbt.getInteger("length");
            int quarters = nbt.getInteger("turnQuarters");
            ItemStack railBed = new ItemStack(nbt.get("railBed"));
            Gauge gauge = Gauge.from(nbt.getDouble("gauge"));

            if (type == TrackItems.SWITCH) {
                quarters = 4;
            }

            TagCompound newPositionFormat = new TagCompound();
            newPositionFormat.setDouble("x", nbt.getDouble("placementPositionX"));
            newPositionFormat.setDouble("y", nbt.getDouble("placementPositionY"));
            newPositionFormat.setDouble("z", nbt.getDouble("placementPositionZ"));
            nbt.set("placementPosition", newPositionFormat);

            PlacementInfo placementInfo = new PlacementInfo(nbt);
            placementInfo = new PlacementInfo(placementInfo.placementPosition, placementInfo.direction, placementInfo.yaw, null);

            SwitchState switchState = SwitchState.values()[nbt.getInteger("switchState")];
            SwitchState switchForced = SwitchState.values()[nbt.getInteger("switchForced")];
            double tablePos = nbt.getDouble("tablePos");

            RailSettings settings = new RailSettings(gauge, "default", type, length, quarters / 4F * 90, 1, TrackPositionType.FIXED, type == TrackItems.SLOPE ? TrackSmoothing.NEITHER : TrackSmoothing.BOTH, TrackDirection.NONE, railBed, cam72cam.mod.item.ItemStack.EMPTY, false, false);
            return new RailInfo(settings, placementInfo, null, switchState, switchForced, tablePos);
        }
    }

    private class MaterialManager {
        private final Function<ItemStack, Boolean> material;
        private final int count;
        private final ItemStack[] examples;
        private final boolean isDrop;

        public MaterialManager(int count, Function<ItemStack, Boolean> material, List<ItemStack> examples) {
            this(true, count, material, examples.toArray(new ItemStack[0]));
        }

        MaterialManager(boolean isDrop, int count, Function<ItemStack, Boolean> material, ItemStack... examples) {
            this.material = material;
            this.count = count;
            this.examples = examples;
            this.isDrop = isDrop;
        }

        private boolean checkMaterials(Player player) {
            int found = 0;
            for (int i = 0; i < player.getInventory().getSlotCount(); i++) {
                ItemStack stack = player.getInventory().get(i);
                if (this.material.apply(stack) && (!stack.is(IRItems.ITEM_RAIL) || new ItemRail.Data(stack).gauge == RailInfo.this.settings.gauge)) {
                    found += stack.getCount();
                }
            }

            if (found < this.count) {
                String example = Arrays.stream(this.examples)
                        .map(ItemStack::getDisplayName)
                        .limit(5)
                        .collect(Collectors.joining(" | "));
                if (this.examples.length > 3) {
                    example += ", ... ";
                }
                player.sendMessage(ChatText.BUILD_MISSING.getMessage(this.count - found, example));
                return false;
            }
            return true;
        }

        private List<ItemStack> useMaterials(Player player) {
            List<ItemStack> drops = new ArrayList<>();
            int required = this.count;
            for (int i = 0; i < player.getInventory().getSlotCount(); i++) {
                ItemStack stack = player.getInventory().get(i);
                if (this.material.apply(stack) && (!stack.is(IRItems.ITEM_RAIL) || new ItemRail.Data(stack).gauge == RailInfo.this.settings.gauge)) {
                    if (required > stack.getCount()) {
                        required -= stack.getCount();
                        ItemStack copy = stack.copy();
                        copy.setCount(stack.getCount());
                        drops.add(copy);
                        stack.setCount(0);
                        player.getInventory().set(i, stack);
                    } else if (required != 0) {
                        ItemStack copy = stack.copy();
                        copy.setCount(required);
                        drops.add(copy);
                        stack.setCount(stack.getCount() - required);
                        player.getInventory().set(i, stack);
                        required = 0;
                    }
                }
            }
            return this.isDrop ? drops : Collections.emptyList();
        }
    }
}

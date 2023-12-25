package cam72cam.immersiverailroading.tile;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.Config.ConfigDebug;
import cam72cam.immersiverailroading.IRBlocks;
import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.immersiverailroading.entity.physics.SimulationState;
import cam72cam.immersiverailroading.items.ItemRailAugment;
import cam72cam.immersiverailroading.items.ItemTrackExchanger;
import cam72cam.immersiverailroading.library.*;
import cam72cam.immersiverailroading.library.augment.Augment;
import cam72cam.immersiverailroading.physics.MovementTrack;
import cam72cam.immersiverailroading.thirdparty.trackapi.BlockEntityTrackTickable;
import cam72cam.immersiverailroading.thirdparty.trackapi.Track;
import cam72cam.immersiverailroading.util.MathUtil;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.immersiverailroading.util.SwitchUtil;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.block.IRedstoneProvider;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.fluid.FluidTank;
import cam72cam.mod.fluid.ITank;
import cam72cam.mod.item.*;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.sound.Audio;
import cam72cam.mod.sound.SoundCategory;
import cam72cam.mod.sound.StandardSound;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.SingleCache;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import trackapi.lib.Gauges;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TileRailBase extends BlockEntityTrackTickable implements IRedstoneProvider {

    private final FluidTank emptyTank = new FluidTank(null, 0);
    private final IInventory emptyInventory = new ItemStackHandler(0);

    private final SingleCache<Vec3i, Vec3i> parentCache = new SingleCache<>(parent -> parent.add(this.getPos()));
    private final SingleCache<Double, IBoundingBox> boundingBox = new SingleCache<>(height -> IBoundingBox.ORIGIN.expand(new Vec3d(1, height, 1)));
    Double cachedGauge;
    ItemStack railBedCache;
    @TagField("flexible")
    private boolean flexible;
    @TagField("parent")
    private Vec3i parent;
    @TagField("height")
    private float bedHeight = 0;
    @TagField("railHeight")
    private float railHeight = 0;
    @TagField("augment")
    private Augment augment;
    @TagField("snowLayers")
    private int snowLayers = 0;
    @TagField("replaced")
    private TagCompound replaced;
    @TagField("stockTag")
    private @Nullable String stockTag;
    private @Nullable EntityMovableRollingStock overhead;
    private Collection<TileRail> tiles;
    private boolean willBeReplaced = false;
    private int ticksExisted;
    private boolean blockUpdate;

    public float getBedHeight() {
        if (this.replaced != null && this.replaced.hasKey("height")) {
            float replacedHeight = this.replaced.getFloat("height");
            return Math.min(this.bedHeight, replacedHeight);
        }
        return this.bedHeight;
    }

    public void setBedHeight(float height) {
        this.bedHeight = height;
    }

    public double getRenderGauge() {
        double gauge = 0;
        TileRail parent = this.getParentTile();
        if (parent != null && parent.info != null) {
            gauge = parent.info.settings.gauge.getRailDistance();
        }
        if (this.getParentReplaced() != null && this.getWorld() != null) {
            parent = this.getWorld().getBlockEntity(this.getParentReplaced(), TileRail.class);
            if (parent != null && parent.info != null) {
                gauge = Math.min(gauge, parent.info.settings.gauge.getRailDistance());
            }
        }
        return gauge;
    }

    public TileRail getParentTile() {
        if (this.getParent() == null) {
            return null;
        }
        TileRail te = this.getWorld().getBlockEntity(this.getParent(), TileRail.class);
        if (te == null || te.info == null) {
            return null;
        }
        return te;
    }

    public Vec3i getParentReplaced() {
        if (this.replaced == null) {
            return null;
        }
        if (!this.replaced.hasKey("parent")) {
            return null;
        }
        return new Vec3i(this.replaced.getLong("parent")).add(this.getPos());
    }

    public Vec3i getParent() {
        if (this.parent == null) {
            if (this.ticksExisted > 5 && this.getWorld().isServer) {
                ImmersiveRailroading.warn("Invalid block without parent");
                // Might be null during init
                this.getWorld().setToAir(this.getPos());
            }
            return null;
        }
        // Assume if pos changes (piston? WE?) the TE is re-initialized
        return this.parentCache.get(this.parent);
    }

    public void setParent(Vec3i pos) {
        this.parent = pos.subtract(this.getPos());
    }

    public float getRailHeight() {
        return this.railHeight;
    }

    public void setRailHeight(float height) {
        this.railHeight = height;
    }

    @Override
    public void load(TagCompound nbt) {
        int version = 0;
        if (nbt.hasKey("version")) {
            version = nbt.getInteger("version");
        }
        switch (version) {
            case 0:
                //NOP
            case 1:
                this.parent = this.parent.subtract(this.getPos());
            case 2:
                // Nothing in base
            case 3:
                if (!nbt.hasKey("railHeight")) {
                    this.railHeight = this.bedHeight;
                }
        }
    }

    @Override
    public void save(TagCompound nbt) {
        nbt.setInteger("version", 4);
    }

    @Override
    public void writeUpdate(TagCompound nbt) {
        if (this.getRenderRailBed() != null) {
            nbt.set("renderBed", this.getRenderRailBed().toTag());
        }
    }

    public ItemStack getRenderRailBed() {
        if (this.railBedCache == null && this.getParent() != null && this.getWorld().isBlockLoaded(this.getParent())) {
            TileRail pt = this.getParentTile();
            if (pt != null) {
                this.railBedCache = pt.info.settings.railBed;
            }
        }
        return this.railBedCache;
    }

    @Override
    public void readUpdate(TagCompound nbt) {
        if (nbt.hasKey("renderBed")) {
            this.railBedCache = new ItemStack(nbt.get("renderBed"));
        }
    }

    @Override
    public void onBreak() {
        if (this.augment != null) {
            ItemStack stack = new ItemStack(IRItems.ITEM_AUGMENT, 1);
            ItemRailAugment.Data data = new ItemRailAugment.Data(stack);
            data.setAugmentId(this.augment.getId());
            data.setGauge(Gauge.getClosestGauge(this.getTrackGauge()));
            data.write();
            this.getWorld().dropItem(stack, this.getPos());
        }

        this.breakParentIfExists();
    }

    @Override
    public boolean onClick(Player player, Player.Hand hand, Facing facing, Vec3d hit) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.is(IRItems.ITEM_TRACK_EXCHANGER) && player.hasPermission(Permissions.EXCHANGE_TRACK)) {
            TileRail parent = this.getParentTile();
            ItemTrackExchanger.Data stackData = new ItemTrackExchanger.Data(stack);
            String track = stackData.track;
            ItemStack railBed = stackData.railBed;
            Gauge gauge = stackData.gauge;
            if (!track.equals(parent.info.settings.track) || !railBed.equals(parent.info.settings.railBed) || !gauge.equals(parent.info.settings.gauge)) {
                RailInfo info = parent.info.withSettings(b -> {
                    b.setTrack(track);
                    b.setRailBed(railBed);
                    b.setGauge(gauge);
                });
                Audio.playSound(this.getWorld(), this.getPos(), StandardSound.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.3f, 0.2f);
                if (!player.isCreative()) {
                    List<ItemStack> drops = parent.getDrops();
                    List<ItemStack> newDrops = info.build(player, parent.getPos(), false);
                    if (newDrops != null) { //cancel if player doesn't have all required items
                        parent.info = info;
                        drops.forEach(drop -> this.getWorld().dropItem(drop, player.getPosition()));
                        parent.setDrops(newDrops);
                        parent.markAllDirty();
                    }
                } else {
                    parent.info = info;
                    parent.markAllDirty();
                }
            }
            return true;
        }
        if (stack.is(Fuzzy.NAME_TAG) && player.hasPermission(Permissions.AUGMENT_TRACK)) {
            if (!this.getWorld().isServer) return true;

            if (player.isCrouching()) {
                this.stockTag = null;
                player.sendMessage(ChatText.RESET_AUGMENT_FILTER.getMessage());
            } else {
                this.stockTag = stack.getDisplayName();
                player.sendMessage(ChatText.SET_AUGMENT_FILTER.getMessage(this.stockTag));
            }
            return true;
        }

        if (player.hasPermission(Permissions.AUGMENT_TRACK) && this.augment != null) {
            if (this.augment.onClick(this, player, hand, facing, hit)) {
                return true;
            }
        }

        return this.handleSnowClicking(player, stack);
    }

    private boolean handleSnowClicking(Player player, ItemStack stack) {
        if (!this.getWorld().isServer) return false;

        if (stack.is(Fuzzy.SNOW_LAYER)) {
            this.handleSnowTick();
            return true;
        }

        if (stack.is(Fuzzy.SNOW_BLOCK)) {
            this.handleSnowTick(8);
            return true;
        }

        if (stack.isValidTool(ToolType.SHOVEL)) {
            this.cleanSnow();
            this.setSnowLayers(0);
            stack.damageItem(1, player);
            return true;
        }

        return false;
    }

    public void handleSnowTick() {
        if (this.snowLayers < (ConfigDebug.deepSnow ? 8 : 1)) {
            this.snowLayers += 1;
            this.markDirty();
        }
    }

    private void handleSnowTick(int repeatTimes) {
        for (int i = 0; i < repeatTimes; i++) {
            this.handleSnowTick();
        }
    }

    public void cleanSnow() {
        int snow = this.getSnowLayers();
        if (snow <= 1) return;

        this.setSnowLayers(1);
        int snowDown = snow - 1;
        for (int i = 1; i <= 3; i++) {
            Facing[] horiz = Facing.values().clone();
            if (Math.random() > 0.5) {
                // Split between sides of the track
                ArrayUtils.reverse(horiz);
            }
            for (Facing facing : horiz) {
                Vec3i precipitationHeight = this.getWorld().getPrecipitationHeight(this.getPos().offset(facing, i));
                for (int j = 0; j < 3; j++) {
                    if (this.getWorld()
                            .isAir(precipitationHeight) && !Track.isRail(this.getWorld(), precipitationHeight.down())) {
                        this.getWorld().setSnowLevel(precipitationHeight, snowDown);
                        return;
                    }
                    int currSnow = this.getWorld().getSnowLevel(precipitationHeight);
                    if (currSnow > 0 && currSnow < 8) {
                        int toAdd = Math.min(8 - currSnow, snowDown);
                        this.getWorld().setSnowLevel(precipitationHeight, currSnow + toAdd);
                        snowDown -= toAdd;
                        if (snowDown <= 0) {
                            return;
                        }
                    }
                    precipitationHeight = precipitationHeight.down();
                }
            }
        }
    }

    public int getSnowLayers() {
        return this.snowLayers;
    }

    private void setSnowLayers(int snowLayers) {
        this.snowLayers = snowLayers;
        this.markDirty();
    }

	/* TODO HACKS
	@Override
	public boolean shouldRefresh(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, net.minecraft.block.state.IBlockState oldState, net.minecraft.block.state.IBlockState newState) {
		// This works around a hack where Chunk does a removeTileEntity directly after calling breakBlock
		// We have already removed the original TE and are replacing it with one which goes with a new block
		if (this.skipNextRefresh ) {
			return false;
		}
		return super.shouldRefresh(world, pos, oldState, newState);
	}
	*/

    @Override
    public ItemStack onPick() {
        ItemStack stack = new ItemStack(IRItems.ITEM_TRACK_BLUEPRINT, 1);

        TileRail parent = this.getParentTile();
        if (parent == null) return stack;

        parent.info.settings.write(stack);
        return stack;
    }

    @Override
    public void onNeighborChange(Vec3i neighbor) {
        TileRailBase tileRailBase = this;

        if (this.getWorld().isClient) return;

        this.blockUpdate = true;

        TagCompound data = tileRailBase.getReplaced();
        while (true) {
            TileRail teParent = tileRailBase.getParentTile();
            if (teParent != null && teParent.getParentTile() != null) {
                TileRail switchTile = tileRailBase.getParentTile();
                if (tileRailBase instanceof TileRail) {
                    switchTile = (TileRail) tileRailBase;
                }
                SwitchState state = SwitchUtil.getSwitchState(switchTile);
                if (state != SwitchState.NONE) {
                    switchTile.setSwitchState(state);
                }
            }
            if (data == null) break;
            tileRailBase = (TileRailBase) this.getWorld().reconstituteBlockEntity(data);

            if (tileRailBase == null) break;
            data = tileRailBase.getReplaced();
        }
    }

    @Override
    public IInventory getInventory(Facing side) {
        if (this.augment == null) return null;
        if (!this.augment.hasInventory(this)) return null;
        if (!this.augment.canOperate(this)) return this.emptyInventory;

        Freight freight = this.getStockOverhead(Freight.class);
        if (freight == null || freight.isDead()) return this.emptyInventory;
        return freight.cargoItems;
    }

    @Override
    public ITank getTank(Facing side) {
        Augment augment = this.getAugment();
        if (augment == null) return null;
        if (!augment.hasTank(this)) return null;
        if (!this.augment.canOperate(this)) return this.emptyTank;

        FreightTank stock = this.getStockOverhead(FreightTank.class);
        if (stock == null) return this.emptyTank;
        return stock.tank;
    }

    public @Nullable Augment getAugment() {
        return this.augment;
    }

    public void setAugment(Augment augment) {
        this.augment = augment;
        this.markDirty();
    }

    @Override
    public boolean tryBreak(Player player) {
        if (player == null) return false;
        if (!player.hasPermission(Permissions.BREAK_TRACK)) return false;

        try {
            TileRailBase rail = this;
            if (rail.getReplaced() != null) {
                // new object here is important
                TileRailGag newGag = (TileRailGag) this.getWorld().reconstituteBlockEntity(rail.getReplaced());
                if (newGag == null) {
                    return true;
                }

                while (true) {
                    if (newGag.getParent() != null && this.getWorld()
                            .hasBlockEntity(newGag.getParent(), TileRail.class)) {
                        this.getWorld().setBlockEntity(this.getPos(), newGag);
                        rail.breakParentIfExists();
                        return false;
                    }
                    // Only do replacement if parent still exists

                    TagCompound data = newGag.getReplaced();
                    if (data == null) {
                        break;
                    }

                    newGag = (TileRailGag) this.getWorld().reconstituteBlockEntity(data);
                    if (newGag == null) {
                        break;
                    }
                }
            }
        } catch (StackOverflowError ex) {
            ImmersiveRailroading.error("Invalid recursive rail block at %s", this.getPos());
            ImmersiveRailroading.catching(ex);
            this.getWorld().setToAir(this.getPos());
        }
        return true;
    }

    @Override
    public IBoundingBox getBoundingBox() {
        if (this instanceof TileRailGag && (this.getParent() == null || !this.getWorld()
                .isBlockLoaded(this.getParent()))) {
            // Accessing TEs (parent) in chunks that are currently loading can cause problems
            return this.boundingBox.get(this.getFullHeight() + 0.1);
        }
        return this.boundingBox.get(this.getFullHeight() + 0.1 * (this.getTrackGauge() / Gauges.STANDARD));
    }

    private float getFullHeight() {
        return this.bedHeight + this.snowLayers / 8f;
    }

    /*
     * Capabilities tie ins
     */

    @Override
    public double getTrackGauge() {
        if (this.cachedGauge != null || this.getParent() == null)
            return this.cachedGauge != null ? this.cachedGauge : 0;

        TileRail parent = this.getParentTile();
        if (parent != null) this.cachedGauge = parent.info.settings.gauge.getRailDistance();

        return this.cachedGauge != null ? this.cachedGauge : 0;
    }

    @Override
    public Vec3d getNextPosition(Vec3d currentPosition, Vec3d motion) {
        double distanceMetersSq = motion.lengthSquared();
        double maxDistance = 0.25;
        if (distanceMetersSq * 0.9 > maxDistance * maxDistance) {
            // 0.9 forces at least one iteration + scaling
            return MovementTrack.iterativePathing(this.getWorld(), currentPosition, this, this.getTrackGauge(), motion, maxDistance);
        }
        return this.getNextPositionShort(currentPosition, motion);
    }

    public <Type extends EntityRollingStock> Type getStockOverhead(Class<Type> type) {
        if (this.overhead == null) return null;
        if (this.augment != null && !this.augment.filter(this.overhead.getDefinitionId())) return null;
        if (this.stockTag != null && this.stockTag.equals(this.overhead.tag)) return null;
        return this.overhead.as(type);
    }

    public TagCompound getReplaced() {
        return this.replaced;
    }

    public void setReplaced(TagCompound replaced) {
        this.replaced = replaced;
    }

    @SuppressWarnings("unchecked") // checked
    public <Type extends Augment> void ifAugmentPresent(@NotNull Consumer<@NotNull Type> augment) {
        if (!this.hasAugment()) return;
        augment.accept((Type) this.augment);
    }

    public boolean hasAugment() {
        return this.augment != null;
    }

    public boolean isAugmentOfType(@NotNull Class<? extends Augment> augmentType) {
        return augmentType.isInstance(this.augment);
    }

    // Called before flex track replacement
    public void setWillBeReplaced(boolean value) {
        this.willBeReplaced = value;
    }

    public TileRailBase getReplacedTile() {
        return this.replaced != null ? (TileRailBase) this.getWorld().reconstituteBlockEntity(this.replaced) : null;
    }

    public Vec3d getNextPositionShort(Vec3d currentPosition, Vec3d motion) {
        if (this.getReplaced() == null) {
            // Simple common case, maybe this does not need to be optimized out of the for loop below?
            TileRail tile = this instanceof TileRail ? (TileRail) this : this.getParentTile();
            if (tile == null) {
                return currentPosition;
            }
            //tiles = Collections.singletonList(tile);
            // Optimized version of the below looping when no overlapping occurs

            SwitchState state = SwitchUtil.getSwitchState(tile, currentPosition);

            if (state == SwitchState.STRAIGHT) {
                tile = tile.getParentTile();
            }

            Vec3d potential = MovementTrack.nextPositionDirect(this.getWorld(), currentPosition, tile, motion);
            if (potential != null) {
                return potential;
            }

            return currentPosition;
        }
        // Complex case with overlapping segments
        if (this.tiles == null) {
            Map<Vec3i, TileRail> tileMap = new HashMap<>();
            for (TileRailBase current = this; current != null; current = current.getReplacedTile()) {
                TileRail tile = current instanceof TileRail ? (TileRail) current : current.getParentTile();
                TileRail parent = tile;
                while (parent != null && !parent.getPos().equals(parent.getParent())) {
                    // Move to root of switch (if applicable)
                    parent = parent.getParentTile();
                }
                if (tile != null && parent != null) {
                    tileMap.putIfAbsent(parent.getPos(), tile);
                }
            }
            this.tiles = tileMap.values();
        }


        Vec3d nextPos = currentPosition;
        Vec3d predictedPos = currentPosition.add(motion);
        boolean hasSwitchSet = false;

        for (TileRail tile : this.tiles) {
            SwitchState state = SwitchUtil.getSwitchState(tile, currentPosition);

            if (state == SwitchState.STRAIGHT) {
                tile = tile.getParentTile();
            }

            Vec3d potential = MovementTrack.nextPositionDirect(this.getWorld(), currentPosition, tile, motion);
            if (potential != null) {
                // If the track veers onto the curved leg of a switch, try that (with angle limitation)
                // If two overlapped switches are both set, we could have a weird situation, but it's an incredibly unlikely edge case
                if (state == SwitchState.TURN) {
                    // This code is *fundamentally* broken and most of the time no-longer matters due to the complex parent position logic above
                    float other = VecUtil.toWrongYaw(potential.subtract(currentPosition));
                    float rotationYaw = VecUtil.toWrongYaw(motion);
                    double diff = MathUtil.trueModulus(other - rotationYaw, 360);
                    diff = Math.min(360 - diff, diff);
                    if (diff < 2.5) {
                        hasSwitchSet = true;
                        nextPos = potential;
                    }
                }
                // TODO should this be an else?
                // If we are not on a switch curve and closer to our target (or are on the first iteration)
                if (currentPosition == nextPos || !hasSwitchSet && potential.distanceToSquared(predictedPos) < nextPos.distanceToSquared(predictedPos)) {
                    nextPos = potential;
                }
            }
        }
        return nextPos;
    }

    @Override
    public void update() {
        if (this.getWorld().isClient) return;
        this.ticksExisted++;

        if (((int) (Math.random() * ConfigDebug.snowAccumulateRate * 10) == 0)) {
            if (this.getWorld().isSnowing(this.getPos()) && this.getWorld().canSeeSky(this.getPos().up())) {
                this.handleSnowTick();
            }
        }

        if (ConfigDebug.snowMeltRate != 0 && this.snowLayers != 0) {
            if ((int) (Math.random() * ConfigDebug.snowMeltRate * 10) == 0) {
                if (!this.getWorld().isSnowing(this.getPos())) {
                    this.setSnowLayers(this.snowLayers -= 1);
                }
            }
        }

        if (this.ticksExisted > 5 && this.blockUpdate || (this.ticksExisted % (20 * 5) == 0 && this.ticksExisted > (20 * 20))) {
            this.blockUpdate = false;

            if (this.getParent() == null || !this.getWorld().isBlockLoaded(this.getParent())) {
                return;
            }

            if (this.getParentTile() == null) {
                // Fire update event
                if (IRBlocks.BLOCK_RAIL_GAG.tryBreak(this.getWorld(), this.getPos(), null)) {
                    this.getWorld().breakBlock(this.getPos());
                }
                return;
            }

            if (Config.ConfigDamage.requireSolidBlocks && this instanceof TileRail && this.getWorld()
                    .isBlock(this.getPos(), IRBlocks.BLOCK_RAIL)) {
                double floating = ((TileRail) this).percentFloating();
                if (floating > ConfigBalance.trackFloatingPercent) {
                    if (this.tryBreak(null)) {
                        this.getWorld().breakBlock(this.getPos());
                    }
                    return;
                }
            }
        }

        Augment augment = this.getAugment();
        if (augment != null && this.augment.canOperate(this)) {
            this.clearOverhead();
            augment.update(this);
        }
    }

    private void clearOverhead() {
        if (this.overhead == null) return;
        if (this.ticksExisted % 5 != 0) return;

        SimulationState state = this.overhead.getCurrentState();
        if (state == null || !state.trackToUpdate.contains(this.getPos())) {
            this.overhead = null;
        }
    }

    private void breakParentIfExists() {
        if (this.willBeReplaced) return;

        TileRail parent = this.getParentTile();
        if (parent == null) return;

        parent.spawnDrops();
        this.getWorld().setToAir(parent.getPos());
    }

    @Override
    public int getStrongPower(Facing facing) {
        // todo: strong power seems overkill
        // return this.hasAugment() ? this.getAugment().getRedstoneOutput(this) : 0;
        return 0;
    }

    @Override
    public int getWeakPower(Facing facing) {
        Augment augment = this.getAugment();
        return augment != null ? augment.getRedstoneOutput(this) : 0;
    }

    /**
     * @return the newly applied state
     */
    public SwitchState cycleSwitchForced() {
        TileRail tileSwitch = this.findSwitchParent();
        if (tileSwitch == null) return SwitchState.NONE;

        SwitchState newForcedState = SwitchState.values()[(tileSwitch.info.switchForced.ordinal() + 1) % SwitchState.values().length];
        this.setSwitchForced(newForcedState);
        return newForcedState;
    }

    public boolean isSwitchForced() {
        TileRail tileSwitch = this.findSwitchParent();
        return tileSwitch != null && tileSwitch.info.switchForced != SwitchState.NONE;
    }

    public void setSwitchForced(SwitchState newForcedState) {
        TileRail tileSwitch = this.findSwitchParent();

        if (tileSwitch != null && newForcedState != tileSwitch.info.switchForced) {
            tileSwitch.info = tileSwitch.info.with(info -> info.switchForced = newForcedState);
        }
    }

    /**
     * Finds a parent of <code>this</code> whose type is TrackType.SWITCH. Returns null if one doesn't exist
     *
     * @return parent Rail where parent.info.settings.type.equals(TrackType.SWITCH) is true, if such a parent exists;
     * null otherwise
     */
    public TileRail findSwitchParent() {
        return this.findSwitchParent(this);
    }

    /**
     * Finds a parent of <code>current</code> whose type is TrackType.SWITCH. Returns null if one doesn't exist
     *
     * @param current RailBase whose parents are to be traversed
     * @return parent Rail where parent.info.settings.type.equals(TrackType.SWITCH) is true, if such a parent exists;
     * null otherwise
     */
    private TileRail findSwitchParent(@NotNull TileRailBase current) {
        if (current instanceof TileRail) {
            TileRail currentRail = (TileRail) current;
            if (currentRail.info.settings.type.equals(TrackType.SWITCH)) {
                return currentRail;
            }
        }

        // Prevent infinite recursion
        TileRail parentTile = current.getParentTile();
        if (current.getPos().equals(parentTile.getPos())) return null;
        return this.findSwitchParent(parentTile);
    }

    public boolean clacks() {
        return this.getParent() != null && this.getParentTile().clacks();
    }

    public float getBumpiness() {
        return this.getParent() != null ? this.getParentTile().getBumpiness() : 1;
    }

    public boolean isCog() {
        return this.getParentTile() != null && this.getParentTile().isCog();
    }

    public void stockOverhead(EntityMovableRollingStock stock) {
        this.overhead = stock;
    }

    public int getTicksExisted() {
        return this.ticksExisted;
    }

    public boolean isFlexible() {
        return this.flexible;
    }

    public void setFlexible(boolean flexible) {
        this.flexible = flexible;
    }
}

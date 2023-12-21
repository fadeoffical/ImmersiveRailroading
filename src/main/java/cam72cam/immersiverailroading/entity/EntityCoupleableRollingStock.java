package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config.ConfigDebug;
import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.entity.physics.Consist;
import cam72cam.immersiverailroading.entity.physics.Simulation;
import cam72cam.immersiverailroading.entity.physics.SimulationState;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType.ModelPosition;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.model.part.Control;
import cam72cam.immersiverailroading.net.SoundPacket;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.StrictTagMapper;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class EntityCoupleableRollingStock extends EntityMovableRollingStock {

    static {
        World.onTick(world -> {
            if (world.isClient) {
                return;
            }

            if (ConfigDebug.lagServer > 0) {
                try {
                    Thread.sleep(ConfigDebug.lagServer);
                } catch (InterruptedException e) {
                    // pass
                }
            }

            Simulation.simulate(world);
        });
    }

    @TagField(value = "consist", mapper = Consist.TagMapper.class)
    public Consist consist = new Consist(Collections.emptyList(), Collections.emptyList());
    @TagField("lastKnownFront")
    public Vec3i lastKnownFront = null;
    @TagField("lastKnownRear")
    public Vec3i lastKnownRear = null;
    @TagSync
    @TagField(value = "CoupledFront", mapper = StrictTagMapper.class)
    private UUID coupledFront = null;
    @TagSync
    @TagField("frontCouplerEngaged")
    private boolean frontCouplerEngaged = true;
    @TagSync
    @TagField(value = "CoupledBack", mapper = StrictTagMapper.class)
    private UUID coupledBack = null;
    @TagSync
    @TagField("backCouplerEngaged")
    private boolean backCouplerEngaged = true;
    @TagSync
    @TagField("hasElectricalPower")
    private boolean hasElectricalPower;
    private boolean hadElectricalPower = false;
    private int gotElectricalPowerTick = -1;

    @Override
    public ClickResult onClick(Player player, Player.Hand hand) {
        if (player.getHeldItem(hand).is(IRItems.ITEM_HOOK) && player.hasPermission(Permissions.COUPLING_HOOK)) {
            if (this.getWorld().isClient) {
                return ClickResult.ACCEPTED;
            }
            CouplerType coupler = CouplerType.FRONT;
            if (this.getCouplerPosition(CouplerType.FRONT)
                    .distanceTo(player.getPosition()) > this.getCouplerPosition(CouplerType.BACK)
                    .distanceTo(player.getPosition())) {
                coupler = CouplerType.BACK;
            }
            if (player.isCrouching()) {
                this.setCouplerEngaged(coupler, !this.isCouplerEngaged(coupler));
                if (this.isCouplerEngaged(coupler)) {
                    player.sendMessage(ChatText.COUPLER_ENGAGED.getMessage(coupler));
                } else {
                    player.sendMessage(ChatText.COUPLER_DISENGAGED.getMessage(coupler));
                }
            } else {
                EntityCoupleableRollingStock coupled = this.getCoupled(coupler);
                if (this.isCoupled(coupler) && this.isCouplerEngaged(coupler) && coupled != null) {
                    player.sendMessage(ChatText.COUPLER_STATUS_COUPLED.getMessage(
                            coupler,
                            coupled.getDefinition().name(),
                            coupled.getPosition().x,
                            coupled.getPosition().y,
                            coupled.getPosition().z
                    ));
                } else {
                    if (this.isCouplerEngaged(coupler)) {
                        player.sendMessage(ChatText.COUPLER_STATUS_DECOUPLED_ENGAGED.getMessage(coupler));
                    } else {
                        player.sendMessage(ChatText.COUPLER_STATUS_DECOUPLED_DISENGAGED.getMessage(coupler));
                    }
                }
            }
            return ClickResult.ACCEPTED;
        }
        return super.onClick(player, hand);
    }

    /*
     *
     * Overrides
     *
     */

    @Override
    public void onTick() {
        super.onTick();

        World world = this.getWorld();

        if (world.isClient) {
            // Only couple server side

            //ParticleUtil.spawnParticle(internal, EnumParticleTypes.REDSTONE, this.getCouplerPosition(CouplerType.FRONT));
            //ParticleUtil.spawnParticle(internal, EnumParticleTypes.SMOKE_NORMAL, this.getCouplerPosition(CouplerType.BACK));

            if (!this.hadElectricalPower && this.hasElectricalPower()) {
                this.gotElectricalPowerTick = this.getTickCount();
            }

            return;
        }

        for (Control<?> control : this.getDefinition().getModel().getControls()) {
            if (control.part.type == ModelComponentType.COUPLER_ENGAGED_X) {
                if (control.part.pos.contains(ModelPosition.FRONT)) {
                    if (this.isCouplerEngaged(CouplerType.FRONT) ^ (this.getControlPosition(control) < 0.5)) {
                        this.setCouplerEngaged(CouplerType.FRONT, this.getControlPosition(control) < 0.5);
                    }
                }
                if (control.part.pos.contains(ModelPosition.REAR)) {
                    if (this.isCouplerEngaged(CouplerType.BACK) ^ (this.getControlPosition(control) < 0.5)) {
                        this.setCouplerEngaged(CouplerType.BACK, this.getControlPosition(control) < 0.5);
                    }
                }
            }
        }


        if (this.getTickCount() % 5 == 0) {
            this.hasElectricalPower = false;
            this.mapTrain(this, false, stock ->
                    this.hasElectricalPower = this.hasElectricalPower ||
                            stock instanceof Locomotive && ((Locomotive) stock).providesElectricalPower()
            );
        }

        this.hadElectricalPower = this.hasElectricalPower();

        if (this.getCurrentState() != null && !this.getCurrentState().atRest || ConfigDebug.keepStockLoaded) {
            this.keepLoaded();
        }

        SimulationState state = this.getCurrentState();
        if (state != null) {
            this.setCoupledUUID(CouplerType.FRONT, state.interactingFront);
            this.setCoupledUUID(CouplerType.BACK, state.interactingRear);
            this.consist = state.consist;

            if (this.getCoupledUUID(CouplerType.FRONT) != null) {
                EntityCoupleableRollingStock front = this.getCoupled(CouplerType.FRONT);
                if (front != null) {
                    this.lastKnownFront = front.getBlockPosition();
                }
            } else {
                this.lastKnownFront = null;
            }
            if (this.getCoupledUUID(CouplerType.BACK) != null) {
                EntityCoupleableRollingStock rear = this.getCoupled(CouplerType.BACK);
                if (rear != null) {
                    this.lastKnownRear = rear.getBlockPosition();
                }
            } else {
                this.lastKnownRear = null;
            }
        }
    }

    public void keepLoaded() {
        World world = this.getWorld();
        world.keepLoaded(this.getBlockPosition());
        if (this.getCurrentState() != null && !this.getCurrentState().atRest) {
            world.keepLoaded(new Vec3i(this.guessCouplerPosition(CouplerType.FRONT)));
            world.keepLoaded(new Vec3i(this.guessCouplerPosition(CouplerType.BACK)));
        }
    }

    private Vec3d guessCouplerPosition(CouplerType coupler) {
        return this.getPosition().add(VecUtil.fromWrongYaw(this.getDefinition()
                .getLength(this.getGauge()) / 2 * (coupler == CouplerType.FRONT ? 1 : -1), this.getRotationYaw()));
    }

    public final void setCoupledUUID(CouplerType coupler, UUID id) {
        UUID target = coupler == CouplerType.FRONT ? this.coupledFront : this.coupledBack;
        if (Objects.equals(target, id)) {
            return;
        }
        if (target == null && this.isCouplerEngaged(coupler)) {
            // Technically this fires the coupling sound twice (once for each entity)
            new SoundPacket(this.getDefinition().couple_sound,
                    this.getCouplerPosition(coupler), this.getVelocity(),
                    1, 1, (int) (200 * this.getGauge().scale()), this.soundScale(), SoundPacket.PacketSoundCategory.COUPLE)
                    .sendToObserving(this);
        }

        switch (coupler) {
            case FRONT:
                this.coupledFront = id;
                break;
            case BACK:
                this.coupledBack = id;
                break;
        }
    }


    /*
     * Coupler Getters and Setters
     *
     */

    public void setCouplerEngaged(CouplerType coupler, boolean engaged) {
        switch (coupler) {
            case FRONT:
                this.frontCouplerEngaged = engaged;
                for (Control<?> control : this.getDefinition().getModel().getControls()) {
                    if (control.part.type == ModelComponentType.COUPLER_ENGAGED_X && control.part.pos.contains(ModelPosition.FRONT)) {
                        this.setControlPosition(control, engaged ? 0 : 1);
                    }
                }
                break;
            case BACK:
                this.backCouplerEngaged = engaged;
                for (Control<?> control : this.getDefinition().getModel().getControls()) {
                    if (control.part.type == ModelComponentType.COUPLER_ENGAGED_X && control.part.pos.contains(ModelPosition.REAR)) {
                        this.setControlPosition(control, engaged ? 0 : 1);
                    }
                }
                break;
        }
    }

    public final boolean isCoupled() {
        return this.isCoupled(CouplerType.FRONT) && this.isCoupled(CouplerType.BACK);
    }

    public final boolean isCoupled(CouplerType coupler) {
        return this.getCoupledUUID(coupler) != null;
    }

    public final UUID getCoupledUUID(CouplerType coupler) {
        switch (coupler) {
            case FRONT:
                return this.coupledFront;
            case BACK:
                return this.coupledBack;
            default:
                return null;
        }
    }

    public Vec3d getCouplerPosition(CouplerType coupler) {
        SimulationState state = this.getCurrentState();
        if (state != null) {
            return coupler == CouplerType.FRONT ? state.couplerPositionFront : state.couplerPositionRear;
        }
        return this.getPosition();
    }

    public final List<EntityCoupleableRollingStock> getTrain() {
        return this.getTrain(true);
    }

    /*
     * Checkers
     *
     */

    public final List<EntityCoupleableRollingStock> getTrain(boolean followDisengaged) {
        List<EntityCoupleableRollingStock> train = new ArrayList<>();
        this.mapTrain(this, followDisengaged, train::add);
        return train;
    }

    public final void mapTrain(EntityCoupleableRollingStock prev, boolean followDisengaged, Consumer<EntityCoupleableRollingStock> fn) {
        this.mapTrain(prev, true, followDisengaged, (coupleableRollingStock, direction) -> fn.accept(coupleableRollingStock));
    }

    public final void mapTrain(EntityCoupleableRollingStock prev, boolean direction, boolean followDisengaged, BiConsumer<EntityCoupleableRollingStock, Boolean> fn) {
        this.getDirectionalTrain(followDisengaged).forEach(stock -> fn.accept(stock.getStock(), stock.isDirection()));
    }

    /*
     * Helpers
     */

    public Collection<DirectionalStock> getDirectionalTrain(boolean followDisengaged) {
        HashSet<UUID> trainMap = new HashSet<UUID>();
        List<DirectionalStock> trainList = new ArrayList<DirectionalStock>();

        Function<DirectionalStock, DirectionalStock> next = (DirectionalStock current) -> {
            for (CouplerType coupler : CouplerType.values()) {
                EntityCoupleableRollingStock stock = current.getStock();
                boolean direction = current.isDirection();

                if (stock.getCoupledUUID(coupler) == null) continue;
                if (trainMap.contains(stock.getCoupledUUID(coupler))) continue;
                if (!(followDisengaged || stock.isCouplerEngaged(coupler))) continue;

                EntityCoupleableRollingStock coupled = stock.getCoupled(coupler);
                if (coupled == null) continue;

                CouplerType otherCoupler = coupled.getCouplerFor(stock);
                if (!(followDisengaged || coupled.isCouplerEngaged(otherCoupler))) continue;

                return new DirectionalStock(stock, coupled, (coupler.opposite() == otherCoupler) == direction);
            }
            return null;
        };


        DirectionalStock start = new DirectionalStock(null, this, true);
        trainMap.add(start.getStock().getUUID());
        trainList.add(start);

        for (int i = 0; i < 2; i++) {
            // Will fire for both front and back

            for (DirectionalStock current = next.apply(start); current != null; current = next.apply(current)) {
                trainMap.add(current.getStock().getUUID());
                trainList.add(current);
            }
        }


        return trainList;
    }

    public boolean isCouplerEngaged(CouplerType coupler) {
        if (coupler == null) return false;
        else if (coupler == CouplerType.FRONT) return this.frontCouplerEngaged;
        else if (coupler == CouplerType.BACK) return this.backCouplerEngaged;
        return false;
    }

    public EntityCoupleableRollingStock getCoupled(CouplerType coupler) {
        if (this.getCoupledUUID(coupler) != null) return this.findByUUID(this.getCoupledUUID(coupler));
        return null;
    }

    public CouplerType getCouplerFor(EntityCoupleableRollingStock stock) {
        if (stock == null) {
            return null;
        }
        for (CouplerType coupler : CouplerType.values()) {
            if (stock.getUUID().equals(this.getCoupledUUID(coupler))) {
                return coupler;
            }
        }
        return null;
    }

    public EntityCoupleableRollingStock findByUUID(UUID uuid) {
        return this.getWorld().getEntity(uuid, EntityCoupleableRollingStock.class);
    }

    @Override
    public boolean externalLightsEnabled() {
        return this.hasElectricalPower() && (
                this.gotElectricalPowerTick == -1 ||
                        this.getTickCount() - this.gotElectricalPowerTick > 15 ||
                        ((this.getTickCount() - this.gotElectricalPowerTick) / (int) ((Math.random() + 2) * 4)) % 2 == 0
        );
    }

    @Override
    public boolean internalLightsEnabled() {
        return this.getDefinition().hasInternalLighting() && this.hasElectricalPower() && (
                this.gotElectricalPowerTick == -1 ||
                        this.getTickCount() - this.gotElectricalPowerTick > 15 ||
                        ((this.getTickCount() - this.gotElectricalPowerTick) / (int) ((Math.random() + 2) * 4)) % 2 == 0
        );
    }

    public boolean hasElectricalPower() {
        return this.hasElectricalPower;
    }

    @Override
    public void setControlPosition(Control<?> component, float val) {
        super.setControlPosition(component, val);
        if (component.global) {
            this.mapTrain(this, false, stock -> {
                stock.controlPositions.put(component.controlGroup, this.getControlData(component));
            });
        }
    }

    public enum CouplerType {
        FRONT(0), BACK(180);

        private final float yaw;

        CouplerType(float yaw) {
            this.yaw = yaw;
        }

        public float getYaw() {
            return this.yaw;
        }

        public CouplerType opposite() {
            return this == FRONT ? BACK : FRONT;
        }

        @Override
        public String toString() {
            return (this == FRONT ? ChatText.COUPLER_FRONT : ChatText.COUPLER_BACK).toString();
        }
    }

    public static class DirectionalStock {

        private final EntityCoupleableRollingStock previous;
        private final EntityCoupleableRollingStock stock;
        private final boolean direction;

        DirectionalStock(EntityCoupleableRollingStock previous, EntityCoupleableRollingStock stock, boolean direction) {
            this.previous = previous;
            this.stock = stock;
            this.direction = direction;
        }

        public EntityCoupleableRollingStock getPrevious() {
            return this.previous;
        }

        public EntityCoupleableRollingStock getStock() {
            return this.stock;
        }

        public boolean isDirection() {
            return this.direction;
        }
    }
}

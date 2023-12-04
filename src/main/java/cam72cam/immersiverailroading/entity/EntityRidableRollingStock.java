package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.model.part.Door;
import cam72cam.immersiverailroading.model.part.Seat;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.custom.IRidable;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.serialization.SerializationException;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapper;

import java.util.*;
import java.util.stream.Collectors;

public abstract class EntityRidableRollingStock extends EntityBuildableRollingStock implements IRidable {
    @TagField(value = "payingPassengerPositions", mapper = PassengerMapper.class)
    private final Map<UUID, Vec3d> payingPassengerPositions = new HashMap<>();
    @TagField(value = "seatedPassengers", mapper = SeatedMapper.class)
    @TagSync
    private final Map<String, UUID> seatedPassengers = new HashMap<>();
    // Hack to remount players if they were seated
    private final Map<UUID, Vec3d> remount = new HashMap<>();

    public float getRidingSoundModifier() {
        return this.getDefinition().dampeningAmount;
    }

    @Override
    public ClickResult onClick(Player player, Player.Hand hand) {
        ClickResult clickRes = super.onClick(player, hand);
        if (clickRes != ClickResult.PASS) {
            return clickRes;
        }

        if (player.isCrouching()) {
            return ClickResult.PASS;
        } else if (this.isPassenger(player)) {
            return ClickResult.PASS;
        } else {
            if (this.getWorld().isServer) {
                player.startRiding(this);
            }
            return ClickResult.ACCEPTED;
        }
    }

    @Override
    public boolean canFitPassenger(Entity passenger) {
        if (passenger instanceof Player && !((Player) passenger).hasPermission(Permissions.BOARD_STOCK)) {
            return false;
        }
        return this.getPassengerCount() < this.getDefinition().getMaxPassengers();
    }

    @Override
    public boolean shouldRiderSit(Entity passenger) {
        boolean nonSeated = this.getDefinition().shouldSit != null ? this.getDefinition().shouldSit : this.gauge.shouldSit();
        return nonSeated || this.seatedPassengers.containsValue(passenger.getUUID());
    }

    @Override
    public Vec3d getMountOffset(Entity passenger, Vec3d off) {
        if (passenger.isVillager() && !this.payingPassengerPositions.containsKey(passenger.getUUID())) {
            this.payingPassengerPositions.put(passenger.getUUID(), passenger.getPosition());
        }

        if (passenger.isVillager() && !this.seatedPassengers.containsValue(passenger.getUUID())) {
            for (Seat<?> seat : this.getDefinition().getModel().getSeats()) {
                if (!this.seatedPassengers.containsKey(seat.part.key)) {
                    this.seatedPassengers.put(seat.part.key, passenger.getUUID());
                    break;
                }
            }
        }

        Vec3d seat = this.getSeatPosition(passenger.getUUID());
        if (seat != null) {
            return seat;
        }

        int wiggle = passenger.isVillager() ? 10 : 0;
        off = off.add((Math.random() - 0.5) * wiggle, 0, (Math.random() - 0.5) * wiggle);
        off = this.getDefinition().correctPassengerBounds(this.gauge, off, this.shouldRiderSit(passenger));

        return off;
    }

    private Vec3d getSeatPosition(UUID passenger) {
        String seat = this.seatedPassengers.entrySet().stream()
                .filter(x -> x.getValue().equals(passenger))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        return this.getDefinition().getModel().getSeats().stream()
                .filter(s -> s.part.key.equals(seat))
                .map(s -> new Vec3d(s.part.center.z, s.part.min.y, -s.part.center.x).scale(this.gauge.scale())
                        .subtract(0, 0.6, 0))
                .findFirst().orElse(null);
    }

    @Override
    public Vec3d onPassengerUpdate(Entity passenger, Vec3d offset) {
        if (passenger.isPlayer()) {
            offset = this.playerMovement(passenger.asPlayer(), offset);
        }

        Vec3d seat = this.getSeatPosition(passenger.getUUID());
        if (seat != null) {
            offset = seat;
        } else {
            offset = this.getDefinition().correctPassengerBounds(this.gauge, offset, this.shouldRiderSit(passenger));
        }
        offset = offset.add(0, Math.sin(Math.toRadians(this.getRotationPitch())) * offset.z, 0);

        return offset;
    }

    private Vec3d playerMovement(Player source, Vec3d offset) {
        Vec3d movement = source.getMovementInput();
        /*
        if (sprinting) {
            movement = movement.scale(3);
        }
        */
        if (movement.length() < 0.1) {
            return offset;
        }

        movement = new Vec3d(movement.x, 0, movement.z).rotateYaw(this.getRotationYaw() - source.getRotationYawHead());

        offset = offset.add(movement);

        if (this instanceof EntityCoupleableRollingStock) {
            EntityCoupleableRollingStock couplable = (EntityCoupleableRollingStock) this;

            boolean atFront = this.getDefinition().isAtFront(this.gauge, offset);
            boolean atBack = this.getDefinition().isAtRear(this.gauge, offset);
            // TODO config for strict doors
            boolean atDoor = this.isNearestDoorOpen(source);

            atFront &= atDoor;
            atBack &= atDoor;

            for (CouplerType coupler : CouplerType.values()) {
                boolean atCoupler = coupler == CouplerType.FRONT ? atFront : atBack;
                if (atCoupler && couplable.isCoupled(coupler)) {
                    EntityCoupleableRollingStock coupled = ((EntityCoupleableRollingStock) this).getCoupled(coupler);
                    if (coupled != null) {
                        if (((EntityRidableRollingStock) coupled).isNearestDoorOpen(source)) {
                            coupled.addPassenger(source);
                        }
                    } else if (this.getTickCount() > 20) {
                        ImmersiveRailroading.info(
                                "Tried to move between cars (%s, %s), but %s was not found",
                                this.getUUID(),
                                couplable.getCoupledUUID(coupler),
                                couplable.getCoupledUUID(coupler)
                        );
                    }
                    return offset;
                }
            }
        }

        if (this.getDefinition().getModel()
                .getDoors()
                .stream()
                .anyMatch(x -> x.isAtOpenDoor(source, this, Door.Types.EXTERNAL)) &&
                this.getWorld().isServer &&
                !this.getDefinition().correctPassengerBounds(this.gauge, offset, this.shouldRiderSit(source)).equals(offset)
        ) {
            this.removePassenger(source);
        }

        return offset;
    }

    private boolean isNearestDoorOpen(Player source) {
        // Find any doors that are close enough that are closed (and then negate)
        return !this.getDefinition().getModel().getDoors().stream()
                .filter(d -> d.type == Door.Types.CONNECTING)
                .filter(d -> d.center(this)
                        .distanceTo(source.getPosition()) < this.getDefinition().getLength(this.gauge) / 3)
                .min(Comparator.comparingDouble(d -> d.center(this).distanceTo(source.getPosition())))
                .filter(x -> !x.isOpen(this))
                .isPresent();
    }

    public Vec3d onDismountPassenger(Entity passenger, Vec3d offset) {
        List<String> seats = this.seatedPassengers.entrySet().stream().filter(x -> x.getValue().equals(passenger.getUUID()))
                .map(Map.Entry::getKey).collect(Collectors.toList());
        if (!seats.isEmpty()) {
            seats.forEach(this.seatedPassengers::remove);
            if (this.getWorld().isServer && passenger.isPlayer()) {
                this.remount.put(passenger.getUUID(), passenger.getPosition());
            }
        }

        //TODO calculate better dismount offset
        offset = new Vec3d(Math.copySign(this.getDefinition().getWidth(this.gauge) / 2 + 1, offset.x), 0, offset.z);

        if (this.getWorld().isServer && passenger.isVillager() && this.payingPassengerPositions.containsKey(passenger.getUUID())) {
            double distanceMoved = passenger.getPosition()
                    .distanceTo(this.payingPassengerPositions.get(passenger.getUUID()));

            int payout = (int) Math.floor(distanceMoved * Config.ConfigBalance.villagerPayoutPerMeter);

            List<ItemStack> payouts = Config.ConfigBalance.getVillagerPayout();
            if (payouts.size() != 0) {
                int type = (int) (Math.random() * 100) % payouts.size();
                ItemStack stack = payouts.get(type).copy();
                stack.setCount(payout);
                this.getWorld().dropItem(stack, this.getBlockPosition());
                // TODO drop by player or new pos?
            }
            this.payingPassengerPositions.remove(passenger.getUUID());
        }

        return offset;
    }

    @Override
    public void onTick() {
        super.onTick();

        if (this.getWorld().isServer) {
            this.remount.forEach((uuid, pos) -> {
                Player player = this.getWorld().getEntity(uuid, Player.class);
                if (player != null) {
                    player.setPosition(pos);
                    player.startRiding(this);
                }
            });
            this.remount.clear();
            for (Player source : this.getWorld().getEntities(Player.class)) {
                if (source.getRiding() == null && this.getDefinition().getModel()
                        .getDoors()
                        .stream()
                        .anyMatch(x -> x.isAtOpenDoor(source, this, Door.Types.EXTERNAL))) {
                    this.addPassenger(source);
                }
            }
        }
    }

    public void onSeatClick(String seat, Player player) {
        List<String> seats = this.seatedPassengers.entrySet().stream().filter(x -> x.getValue().equals(player.getUUID()))
                .map(Map.Entry::getKey).collect(Collectors.toList());
        if (!seats.isEmpty()) {
            seats.forEach(this.seatedPassengers::remove);
            return;
        }

        this.seatedPassengers.put(seat, player.getUUID());
    }

    private static class PassengerMapper implements TagMapper<Map<UUID, Vec3d>> {
        @Override
        public TagAccessor<Map<UUID, Vec3d>> apply(Class<Map<UUID, Vec3d>> type, String fieldName, TagField tag) {
            return new TagAccessor<>(
                    (d, o) -> d.setMap(fieldName, o, UUID::toString, (Vec3d pos) -> new TagCompound().setVec3d("pos", pos)),
                    d -> d.getMap(fieldName, UUID::fromString, t -> t.getVec3d("pos"))
            );
        }
    }

    private static class SeatedMapper implements TagMapper<Map<String, UUID>> {
        @Override
        public TagAccessor<Map<String, UUID>> apply(Class<Map<String, UUID>> type, String fieldName, TagField tag) throws SerializationException {
            return new TagAccessor<>(
                    (d, o) -> d.setMap(fieldName, o, i -> i, u -> new TagCompound().setUUID("uuid", u)),
                    d -> d.getMap(fieldName, i -> i, t -> t.getUUID("uuid"))
            );
        }
    }
}

package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.EntityBuildableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.ItemComponentType;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.thirdparty.trackapi.Track;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.CustomItem;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;
import trackapi.lib.Gauges;

import java.util.List;

public abstract class BaseItemRollingStock extends CustomItem {

    public BaseItemRollingStock(String modID, String name) {
        super(modID, name);
    }

    public static ClickResult tryPlaceStock(Player player, World world, Vec3i position, Player.Hand hand, List<ItemComponentType> parts) {
        if (!player.hasPermission(Permissions.STOCK_ASSEMBLY)) return ClickResult.REJECTED;

        ItemStack stack = player.getHeldItem(hand);
        EntityRollingStockDefinition rollingStock = new Data(stack).rollingStockDefinition;

        if (rollingStock == null) {
            player.sendMessage(ChatText.STOCK_INVALID.getMessage());
            return ClickResult.REJECTED;
        }

        List<ItemComponentType> partsToSpawnWith = parts == null ? rollingStock.getItemComponents() : parts;
        return placeStock(player, hand, world, position, rollingStock, partsToSpawnWith);
    }

    // no need to have this in a separate class
    private static ClickResult placeStock(Player player, Player.Hand hand, World world, Vec3i position, EntityRollingStockDefinition rollingStock, List<ItemComponentType> partsToSpawnWith) {
        ItemRollingStock.Data data = new ItemRollingStock.Data(player.getHeldItem(hand));

        Track track = Track.get(world, new Vec3d(position).add(0, 0.7, 0), true);
        if (track == null) return ClickResult.REJECTED;

        double trackGauge = track.getTrackGauge();
        Gauge gauge = Gauge.getClosestGauge(trackGauge);


        if (!player.isCreative() && gauge != data.gauge) {
            player.sendMessage(ChatText.STOCK_WRONG_GAUGE.getMessage());
            return ClickResult.REJECTED;
        }

        double offset = rollingStock.getCouplerPosition(EntityCoupleableRollingStock.CouplerType.BACK, gauge) - Config.ConfigDebug.couplerRange;
        float yaw = player.getYawHead();

        if (world.isServer) {
            handleServersideSpawning(world, position, rollingStock, partsToSpawnWith, yaw, gauge, data, track, offset);
        }

        if (!player.isCreative()) {
            ItemStack stack = player.getHeldItem(hand);
            stack.setCount(stack.getCount() - 1);
            player.setHeldItem(hand, stack);
        }

        return ClickResult.ACCEPTED;
    }

    private static void handleServersideSpawning(World world, Vec3i position, EntityRollingStockDefinition rollingStock, List<ItemComponentType> partsToSpawnWith, float yaw, Gauge gauge, ItemRollingStock.Data data, Track track, double offset) {
        EntityRollingStock stock = rollingStock.spawn(world, new Vec3d(position).add(0.5, 0.1, 0.5), yaw, gauge, data.texture);

        Vec3d center = stock.getPosition();
        center = track.getNextPosition(center, VecUtil.fromWrongYaw(-0.1, yaw));
        center = track.getNextPosition(center, VecUtil.fromWrongYaw(0.1, yaw));
        center = track.getNextPosition(center, VecUtil.fromWrongYaw(offset, yaw));
        stock.setPosition(center);

        if (stock instanceof EntityMovableRollingStock) {
            EntityMovableRollingStock movable = (EntityMovableRollingStock) stock;
            Track centerte = Track.get(world, center, true);
            if (centerte != null) {
                float frontDistance = movable.getDefinition().getBogeyFront(gauge);
                float rearDistance = movable.getDefinition().getBogeyRear(gauge);
                Vec3d front = centerte.getNextPosition(center, VecUtil.fromWrongYaw(frontDistance, yaw));
                Vec3d rear = centerte.getNextPosition(center, VecUtil.fromWrongYaw(rearDistance, yaw));

                movable.setRotationYaw(VecUtil.toWrongYaw(front.subtract(rear)));
                movable.setRotationPitch(VecUtil.toPitch(front.subtract(rear)) - 90);
                movable.setPosition(rear.add(front.subtract(rear)
                        .scale(frontDistance / (frontDistance - rearDistance))));

                Track frontte = Track.get(world, front, true);
                if (frontte != null) {
                    Vec3d frontNext = frontte.getNextPosition(front, VecUtil.fromWrongYaw(0.1 * gauge.scale(), movable.getRotationYaw()));
                    movable.setFrontYaw(VecUtil.toWrongYaw(frontNext.subtract(front)));
                }

                Track rearte = Track.get(world, rear, true);
                if (rearte != null) {
                    Vec3d rearNext = rearte.getNextPosition(rear, VecUtil.fromWrongYaw(0.1 * gauge.scale(), movable.getRotationYaw()));
                    movable.setRearYaw(VecUtil.toWrongYaw(rearNext.subtract(rear)));
                }
            }

            movable.newlyPlaced = true;
        }

        if (stock instanceof EntityBuildableRollingStock) {
            ((EntityBuildableRollingStock) stock).setComponents(partsToSpawnWith);
        }


        world.spawnEntity(stock);
    }

    @Override
    public String getCustomName(ItemStack stack) {
        EntityRollingStockDefinition rollingStockDefinition = new Data(stack).rollingStockDefinition;
        return rollingStockDefinition == null ? null : rollingStockDefinition.name();
    }

    protected static class Data extends ItemDataSerializer {
        @TagField(value = "defID")
        public EntityRollingStockDefinition rollingStockDefinition;

        @TagField(value = "gauge")
        public Gauge gauge;

        @TagField(value = "texture_variant")
        public String texture;

        protected Data(ItemStack stack) {
            super(stack);

            this.gauge = Gauge.getClosestGauge(Gauges.STANDARD);

            if (this.rollingStockDefinition != null && !this.rollingStockDefinition.textureNames.containsKey(this.texture)) {
                this.texture = null;
            }
        }
    }
}

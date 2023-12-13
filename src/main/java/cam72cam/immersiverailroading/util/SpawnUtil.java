package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.Config.ConfigDebug;
import cam72cam.immersiverailroading.entity.EntityBuildableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.items.ItemRollingStock;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.ItemComponentType;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.thirdparty.trackapi.ITrack;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;

import java.util.List;

public final class SpawnUtil {

    private SpawnUtil() {}

    public static ClickResult placeStock(Player player, Player.Hand hand, World worldIn, Vec3i pos, EntityRollingStockDefinition def, List<ItemComponentType> list) {
        ItemRollingStock.Data data = new ItemRollingStock.Data(player.getHeldItem(hand));

        ITrack initte = ITrack.get(worldIn, new Vec3d(pos).add(0, 0.7, 0), true);
        if (initte == null) {
            return ClickResult.REJECTED;
        }
        double trackGauge = initte.getTrackGauge();
        Gauge gauge = Gauge.getClosestGauge(trackGauge);


        if (!player.isCreative() && gauge != data.gauge) {
            player.sendMessage(ChatText.STOCK_WRONG_GAUGE.getMessage());
            return ClickResult.REJECTED;
        }

        double offset = def.getCouplerPosition(CouplerType.BACK, gauge) - ConfigDebug.couplerRange;
        float yaw = player.getYawHead();

        if (worldIn.isServer) {
            EntityRollingStock stock = def.spawn(worldIn, new Vec3d(pos).add(0.5, 0.1, 0.5), yaw, gauge, data.texture);

            Vec3d center = stock.getPosition();
            center = initte.getNextPosition(center, VecUtil.fromWrongYaw(-0.1, yaw));
            center = initte.getNextPosition(center, VecUtil.fromWrongYaw(0.1, yaw));
            center = initte.getNextPosition(center, VecUtil.fromWrongYaw(offset, yaw));
            stock.setPosition(center);

            if (stock instanceof EntityMoveableRollingStock) {
                EntityMoveableRollingStock movable = (EntityMoveableRollingStock) stock;
                ITrack centerte = ITrack.get(worldIn, center, true);
                if (centerte != null) {
                    float frontDistance = movable.getDefinition().getBogeyFront(gauge);
                    float rearDistance = movable.getDefinition().getBogeyRear(gauge);
                    Vec3d front = centerte.getNextPosition(center, VecUtil.fromWrongYaw(frontDistance, yaw));
                    Vec3d rear = centerte.getNextPosition(center, VecUtil.fromWrongYaw(rearDistance, yaw));

                    movable.setRotationYaw(VecUtil.toWrongYaw(front.subtract(rear)));
                    movable.setRotationPitch(VecUtil.toPitch(front.subtract(rear)) - 90);
                    movable.setPosition(rear.add(front.subtract(rear)
                            .scale(frontDistance / (frontDistance - rearDistance))));

                    ITrack frontte = ITrack.get(worldIn, front, true);
                    if (frontte != null) {
                        Vec3d frontNext = frontte.getNextPosition(front, VecUtil.fromWrongYaw(0.1 * gauge.scale(), movable.getRotationYaw()));
                        movable.setFrontYaw(VecUtil.toWrongYaw(frontNext.subtract(front)));
                    }

                    ITrack rearte = ITrack.get(worldIn, rear, true);
                    if (rearte != null) {
                        Vec3d rearNext = rearte.getNextPosition(rear, VecUtil.fromWrongYaw(0.1 * gauge.scale(), movable.getRotationYaw()));
                        movable.setRearYaw(VecUtil.toWrongYaw(rearNext.subtract(rear)));
                    }
                }

                movable.newlyPlaced = true;
            }

            if (stock instanceof EntityBuildableRollingStock) {
                ((EntityBuildableRollingStock) stock).setComponents(list);
            }


            worldIn.spawnEntity(stock);
        }
        if (!player.isCreative()) {
            ItemStack stack = player.getHeldItem(hand);
            stack.setCount(stack.getCount() - 1);
            player.setHeldItem(hand, stack);
        }
        return ClickResult.ACCEPTED;
    }
}

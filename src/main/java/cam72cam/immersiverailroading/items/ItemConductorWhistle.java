package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.net.SoundPacket;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.CustomItem;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.Recipes;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ItemConductorWhistle extends CustomItem {

    private static final HashMap<UUID, Integer> cooldown = new HashMap<>();

    public ItemConductorWhistle() {
        super(ImmersiveRailroading.MODID, "item_conductor_whistle");

        Fuzzy gold = Fuzzy.GOLD_INGOT;
        Recipes.shapedRecipe(this, 2, gold, gold, gold, gold, gold, gold);
    }

    @Override
    public List<CreativeTab> getCreativeTabs() {
        return Collections.singletonList(ItemTabs.MAIN_TAB);
    }

    @Override
    public int getStackSize() {
        return 1;
    }

    @Override
    public void onClickAir(Player player, World world, Player.Hand hand) {
        if (!world.isServer || !player.hasPermission(Permissions.CONDUCTOR)) return;

        int playerTicks = player.getTickCount();
        UUID playerUniqueId = player.getUUID();
        if (cooldown.containsKey(playerUniqueId)) {
            int cooldownTime = cooldown.get(playerUniqueId);
            if (cooldownTime < playerTicks || cooldownTime > playerTicks + 40) cooldown.remove(playerUniqueId);
            else return;
        }

        cooldown.put(playerUniqueId, playerTicks + 40);

        double conductorDistance = Config.ConfigBalance.villagerConductorDistance;
        double soundDistance = conductorDistance * 1.2f;
        Vec3d playerPosition = player.getPosition();
        SoundPacket packet = new SoundPacket(new Identifier(ImmersiveRailroading.MODID, "sounds/conductor_whistle.ogg"), playerPosition, Vec3d.ZERO, 0.7f, (float) (Math.random() / 4 + 0.75), (int) (soundDistance), 1, SoundPacket.PacketSoundCategory.WHISTLE);
        packet.sendToAllAround(world, playerPosition, soundDistance);

        // todo: shouldn't this be a sphere or at least cylinder? current implementation is a cube which feels weird
        //       also the config description implies it's a sphere
        IBoundingBox boundingBox = player.getBounds().grow(new Vec3d(conductorDistance, 4, conductorDistance));
        EntityCoupleableRollingStock closestRollingStockToPlayer = getClosestRollingStockToPlayer(world, boundingBox, playerPosition);
        if (closestRollingStockToPlayer == null) return;

        if (player.isCrouching()) {
            closestRollingStockToPlayer.getTrain()
                    .stream()
                    .filter(car -> car.getPosition().distanceTo(playerPosition) < conductorDistance)
                    .map(EntityCoupleableRollingStock::getPassengers)
                    .flatMap(List::stream)
                    .forEach(car -> car.getPassengers()
                            .stream()
                            .filter(Entity::isVillager)
                            .forEach(car::removePassenger));
            return;
        }

        world.getEntities(villager -> boundingBox.intersects(villager.getBounds()), Entity.class).forEach(villager -> {
            EntityCoupleableRollingStock closest = null;
            for (EntityCoupleableRollingStock rollingStock : closestRollingStockToPlayer.getTrain()) {
                if (!rollingStock.getDefinition().acceptsPassengers()) continue;
                if (!rollingStock.canFitPassenger(villager)) continue;

                Vec3d villagerPosition = villager.getPosition();
                if (closest == null || closest.getPosition().distanceTo(villagerPosition) > rollingStock.getPosition()
                        .distanceTo(villagerPosition)) {
                    closest = rollingStock;
                }
            }
            if (closest != null) closest.addPassenger(villager);
        });

    }

    private static EntityCoupleableRollingStock getClosestRollingStockToPlayer(World world, IBoundingBox boundingBox, Vec3d playerPosition) {
        List<EntityCoupleableRollingStock> nearbyRollingStock = world.getEntities(stock -> boundingBox.intersects(stock.getBounds()), EntityCoupleableRollingStock.class);
        EntityCoupleableRollingStock closestRollingStockToPlayer = null;
        for (EntityCoupleableRollingStock currentRollingStock : nearbyRollingStock) {
            if (closestRollingStockToPlayer == null) {
                closestRollingStockToPlayer = currentRollingStock;
                continue;
            }
            Vec3d closestPosition = closestRollingStockToPlayer.getPosition();
            if (closestPosition.distanceTo(playerPosition) > currentRollingStock.getPosition()
                    .distanceTo(playerPosition)) {
                closestRollingStockToPlayer = currentRollingStock;
            }
        }
        return closestRollingStockToPlayer;
    }
}

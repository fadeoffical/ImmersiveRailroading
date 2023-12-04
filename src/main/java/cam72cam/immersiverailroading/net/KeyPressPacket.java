package cam72cam.immersiverailroading.net;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Player;
import cam72cam.mod.net.Packet;
import cam72cam.mod.serialization.TagField;

public class KeyPressPacket extends Packet {
    @TagField
    private boolean disableIndependentThrottle;
    @TagField
    private KeyTypes type;

    public KeyPressPacket() {}

    public KeyPressPacket(KeyTypes type) {
        this.disableIndependentThrottle = Config.ImmersionConfig.disableIndependentThrottle;
        this.type = type;
        Player player = MinecraftClient.getPlayer();
        if (player.getRiding() instanceof EntityRollingStock) {
            // Do it client side, expect server to overwrite
            player.getRiding().as(EntityRollingStock.class).handleKeyPress(player, type, this.disableIndependentThrottle);
        }
    }

    @Override
    protected void handle() {
        Player player = this.getPlayer();
        if (player.getRiding() instanceof EntityRollingStock && player.hasPermission(Permissions.LOCOMOTIVE_CONTROL)) {
            player.getRiding().as(EntityRollingStock.class).handleKeyPress(player, this.type, this.disableIndependentThrottle);
        }
    }
}

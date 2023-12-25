package cam72cam.immersiverailroading;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.mod.entity.Player;
import cam72cam.mod.text.Command;
import cam72cam.mod.text.PlayerMessage;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;

public final class IRCommand extends Command {

    @Override
    public String getPrefix() {
        return ImmersiveRailroading.MOD_ID;
    }

    @Override
    public String getUsage() {
        return "Usage: " + ImmersiveRailroading.MOD_ID + " (reload|debug)";
    }

    @Override
    public boolean execute(Consumer<PlayerMessage> sender, Optional<Player> player, String[] args) {
        if (args.length != 1) {
            return false;
        }

        if (args[0].equals("reload")) {
            // todo: send message to player
            ImmersiveRailroading.warn("Reloading Immersive Railroading definitions");
            DefinitionManager.initDefinitions();
            ImmersiveRailroading.info("Done reloading Immersive Railroading definitions");
            return true;
        }

        if (args[0].equals("debug")) {
            if (!player.isPresent()) {
                sender.accept(PlayerMessage.direct("This command is not supported for non-players (yet)"));
                return true;
            }

            // todo: maybe we should format the message better
            //       we could display the rolling stock tag for example
            player.get()
                    .getWorld()
                    .getEntities(EntityRollingStock.class)
                    .stream()
                    .sorted(Comparator.comparing(a -> a.getUUID().toString()))
                    .map(rollingStock -> PlayerMessage.direct(String.format("%s : %s - %s : %s", rollingStock.getUUID(), rollingStock.getId(), rollingStock.getDefinitionId(), rollingStock.getPosition())))
                    .forEach(sender);
            return true;
        }

        return false;
    }
}

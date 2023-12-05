package cam72cam.immersiverailroading.entity.physics.chrono;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.world.World;

import java.util.HashMap;
import java.util.Map;

public final class ClientChronoState implements ChronoState {

    private final static Map<World, ClientChronoState> states = new HashMap<>();
    private static long lastWorldTickId = -1;

    static {
        ClientEvents.TICK.subscribe(() -> {
            if (!MinecraftClient.isReady()) return;

            // todo: This looks ugly; Add a method World#isPaused() method or similar
            // this should not take three lines of code and a static var to check

            long currWorldTickId = MinecraftClient.getPlayer().getWorld().getTicks();
            if (currWorldTickId == lastWorldTickId) return; // Handles paused singleplayer vs multiplayer
            lastWorldTickId = currWorldTickId;

            ClientChronoState state = getState(MinecraftClient.getPlayer().getWorld());
            if (state != null) state.tick();

        });
    }

    private final World world;
    private double tickID;
    private double tickSkew;

    private ClientChronoState(ServerChronoState server) {
        this.world = server.world;
        this.tickID = server.tickID;
        this.tickSkew = server.ticksPerSecond / 20;
    }

    public static void updated(ServerChronoState server) {
        if (server.world == null) {
            // Client is not in this world
            return;
        }

        if (!states.containsKey(server.world)) {
            states.put(server.world, new ClientChronoState(server));
            return;
        }

        ClientChronoState client = getState(server.world);

        // Update tickSkew based on reported value
        client.tickSkew = server.ticksPerSecond / 20;

        double delta = server.tickID - client.tickID;

        if (Math.abs(delta) > 25) {
            // We default to server time and assume something strange has happened
            ImmersiveRailroading.warn("Server/Client desync, skipping from %s to %s", client.tickID, server.tickID);
            client.tickID = server.tickID;
        } else {
            // Standard skew assumption
            client.tickSkew += Math.max(-5, Math.min(5, delta)) / 100;
        }
    }

    public static ClientChronoState getState(World world) {
        return states.get(world);
    }

    private void tick() {
        this.tickID += this.tickSkew;
    }

    @Override
    public double getTickID() {
        return this.tickID;
    }

    @Override
    public double getTickSkew() {
        return this.tickSkew;
    }
}

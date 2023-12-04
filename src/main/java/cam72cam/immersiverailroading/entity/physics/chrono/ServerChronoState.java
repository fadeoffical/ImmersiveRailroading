package cam72cam.immersiverailroading.entity.physics.chrono;

import cam72cam.mod.net.Packet;
import cam72cam.mod.net.PacketDirection;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;

import java.util.HashMap;
import java.util.Map;

public class ServerChronoState extends Packet implements ChronoState {
    private final static Map<World, ServerChronoState> states = new HashMap<>();

    static {
        World.onTick(w -> {
            ServerChronoState state = getState(w);
            if (state != null) {
                state.tick();
            }
        });
    }

    @TagField
    protected World world;
    // int -> two years of ticks, good enough
    @TagField
    protected int tickID;
    @TagField
    protected double ticksPerSecond;

    public static ServerChronoState getState(World world) {
        return states.computeIfAbsent(world, ServerChronoState::new);
    }

    private ServerChronoState(World world) {
        this.world = world;
        this.tickID = 0;
        this.ticksPerSecond = 20;
    }

    public static void register() {
        register(ServerChronoState::new, PacketDirection.ServerToClient);
    }

    private ServerChronoState() {
        // only used by networking layer
    }

    private void tick() {
        this.tickID += 1;
        this.ticksPerSecond = this.world.getTPS(20);
        if (this.tickID % 5 == 0) {
            this.sendToAll();
        }
    }

    @Override
    protected void handle() {
        ClientChronoState.updated(this);
    }

    @Override
    public double getTickID() {
        return this.tickID;
    }

    @Override
    public double getTickSkew() {
        return 1;
    }

    public int getServerTickID() {
        return this.tickID;
    }
}

package cam72cam.immersiverailroading.entity.physics.chrono;

import cam72cam.mod.net.Packet;
import cam72cam.mod.net.PacketDirection;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;

import java.util.HashMap;
import java.util.Map;

public final class ServerChronoState extends Packet implements ChronoState {

    private final static Map<World, ServerChronoState> states = new HashMap<>();

    static {
        World.onTick(world -> {
            ServerChronoState state = getState(world);
            if (state != null) state.tick();
        });
    }

    @TagField
    protected World world;

    // todo: does minecraft use long ticks? if so, maybe we should too
    // int -> two years of ticks, good enough
    @TagField
    protected int tickID;

    @TagField
    protected double ticksPerSecond;

    public static ServerChronoState getState(World world) {
        return states.computeIfAbsent(world, ServerChronoState::new);
    }

    private ServerChronoState(World world) {
        this.setWorld(world);
        this.tickID = 0;
        this.ticksPerSecond = 20.0d;
    }

    public static void register() {
        register(ServerChronoState::new, PacketDirection.ServerToClient);
    }

    private ServerChronoState() {
        // only used by networking layer
    }

    private void tick() {
        this.tickID++;
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

    public void setWorld(World world) {
        this.world = world;
    }
}

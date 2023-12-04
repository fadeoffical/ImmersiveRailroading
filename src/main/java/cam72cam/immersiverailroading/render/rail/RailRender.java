package cam72cam.immersiverailroading.render.rail;

import cam72cam.immersiverailroading.render.ExpireableMap;
import cam72cam.immersiverailroading.track.BuilderBase;
import cam72cam.immersiverailroading.track.TrackBase;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.render.opengl.RenderState;
import cam72cam.mod.world.World;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RailRender {
    private static final ExpireableMap<String, RailRender> cache = new ExpireableMap<>();

    private static final ExecutorService pool = new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors(),
            5L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("IR-TrackLoader");
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });


    private final RailInfo info;
    private boolean isLoaded;
    private boolean isLoading;
    private List<BuilderBase.VecYawPitch> renderData;
    private List<TrackBase> tracks;

    private RailRender(RailInfo info) {
        this.info = info;
        this.isLoaded = false;
    }

    public static void render(RailInfo info, World world, Vec3i pos, boolean renderOverlay, RenderState state) {
        state.lighting(false);

        RailRender renderer = get(info);

        MinecraftClient.startProfiler("rail");
        renderer.renderRailModel(state);
        MinecraftClient.endProfiler();

        if (renderOverlay) {
            Vec3d off = info.placementInfo.placementPosition;
            // TODO Is this needed?
            off = off.subtract(new Vec3d(new Vec3i(off)));
            state.translate(-off.x, -off.y, -off.z);

            MinecraftClient.startProfiler("base");
            renderer.renderRailBase(state);
            MinecraftClient.endProfiler();

            MinecraftClient.startProfiler("overlay");
            renderer.renderRailMissing(world, pos, state);
            MinecraftClient.endProfiler();
        }
    }

    public static RailRender get(RailInfo info) {
        RailRender cached = cache.get(info.uniqueID);
        if (cached == null) {
            cached = new RailRender(info);
            cache.put(info.uniqueID, cached);
        }
        return cached;
    }

    public void renderRailModel(RenderState state) {
        if (!this.isLoaded) {
            this.startLoad();
        } else {
            RailBuilderRender.renderRailBuilder(this.info, this.renderData, state);
        }
    }

    public void renderRailBase(RenderState state) {
        if (!this.isLoaded) {
            this.startLoad();
        } else {
            RailBaseRender.draw(this.info, this.tracks, state);
        }

    }

    public void renderRailMissing(World world, Vec3i pos, RenderState state) {
        if (!this.isLoaded) {
            this.startLoad();
        } else {
            RailBaseOverlayRender.draw(this.info, this.tracks, pos, state);
        }
    }

    private void startLoad() {
        if (!this.isLoading) {
            this.isLoading = true;
            pool.submit(() -> {
                // This may have some thread safety problems client side...
                // Might need to add synchronization inside the builders
                BuilderBase builder = this.info.getBuilder(MinecraftClient.getPlayer().getWorld());
                this.renderData = builder.getRenderData();
                this.tracks = builder.getTracksForRender();
                this.isLoaded = true;
                this.isLoading = false;
            });
        }
    }
}

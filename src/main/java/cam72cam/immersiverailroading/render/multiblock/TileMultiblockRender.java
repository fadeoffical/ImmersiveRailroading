package cam72cam.immersiverailroading.render.multiblock;

import cam72cam.immersiverailroading.multiblock.*;
import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.mod.render.StandardModel;
import cam72cam.mod.render.opengl.BlendMode;

import java.util.HashMap;
import java.util.Map;

public class TileMultiblockRender {

    private static final Map<String, IMultiblockRender> renderers = new HashMap<>();

    static {
        renderers.put(SteamHammerMultiBlock.NAME, new SteamHammerRender());
        renderers.put(PlateRollerMultiBlock.NAME, new PlateRollerRender());
        renderers.put(RailRollerMultiBlock.NAME, new RailRollerRender());
        renderers.put(BoilerRollerMultiBlock.NAME, new BoilerRollerRender());
        renderers.put(CastingMultiBlock.NAME, new CastingRender());
    }

    public static StandardModel render(TileMultiblock te) {
        if (te.isLoaded() && te.isRender()) {
            IMultiblockRender renderer = renderers.get(te.getName());
            if (renderer != null) {
                return new StandardModel().addCustom((state, partialTicks) -> {
                    state.blend(BlendMode.OPAQUE);
                    renderer.render(te, state, partialTicks);
                });
            }
        }
        return null;
    }
}

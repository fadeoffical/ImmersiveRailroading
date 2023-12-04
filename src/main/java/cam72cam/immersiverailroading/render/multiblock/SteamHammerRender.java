package cam72cam.immersiverailroading.render.multiblock;

import cam72cam.immersiverailroading.multiblock.SteamHammerMultiblock.SteamHammerInstance;
import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.mod.model.obj.OBJModel;
import cam72cam.mod.render.obj.OBJRender;
import cam72cam.mod.render.opengl.RenderState;
import cam72cam.mod.resource.Identifier;

import java.util.ArrayList;

public class SteamHammerRender implements IMultiblockRender {
    private OBJModel model;
    private ArrayList<String> hammer;
    private ArrayList<String> rest;

    @Override
    public void render(TileMultiblock te, RenderState state, float partialTicks) {
        if (this.model == null) {
            try {
                this.model = new OBJModel(new Identifier("immersiverailroading:models/multiblocks/steam_hammer.obj"), -0.1f, null);
                this.hammer = new ArrayList<>();
                this.rest = new ArrayList<>();
                for (String group : this.model.groups()) {
                    if (group.contains("Hammer")) {
                        this.hammer.add(group);
                    } else {
                        this.rest.add(group);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        SteamHammerInstance mb = (SteamHammerInstance) te.getMultiblock();

        //state.scale(2, 2, 2);
        state.translate(0.5, 0, 0.5);
        state.rotate(te.getRotation(), 0, 1, 0);
        try (OBJRender.Binding vbo = this.model.binder().bind(state)) {
            vbo.draw(this.rest);
            double dist;
            if (mb != null && mb.hasPower()) {
                if (te.getCraftProgress() != 0) {
                    dist = -(Math.abs((te.getRenderTicks() + partialTicks) % 10 - 5)) / 4f;
                } else {
                    dist = -(Math.abs((te.getRenderTicks() + partialTicks) % 30 - 15)) / 14f;
                }
            } else {
                dist = 0;
            }
            vbo.draw(this.hammer, s -> s.translate(0, dist, 0));
        }
    }
}

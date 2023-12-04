package cam72cam.immersiverailroading.net;

import cam72cam.immersiverailroading.render.tile.MultiPreviewRender;
import cam72cam.immersiverailroading.tile.TileRailPreview;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.net.Packet;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;

public class PreviewRenderPacket extends Packet {
    @TagField
    private TileRailPreview preview;
    @TagField
    private World world;
    @TagField
    private Vec3i removed;

    public PreviewRenderPacket() {}

    public PreviewRenderPacket(TileRailPreview preview) {
        this.preview = preview;
    }

    public PreviewRenderPacket(World world, Vec3i removed) {
        this.world = world;
        this.removed = removed;
    }

    @Override
    public void handle() {
        if (this.removed != null) {
            MultiPreviewRender.remove(this.world, this.removed);
            return;
        }

        if (this.preview == null || this.preview.getWorld() != this.getPlayer().getWorld()) {
            return;
        }

        MultiPreviewRender.add(this.preview);
    }
}

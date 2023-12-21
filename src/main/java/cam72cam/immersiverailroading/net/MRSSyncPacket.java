package cam72cam.immersiverailroading.net;

import cam72cam.immersiverailroading.entity.EntityMovableRollingStock;
import cam72cam.immersiverailroading.physics.TickPos;
import cam72cam.mod.net.Packet;
import cam72cam.mod.serialization.TagField;

import java.util.List;

/*
 * Movable rolling stock sync packet
 */
public class MRSSyncPacket extends Packet {
    @TagField
    private EntityMovableRollingStock stock;
    @TagField(mapper = TickPos.ListTagMapper.class)
    private List<TickPos> positions;

    public MRSSyncPacket() {}

    public MRSSyncPacket(EntityMovableRollingStock mrs, List<TickPos> positions) {
        this.stock = mrs;
        this.positions = positions;
    }

    @Override
    public void handle() {
        if (this.stock != null) {
            this.stock.handleTickPosPacket(this.positions);
        }
    }
}

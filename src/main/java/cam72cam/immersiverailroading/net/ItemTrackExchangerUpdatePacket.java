package cam72cam.immersiverailroading.net;

import cam72cam.immersiverailroading.items.ItemTrackExchanger;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.net.Packet;
import cam72cam.mod.serialization.TagField;

public class ItemTrackExchangerUpdatePacket extends Packet {
    @TagField
    private String track;

    @TagField
    private ItemStack railBed;

    @TagField
    private Gauge gauge;

    public ItemTrackExchangerUpdatePacket() {}

    public ItemTrackExchangerUpdatePacket(String track, ItemStack railBed, Gauge gauge) {
        this.track = track;
        this.railBed = railBed;
        this.gauge = gauge;
    }

    @Override
    public void handle() {
        ItemStack stack = this.getPlayer().getHeldItem(Player.Hand.PRIMARY);
        ItemTrackExchanger.Data data = new ItemTrackExchanger.Data(stack);
        data.track = this.track;
        data.railBed = this.railBed;
        data.gauge = this.gauge;
        data.write();
        this.getPlayer().setHeldItem(Player.Hand.PRIMARY, stack);
    }
}

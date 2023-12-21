package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.SteamLocomotive;
import cam72cam.immersiverailroading.gui.overlay.GuiBuilder;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.model.SteamLocomotiveModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.resource.Identifier;

import java.io.IOException;
import java.util.List;

public class LocomotiveSteamDefinition extends LocomotiveDefinition {
    public Quilling quill;
    public SoundDefinition whistle;
    public SoundDefinition idle;
    public Identifier chuff;
    public Identifier pressure;
    public Identifier cylinder_drain;
    public boolean tender_auto_feed;
    public boolean cab_forward;
    private double tankCapacity_l;
    private double maxPSI;
    private double numSlots;
    private double width;

    public LocomotiveSteamDefinition(String defID, DataBlock data) throws Exception {
        super(SteamLocomotive.class, defID, data);
    }

    @Override
    protected Identifier defaultDataLocation() {
        return new Identifier(ImmersiveRailroading.MODID, "rolling_stock/default/steam.caml");
    }

    @Override
    public void loadData(DataBlock data) throws Exception {
        super.loadData(data);
        DataBlock properties = data.getBlock("properties");
        if (this.isCabCar()) {
            this.tankCapacity_l = 0;
            this.maxPSI = 0;
            this.numSlots = 0;
            this.width = 0;
            this.tender_auto_feed = false;
        } else {
            DataBlock firebox = data.getBlock("firebox");

            this.tankCapacity_l = properties.getValue("water_capacity_l").asInteger() * this.internal_inv_scale;
            this.maxPSI = Math.ceil(properties.getValue("max_psi").asInteger() * this.internal_inv_scale);
            this.numSlots = Math.ceil(firebox.getValue("slots").asInteger() * this.internal_inv_scale);
            this.width = Math.ceil(firebox.getValue("width").asInteger() * this.internal_inv_scale);
            this.tender_auto_feed = properties.getValue("tender_auto_feed").asBoolean(true);
        }
        this.cab_forward = properties.getValue("cab_forward").asBoolean(false);

        DataBlock sounds = data.getBlock("sounds");
        this.whistle = SoundDefinition.getOrDefault(sounds, "whistle");
        this.idle = SoundDefinition.getOrDefault(sounds, "idle");
        this.chuff = sounds.getValue("chuff").asIdentifier();
        this.pressure = sounds.getValue("pressure").asIdentifier();
        this.bell = SoundDefinition.getOrDefault(sounds, "bell");
        this.cylinder_drain = sounds.getValue("cylinder_drain").asIdentifier();

        List<DataBlock> quilling = sounds.getBlocks("quilling");
        if (quilling != null) {
            this.quill = new Quilling(quilling);
        }
        if (this.whistle == null && (this.quill == null || !this.quill.canLoad())) {
            this.quill = new Quilling(new Identifier(ImmersiveRailroading.MODID, "sounds/steam/default/quill.ogg"));
        }
    }

    @Override
    protected StockModel<?, ?> createModel() throws Exception {
        return new SteamLocomotiveModel(this);
    }

    @Override
    public StockModel<?, ?> getModel() {
        return super.getModel();
    }

    @Override
    protected GuiBuilder getDefaultOverlay(DataBlock data) throws IOException {
        return this.readCabCarFlag(data) ?
                GuiBuilder.parse(new Identifier(ImmersiveRailroading.MODID, "gui/default/cab_car.caml")) :
                GuiBuilder.parse(new Identifier(ImmersiveRailroading.MODID, "gui/default/steam.caml"));
    }

    public FluidQuantity getTankCapacity(Gauge gauge) {
        return FluidQuantity.fromLiters((int) Math.ceil(this.tankCapacity_l * gauge.scale()))
                .min(FluidQuantity.fromBuckets(1))
                .roundBuckets();
    }

    public int getMaxPSI(Gauge gauge) {
        return (int) Math.ceil(this.maxPSI * gauge.scale());
    }

    public int getInventorySize(Gauge gauge) {
        return (int) Math.ceil(this.numSlots * gauge.scale());
    }

    public int getInventoryWidth(Gauge gauge) {
        return (int) Math.max(3, Math.ceil(this.width * gauge.scale()));
    }
}

package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.model.FreightModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.util.DataBlock;

public abstract class FreightDefinition extends EntityRollingStockDefinition {

    private boolean showCurrentLoadOnly;

    FreightDefinition(Class<? extends EntityRollingStock> type, String defID, DataBlock data) throws Exception {
        super(type, defID, data);
    }

    @Override
    public void loadData(DataBlock data) throws Exception {
        super.loadData(data);
        this.showCurrentLoadOnly = data.getValue("show_current_load_only").asBoolean();
    }

    @Override
    protected StockModel<?, ?> createModel() throws Exception {
        return new FreightModel<>(this);
    }

    public boolean shouldShowCurrentLoadOnly() {
        return this.showCurrentLoadOnly;
    }

}

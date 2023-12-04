package cam72cam.immersiverailroading.gui.container;

import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.mod.gui.container.IContainerBuilder;

public class FreightContainer extends BaseContainer {
    public Freight stock;

    public FreightContainer(Freight stock) {
        this.stock = stock;
    }

    public void draw(IContainerBuilder container) {
        int currY = 0;
        currY = container.drawTopBar(0, currY, this.stock.getInventoryWidth());
        currY = container.drawSlotBlock(this.stock.cargoItems, 0, this.stock.getInventoryWidth(), 0, currY);
        currY = container.drawPlayerInventoryConnector(0, currY, this.stock.getInventoryWidth());
        currY = container.drawPlayerInventory(currY, this.stock.getInventoryWidth());
        this.drawName(container, this.stock);
    }

    @Override
    public int getSlotsX() {
        return this.stock.getInventoryWidth();
    }

    @Override
    public int getSlotsY() {
        return this.stock.getInventorySize() / this.stock.getInventoryWidth();
    }
}

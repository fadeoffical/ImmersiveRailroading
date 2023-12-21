package cam72cam.immersiverailroading.gui.container;

import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.mod.gui.container.IContainerBuilder;
import cam72cam.mod.render.opengl.RenderState;

public class FreightContainer extends BaseContainer {

    public Freight stock;

    public FreightContainer(Freight stock) {
        this.stock = stock;
    }

    @Override
    public void draw(IContainerBuilder container, RenderState state) {
        int currentY = 0;
        currentY = container.drawTopBar(0, currentY, this.stock.getInventoryWidth());
        currentY = container.drawSlotBlock(this.stock.cargoItems, 0, this.stock.getInventoryWidth(), 0, currentY);
        currentY = container.drawPlayerInventoryConnector(0, currentY, this.stock.getInventoryWidth());
        currentY = container.drawPlayerInventory(currentY, this.stock.getInventoryWidth());
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

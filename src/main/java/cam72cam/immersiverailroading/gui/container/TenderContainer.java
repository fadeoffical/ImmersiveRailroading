package cam72cam.immersiverailroading.gui.container;

import cam72cam.immersiverailroading.entity.Tender;
import cam72cam.mod.gui.container.IContainerBuilder;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;

public class TenderContainer extends BaseContainer {
    public final Tender stock;
    private final ItemStack template;

    public TenderContainer(Tender stock) {
        this.stock = stock;
        this.template = Fuzzy.BUCKET.example();
    }

    public void draw(IContainerBuilder container) {
        int currY = 0;
        int horizSlots = this.stock.getInventoryWidth();
        int inventoryRows = (int) Math.ceil(((double) this.stock.getInventorySize() - 2) / horizSlots);
        int slotY = 0;

        currY = container.drawTopBar(0, currY, horizSlots);

        int tankY = currY;
        for (int i = 0; i < inventoryRows; i++) {
            currY = container.drawSlotRow(null, 0, horizSlots, 0, currY);
            if (i == 0) {
                slotY = currY;
            }
        }

        container.drawTankBlock(0, tankY, horizSlots, inventoryRows, this.stock.getLiquid(), this.stock.getLiquidAmount() / (float) this.stock.getTankCapacity()
                .asMillibuckets());

        currY = container.drawSlotBlock(this.stock.cargoItems, 2, this.stock.getInventoryWidth(), 0, currY);

        container.drawSlotOverlay(this.template, 1, slotY);
        container.drawSlot(this.stock.cargoItems, 0, 1, slotY);
        container.drawSlot(this.stock.cargoItems, 1, 1 + horizSlots * 16, slotY);

        String quantityStr = String.format("%s/%s", this.stock.getLiquidAmount(), this.stock.getTankCapacity().asMillibuckets());
        container.drawCenteredString(quantityStr, 0, slotY);

        currY = container.drawPlayerInventoryConnector(0, currY, horizSlots);
        currY = container.drawPlayerInventory(currY, horizSlots);
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

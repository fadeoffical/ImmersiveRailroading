package cam72cam.immersiverailroading.gui.container;

import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.mod.gui.container.IContainerBuilder;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;

public class TankContainer extends BaseContainer {
    public final FreightTank stock;
    private final ItemStack template;

    public TankContainer(FreightTank tank) {
        this.stock = tank;
        this.template = Fuzzy.BUCKET.example();
    }

    public void draw(IContainerBuilder container) {
        int currY = 0;
        int horizSlots = 10;
        int inventoryRows = 4;
        int slotY = 0;

        currY = container.drawTopBar(0, currY, horizSlots);

        int tankY = currY;
        for (int i = 0; i < 4; i++) {
            currY = container.drawSlotRow(null, 0, horizSlots, 0, currY);
            if (i == 0) {
                slotY = currY;
            }
        }

        container.drawTankBlock(0, tankY, horizSlots, inventoryRows, this.stock.getLiquid(), this.stock.getLiquidAmount() / (float) this.stock.getTankCapacity()
                .asMillibuckets());

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
        return 10;
    }

    @Override
    public int getSlotsY() {
        return 4;
    }
}

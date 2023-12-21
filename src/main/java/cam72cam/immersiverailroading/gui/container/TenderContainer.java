package cam72cam.immersiverailroading.gui.container;

import cam72cam.immersiverailroading.entity.Tender;
import cam72cam.mod.gui.container.IContainerBuilder;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.render.opengl.RenderState;

public class TenderContainer extends BaseContainer {

    public final Tender stock;
    private final ItemStack template;

    public TenderContainer(Tender stock) {
        this.stock = stock;
        this.template = Fuzzy.BUCKET.example();
    }

    @Override
    public void draw(IContainerBuilder container, RenderState state) {
        int currentY = 0;
        int horizontalSlots = this.getSlotsX();
        int inventoryRows = (int) Math.ceil(((double) this.stock.getInventorySize() - 2) / horizontalSlots);
        int slotY = 0;

        currentY = container.drawTopBar(0, currentY, horizontalSlots);

        int tankY = currentY;
        for (int i = 0; i < inventoryRows; i++) {
            currentY = container.drawSlotRow(null, 0, horizontalSlots, 0, currentY);
            if (i == 0) {
                slotY = currentY;
            }
        }

        container.drawTankBlock(0, tankY, horizontalSlots, inventoryRows, this.stock.getLiquid(), this.stock.getLiquidAmount() / (float) this.stock.getTankCapacity()
                .asMillibuckets());

        currentY = container.drawSlotBlock(this.stock.cargoItems, 2, this.stock.getInventoryWidth(), 0, currentY);

        container.drawSlotOverlay(this.template, 1, slotY);
        container.drawSlot(this.stock.cargoItems, 0, 1, slotY);
        container.drawSlot(this.stock.cargoItems, 1, 1 + horizontalSlots * 16, slotY);

        String quantityStr = String.format("%s/%s", this.stock.getLiquidAmount(), this.stock.getTankCapacity().asMillibuckets());
        container.drawCenteredString(quantityStr, 0, slotY);

        currentY = container.drawPlayerInventoryConnector(0, currentY, horizontalSlots);
        currentY = container.drawPlayerInventory(currentY, horizontalSlots);
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

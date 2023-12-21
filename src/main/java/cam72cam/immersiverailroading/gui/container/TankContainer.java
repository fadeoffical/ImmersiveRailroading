package cam72cam.immersiverailroading.gui.container;

import cam72cam.immersiverailroading.entity.FreightTank;
import cam72cam.mod.gui.container.IContainerBuilder;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.render.opengl.RenderState;

public class TankContainer extends BaseContainer {

    private static final int INVENTORY_WIDTH = 10;
    private static final int INVENTORY_HEIGHT = 4;


    public final FreightTank stock;
    private final ItemStack template;

    public TankContainer(FreightTank tank) {
        this.stock = tank;
        this.template = Fuzzy.BUCKET.example();
    }

    @Override
    public void draw(IContainerBuilder container, RenderState state) {
        int currentY = 0;
        int slotY = 0;

        currentY = container.drawTopBar(0, currentY, INVENTORY_WIDTH);

        int tankY = currentY;
        for (int i = 0; i < INVENTORY_HEIGHT; i++) {
            currentY = container.drawSlotRow(null, 0, INVENTORY_WIDTH, 0, currentY);
            if (i == 0) {
                slotY = currentY;
            }
        }

        container.drawTankBlock(0, tankY, INVENTORY_WIDTH, INVENTORY_HEIGHT, this.stock.getLiquid(), this.stock.getLiquidAmount() / (float) this.stock.getTankCapacity()
                .asMillibuckets());

        container.drawSlotOverlay(this.template, 1, slotY);
        container.drawSlot(this.stock.cargoItems, 0, 1, slotY);
        container.drawSlot(this.stock.cargoItems, 1, 1 + INVENTORY_WIDTH * 16, slotY);

        String quantityFormatted = String.format("%s/%s", this.stock.getLiquidAmount(), this.stock.getTankCapacity().asMillibuckets());
        container.drawCenteredString(quantityFormatted, 0, slotY);

        currentY = container.drawPlayerInventoryConnector(0, currentY, INVENTORY_WIDTH);
        currentY = container.drawPlayerInventory(currentY, INVENTORY_WIDTH);
        this.drawName(container, this.stock);
    }

    @Override
    public int getSlotsX() {
        return INVENTORY_WIDTH;
    }

    @Override
    public int getSlotsY() {
        return INVENTORY_HEIGHT;
    }
}

package cam72cam.immersiverailroading.gui.container;

import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.mod.gui.container.IContainerBuilder;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;

import java.util.Map;

public class SteamLocomotiveContainer extends BaseContainer {
    public final LocomotiveSteam stock;
    private final ItemStack template;

    public SteamLocomotiveContainer(LocomotiveSteam stock) {
        this.stock = stock;
        this.template = Fuzzy.BUCKET.example();
    }

    public void draw(IContainerBuilder container) {
        int currY = 0;
        int horizSlots = this.stock.getInventoryWidth();
        int inventoryRows = (int) Math.ceil(((double) this.stock.getInventorySize() - 2) / horizSlots);
        int slotY = 0;

        currY = container.drawTopBar(0, currY, horizSlots * 2);

        int tankY = currY;
        for (int i = 0; i < inventoryRows; i++) {
            currY = container.drawSlotRow(null, 0, horizSlots * 2, 0, currY);
            if (i == 0) {
                slotY = currY;
            }
        }

        container.drawTankBlock(0, tankY, horizSlots * 2, inventoryRows, this.stock.getLiquid(), this.stock.getLiquidAmount() / (float) this.stock.getTankCapacity()
                .asMillibuckets());

        currY = container.drawBottomBar(0, currY, horizSlots * 2);

        int containerY = currY;
        currY = container.drawSlotBlock(this.stock.cargoItems, 2, this.stock.getInventoryWidth(), 0, currY);
        Map<Integer, Integer> burnTime = this.stock.getBurnTime();
        Map<Integer, Integer> burnMax = this.stock.getBurnMax();
        for (int slot : burnTime.keySet()) {
            int time = this.stock.getBurnTime().get(slot);
            if (time != 0) {
                float perc = Math.min(1f, (float) time / burnMax.get(slot));

                int xSlot = (slot - 2) % horizSlots;
                int ySlot = (slot - 2) / horizSlots;


                container.drawSlotOverlay("minecraft:blocks/fire_layer_1", xSlot * 18 + ((horizSlots) * 9), containerY + ySlot * 18, perc, 0x77c64306);
            }
        }

        container.drawSlotOverlay(this.template, 1, slotY);
        container.drawSlot(this.stock.cargoItems, 0, 1, slotY);
        container.drawSlot(this.stock.cargoItems, 1, (horizSlots * 2 - 1) * 18 - 1, slotY);

        String quantityStr = String.format("%s/%s", this.stock.getLiquidAmount(), this.stock.getTankCapacity().asMillibuckets());
        container.drawCenteredString(quantityStr, 0, slotY);

        currY = container.drawPlayerInventoryConnector(0, currY, horizSlots);
        currY = container.drawPlayerInventory(currY, horizSlots * 2);
        this.drawName(container, this.stock);
    }

    @Override
    public int getSlotsX() {
        return this.stock.getInventoryWidth() * 2;
    }

    @Override
    public int getSlotsY() {
        return this.stock.getInventorySize() / this.stock.getInventoryWidth();
    }
}

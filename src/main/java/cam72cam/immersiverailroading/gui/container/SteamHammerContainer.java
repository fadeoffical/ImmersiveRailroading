package cam72cam.immersiverailroading.gui.container;

import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.gui.container.IContainer;
import cam72cam.mod.gui.container.IContainerBuilder;
import cam72cam.mod.render.opengl.RenderState;

public class SteamHammerContainer implements IContainer {

    private final TileMultiblock tile;

    public SteamHammerContainer(TileMultiblock tile) {
        this.tile = tile;
    }

    @Override
    public void draw(IContainerBuilder container, RenderState state) {
        int currentY = 0;
        int horizontalSlots = 10;
        currentY = container.drawTopBar(0, currentY, horizontalSlots);

        int tankY = currentY;
        int slotY = 0;

        int inventoryRows = 4;
        for (int i = 0; i < inventoryRows; i++) {
            currentY = container.drawSlotRow(null, 0, horizontalSlots, 0, currentY);
            if (i == 0) {
                slotY = currentY;
            }
        }

        container.drawTankBlock(0, tankY, horizontalSlots, inventoryRows, Fluid.LAVA, this.tile.getCraftProgress() / 100f);

        container.drawSlot(this.tile.getContainer(), 0, 0, slotY);
        container.drawSlot(this.tile.getContainer(), 1, 16 * horizontalSlots, slotY);

        currentY = container.drawPlayerInventoryConnector(0, currentY, horizontalSlots);
        currentY = container.drawPlayerInventory(currentY, horizontalSlots);
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

package cam72cam.immersiverailroading.gui;

import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.items.ItemPlate;
import cam72cam.immersiverailroading.items.ItemRollingStock;
import cam72cam.immersiverailroading.library.CraftingType;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.PlateType;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.mod.entity.Player;
import cam72cam.mod.gui.screen.Button;
import cam72cam.mod.gui.screen.IScreen;
import cam72cam.mod.gui.screen.IScreenBuilder;
import cam72cam.mod.item.ItemStack;

import java.util.Collections;
import java.util.List;

import static cam72cam.immersiverailroading.gui.ClickListHelper.next;

public class PlateRollerGUI implements IScreen {

    private final TileMultiblock multiBlockTile;

    private Button pickerButton;
    private Button gaugeButton;

    private Gauge gauge;

    private ItemStack currentItem;

    public PlateRollerGUI(TileMultiblock multiBlockTile) {
        this.multiBlockTile = multiBlockTile;
        this.currentItem = multiBlockTile.getCraftItem();

        if (this.currentItem == null || this.currentItem.isEmpty()) {
            this.currentItem = new ItemStack(IRItems.ITEM_PLATE, 1);
        }

        ItemPlate.ItemPlateData data = new ItemPlate.ItemPlateData(this.currentItem);
        this.gauge = data.gauge;
    }

    @Override
    public void init(IScreenBuilder screen) {
        this.gaugeButton = new Button(screen, -100, -24, GuiText.SELECTOR_GAUGE.translate(this.gauge)) {
            @Override
            public void onClick(Player.Hand hand) {
                if (!PlateRollerGUI.this.currentItem.isEmpty()) {
                    EntityRollingStockDefinition def = new ItemPlate.ItemPlateData(PlateRollerGUI.this.currentItem).rollingStockDefinition;
                    if (def != null && ConfigBalance.DesignGaugeLock) {
                        List<Gauge> validGauges = Collections.singletonList(Gauge.getClosestGauge(def.recommended_gauge.getRailDistance()));
                        PlateRollerGUI.this.gauge = next(validGauges, PlateRollerGUI.this.gauge, hand);
                    } else {
                        PlateRollerGUI.this.gauge = next(Gauge.getRegisteredGauges(), PlateRollerGUI.this.gauge, hand);
                    }
                }
                PlateRollerGUI.this.gaugeButton.setText(GuiText.SELECTOR_GAUGE.translate(PlateRollerGUI.this.gauge));
                PlateRollerGUI.this.sendPacket();
            }
        };

        this.pickerButton = new Button(screen, -100, -24 + 2 * 30, "") {
            @Override
            public void onClick(Player.Hand hand) {
                CraftPicker.showCraftPicker(screen, null, CraftingType.PLATE_BOILER, item -> {
                    if (item == null) return;

                    if (item.is(IRItems.ITEM_ROLLING_STOCK)) {
                        ItemRollingStock.Data stock = new ItemRollingStock.Data(item);
                        item = new ItemStack(IRItems.ITEM_PLATE, 1);
                        ItemPlate.ItemPlateData data = new ItemPlate.ItemPlateData(item);
                        data.rollingStockDefinition = stock.rollingStockDefinition;
                        data.gauge = PlateRollerGUI.this.gauge;
                        data.plateType = PlateType.BOILER;
                        data.write();
                    }

                    ItemPlate.ItemPlateData data = new ItemPlate.ItemPlateData(item);
                    EntityRollingStockDefinition def = data.rollingStockDefinition;
                    if (def != null && !PlateRollerGUI.this.gauge.isModelGauge() && PlateRollerGUI.this.gauge.getRailDistance() != def.recommended_gauge.getRailDistance()) {
                        PlateRollerGUI.this.gauge = def.recommended_gauge;
                        PlateRollerGUI.this.gaugeButton.setText(GuiText.SELECTOR_GAUGE.translate(PlateRollerGUI.this.gauge));
                    }
                    PlateRollerGUI.this.currentItem = item;
                    PlateRollerGUI.this.updatePickerButton();
                    PlateRollerGUI.this.sendPacket();
                });
            }
        };
        this.updatePickerButton();
    }

    @Override
    public void onEnterKey(IScreenBuilder builder) {
        this.sendPacket();
        builder.close();
    }

    @Override
    public void onClose() {
        this.sendPacket();
    }

    private void sendPacket() {
        ItemPlate.ItemPlateData data = new ItemPlate.ItemPlateData(this.currentItem);
        data.gauge = this.gauge;
        data.write();
        this.currentItem.setCount(Math.min(64, Math.max(1, (int) Math.floor(data.plateType.platesPerBlock() / this.gauge.scale()))));
        this.multiBlockTile.setCraftItem(this.currentItem);
    }

    private void updatePickerButton() {
        this.pickerButton.setText(
                GuiText.SELECTOR_PLATE_TYPE.translate(this.currentItem != null ? this.currentItem.getDisplayName() : "")
        );
    }
}

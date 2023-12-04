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
    private Button gaugeButton;

    private Gauge gauge;

    private Button pickerButton;

    private final TileMultiblock tile;
    private ItemStack currentItem;

    public PlateRollerGUI(TileMultiblock te) {
        this.tile = te;
        this.currentItem = te.getCraftItem();
        if (this.currentItem == null || this.currentItem.isEmpty()) {
            this.currentItem = new ItemStack(IRItems.ITEM_PLATE, 1);
        }

        ItemPlate.Data data = new ItemPlate.Data(this.currentItem);
        this.gauge = data.gauge;
    }

    @Override
    public void init(IScreenBuilder screen) {
        this.gaugeButton = new Button(screen, -100, -24, GuiText.SELECTOR_GAUGE.toString(this.gauge)) {
            @Override
            public void onClick(Player.Hand hand) {
                if (!PlateRollerGUI.this.currentItem.isEmpty()) {
                    EntityRollingStockDefinition def = new ItemPlate.Data(PlateRollerGUI.this.currentItem).def;
                    if (def != null && ConfigBalance.DesignGaugeLock) {
                        List<Gauge> validGauges = Collections.singletonList(Gauge.from(def.recommended_gauge.value()));
                        PlateRollerGUI.this.gauge = next(validGauges, PlateRollerGUI.this.gauge, hand);
                    } else {
                        PlateRollerGUI.this.gauge = next(Gauge.values(), PlateRollerGUI.this.gauge, hand);
                    }
                }
                PlateRollerGUI.this.gaugeButton.setText(GuiText.SELECTOR_GAUGE.toString(PlateRollerGUI.this.gauge));
                PlateRollerGUI.this.sendPacket();
            }
        };

        this.pickerButton = new Button(screen, -100, -24 + 2 * 30, "") {
            @Override
            public void onClick(Player.Hand hand) {
                CraftPicker.showCraftPicker(screen, null, CraftingType.PLATE_BOILER, (ItemStack item) -> {
                    if (item != null) {
                        if (item.is(IRItems.ITEM_ROLLING_STOCK)) {
                            ItemRollingStock.Data stock = new ItemRollingStock.Data(item);
                            item = new ItemStack(IRItems.ITEM_PLATE, 1);
                            ItemPlate.Data data = new ItemPlate.Data(item);
                            data.def = stock.def;
                            data.gauge = PlateRollerGUI.this.gauge;
                            data.type = PlateType.BOILER;
                            data.write();
                        }

                        ItemPlate.Data data = new ItemPlate.Data(item);
                        EntityRollingStockDefinition def = data.def;
                        if (def != null && !PlateRollerGUI.this.gauge.isModel() && PlateRollerGUI.this.gauge.value() != def.recommended_gauge.value()) {
                            PlateRollerGUI.this.gauge = def.recommended_gauge;
                            PlateRollerGUI.this.gaugeButton.setText(GuiText.SELECTOR_GAUGE.toString(PlateRollerGUI.this.gauge));
                        }
                        PlateRollerGUI.this.currentItem = item;
                        PlateRollerGUI.this.updatePickerButton();
                        PlateRollerGUI.this.sendPacket();
                    }
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

    @Override
    public void draw(IScreenBuilder builder) {

    }

    private void sendPacket() {
        ItemPlate.Data data = new ItemPlate.Data(this.currentItem);
        data.gauge = this.gauge;
        data.write();
        this.currentItem.setCount(Math.min(64, Math.max(1, (int) Math.floor(data.type.platesPerBlock() / this.gauge.scale()))));
        this.tile.setCraftItem(this.currentItem);
    }

    private void updatePickerButton() {
        this.pickerButton.setText(
                GuiText.SELECTOR_PLATE_TYPE.toString(this.currentItem != null ? this.currentItem.getDisplayName() : "")
        );
    }
}

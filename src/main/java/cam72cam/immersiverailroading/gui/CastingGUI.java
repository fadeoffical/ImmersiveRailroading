package cam72cam.immersiverailroading.gui;

import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.items.ItemRailAugment;
import cam72cam.immersiverailroading.items.ItemRollingStock;
import cam72cam.immersiverailroading.items.ItemRollingStockComponent;
import cam72cam.immersiverailroading.library.CraftingMachineMode;
import cam72cam.immersiverailroading.library.CraftingType;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.multiblock.CastingMultiblock;
import cam72cam.immersiverailroading.multiblock.CastingMultiblock.CastingInstance;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.immersiverailroading.util.ItemCastingCost;
import cam72cam.mod.entity.Player;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.gui.screen.Button;
import cam72cam.mod.gui.screen.IScreen;
import cam72cam.mod.gui.screen.IScreenBuilder;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.resource.Identifier;

import java.util.Collections;
import java.util.List;

import static cam72cam.immersiverailroading.gui.ClickListHelper.next;

public class CastingGUI implements IScreen {
    public static final Identifier CASTING_GUI_TEXTURE = new Identifier("immersiverailroading:gui/casting_gui.png");

    private Button gaugeButton;
    private Gauge gauge;

    private Button pickerButton;

    private Button singleCastButton;
    private Button repeatCastButton;

    private final TileMultiblock tile;
    private ItemStack currentItem;

    public CastingGUI(TileMultiblock te) {
        this.tile = te;
        this.currentItem = ((CastingInstance) te.getMultiblock()).getCraftItem();

        this.gauge = new ItemRollingStockComponent.Data(this.currentItem).gauge;
    }

    @Override
    public void init(IScreenBuilder screen) {
        this.pickerButton = new Button(screen, -100, -20 - 10, GuiText.SELECTOR_TYPE.toString("")) {
            @Override
            public void onClick(Player.Hand hand) {
                CraftPicker.showCraftPicker(screen, CastingGUI.this.currentItem, CraftingType.CASTING, (ItemStack item) -> {
                    if (item != null) {
                        CastingGUI.this.currentItem = item;
                        EntityRollingStockDefinition def =
                                CastingGUI.this.currentItem.is(IRItems.ITEM_ROLLING_STOCK_COMPONENT) ?
                                        new ItemRollingStockComponent.Data(CastingGUI.this.currentItem).def :
                                        new ItemRollingStock.Data(CastingGUI.this.currentItem).def;
                        if (def != null && !CastingGUI.this.gauge.isModelGauge() && CastingGUI.this.gauge.getRailDistance() != def.recommended_gauge.getRailDistance()) {
                            CastingGUI.this.gauge = def.recommended_gauge;
                            CastingGUI.this.gaugeButton.setText(GuiText.SELECTOR_GAUGE.toString(CastingGUI.this.gauge));
                        }
                        CastingGUI.this.updatePickerButton();
                        CastingGUI.this.sendItemPacket();
                    }
                });
            }
        };
        this.updatePickerButton();

        this.gaugeButton = new Button(screen, 0, -10, 100, 20, GuiText.SELECTOR_GAUGE.toString(this.gauge)) {
            @Override
            public void onClick(Player.Hand hand) {
                if (!CastingGUI.this.currentItem.isEmpty()) {
                    EntityRollingStockDefinition def = new ItemRollingStockComponent.Data(CastingGUI.this.currentItem).def;
                    if (def != null && ConfigBalance.DesignGaugeLock) {
                        List<Gauge> validGauges = Collections.singletonList(Gauge.getClosestGauge(def.recommended_gauge.getRailDistance()));
                        CastingGUI.this.gauge = next(validGauges, CastingGUI.this.gauge, hand);
                    } else {
                        CastingGUI.this.gauge = next(Gauge.getRegisteredGauges(), CastingGUI.this.gauge, hand);
                    }
                }
                CastingGUI.this.gaugeButton.setText(GuiText.SELECTOR_GAUGE.toString(CastingGUI.this.gauge));
                CastingGUI.this.sendItemPacket();
            }
        };
        this.singleCastButton = new Button(screen, 0, +20 - 10, 100, 20, GuiText.SELECTOR_CAST_SINGLE.toString()) {
            @Override
            public void onClick(Player.Hand hand) {
                if (CastingGUI.this.tile.getCraftMode() != CraftingMachineMode.SINGLE) {
                    CastingGUI.this.tile.setCraftMode(CraftingMachineMode.SINGLE);
                } else {
                    CastingGUI.this.tile.setCraftMode(CraftingMachineMode.STOPPED);
                }
            }

            @Override
            public void onUpdate() {
                CastingGUI.this.singleCastButton.setTextColor(CastingGUI.this.tile.getCraftMode() == CraftingMachineMode.SINGLE ? 0xcc4334 : 0);
            }
        };
        this.repeatCastButton = new Button(screen, 0, +40 - 10, 100, 20, GuiText.SELECTOR_CAST_REPEAT.toString()) {
            @Override
            public void onClick(Player.Hand hand) {
                if (CastingGUI.this.tile.getCraftMode() != CraftingMachineMode.REPEAT) {
                    CastingGUI.this.tile.setCraftMode(CraftingMachineMode.REPEAT);
                } else {
                    CastingGUI.this.tile.setCraftMode(CraftingMachineMode.STOPPED);
                }
            }

            @Override
            public void onUpdate() {
                CastingGUI.this.repeatCastButton.setTextColor(CastingGUI.this.tile.getCraftMode() == CraftingMachineMode.REPEAT ? 0xcc4334 : 0);
            }
        };
    }

    private void updatePickerButton() {
        if (this.currentItem.isEmpty()) {
            this.pickerButton.setText(GuiText.SELECTOR_TYPE.toString(""));
        } else {
            this.pickerButton.setText(this.currentItem.getDisplayName());
        }
    }

    private void sendItemPacket() {
        this.currentItem.setCount(1);

        if (this.currentItem.is(IRItems.ITEM_ROLLING_STOCK_COMPONENT) || this.currentItem.is(IRItems.ITEM_CAST_RAIL)) {
            ItemRollingStockComponent.Data data = new ItemRollingStockComponent.Data(this.currentItem);
            data.gauge = this.gauge;
            data.rawCast = (data.componentType.crafting == CraftingType.CASTING_HAMMER);
            data.write();
        } else if (this.currentItem.is(IRItems.ITEM_AUGMENT)) {
            ItemRailAugment.Data data = new ItemRailAugment.Data(this.currentItem);
            data.gauge = this.gauge;
            data.write();
        } else if (this.currentItem.is(IRItems.ITEM_ROLLING_STOCK)) {
            ItemRollingStock.Data data = new ItemRollingStock.Data(this.currentItem);
            data.gauge = this.gauge;
            data.write();
        } else {
            this.currentItem.clearTagCompound();
        }
        this.tile.setCraftItem(this.currentItem);
    }

    @Override
    public void onEnterKey(IScreenBuilder b) {
    }

    @Override
    public void onClose() {

    }

    @Override
    public void draw(IScreenBuilder builder) {
        double fluidPercent = ((CastingInstance) this.tile.getMultiblock()).getSteelLevel();
        int progress = this.tile.getCraftProgress();
        float cost;
        if (this.currentItem.is(IRItems.ITEM_ROLLING_STOCK)) {
            cost = IRItems.ITEM_ROLLING_STOCK.getCastableComponents(this.currentItem)
                    .stream()
                    .mapToInt(ItemCastingCost::getCastCost)
                    .sum();
        } else {
            cost = ItemCastingCost.getCastCost(this.currentItem);
            if (cost == ItemCastingCost.BAD_CAST_COST) {
                cost = 0;
            }
        }

        builder.drawImage(CASTING_GUI_TEXTURE, -100, 0, 200, 100);

        builder.drawTank(-95, 3, 57, 60, Fluid.LAVA, (float) fluidPercent, false, 0x99fb7e15);
        builder.drawTank(-29, 67, 126, 30, Fluid.LAVA, progress / cost, false, 0x998c1919);

        String fillStr = String.format("%s/%s", (int) (fluidPercent * CastingMultiblock.max_volume), (int) CastingMultiblock.max_volume);
        String castStr = String.format("%s/%s", progress, (int) cost);
        builder.drawCenteredString(fillStr, -94 + 27, 3 + 25, 14737632);
        builder.drawCenteredString(castStr, -28 + 60, 67 + 10, 14737632);
    }
}

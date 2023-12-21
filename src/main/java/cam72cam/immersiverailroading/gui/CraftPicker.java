package cam72cam.immersiverailroading.gui;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.items.ItemPlate;
import cam72cam.immersiverailroading.items.ItemRollingStock;
import cam72cam.immersiverailroading.items.ItemRollingStockComponent;
import cam72cam.immersiverailroading.items.ItemTabs;
import cam72cam.immersiverailroading.library.CraftingType;
import cam72cam.immersiverailroading.library.ItemComponentType;
import cam72cam.immersiverailroading.library.PlateType;
import cam72cam.immersiverailroading.util.IRFuzzy;
import cam72cam.mod.gui.helpers.ItemPickerGUI;
import cam72cam.mod.gui.screen.IScreenBuilder;
import cam72cam.mod.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CraftPicker {

    private final boolean enableItemPicker;
    private final ItemPickerGUI stockSelector;
    private final ItemPickerGUI itemSelector;
    private final List<ItemStack> items;
    private final Consumer<ItemStack> onChoose;

    private CraftPicker(IScreenBuilder screen, ItemStack current, CraftingType craftType, Consumer<ItemStack> onChoose) {
        this.enableItemPicker = craftType.isCasting();
        this.onChoose = stack -> {
            screen.show();
            onChoose.accept(stack);
        };
        this.items = new ArrayList<>(IRItems.ITEM_ROLLING_STOCK_COMPONENT.getItemVariants());

        List<ItemStack> stock = new ArrayList<>();

        stock.addAll(IRItems.ITEM_ROLLING_STOCK.getItemVariants(ItemTabs.LOCOMOTIVE_TAB));
        stock.addAll(IRItems.ITEM_ROLLING_STOCK.getItemVariants(ItemTabs.PASSENGER_TAB));
        stock.addAll(IRItems.ITEM_ROLLING_STOCK.getItemVariants(ItemTabs.STOCK_TAB));

        List<ItemStack> toRemove = new ArrayList<>();
        for (ItemStack item : this.items) {
            ItemRollingStockComponent.Data data = new ItemRollingStockComponent.Data(item);
            ItemComponentType comp = data.componentType;
            if (comp.isWooden(data.rollingStockDefinition)) {
                toRemove.add(item);
                continue;
            }
            boolean isCastable = craftType == CraftingType.CASTING && comp.crafting == CraftingType.CASTING_HAMMER;
            if (comp.crafting != craftType && !isCastable) {
                toRemove.add(item);
            }
        }
        this.items.removeAll(toRemove);


        this.stockSelector = new ItemPickerGUI(stock, this::onStockExit);
        toRemove = new ArrayList<>();
        for (ItemStack itemStock : stock) {
            boolean hasComponent = false;
            for (ItemStack item : this.items) {
                if (this.isPartOf(itemStock, item)) {
                    hasComponent = true;
                    break;
                }
            }
            if (!hasComponent) {
                toRemove.add(itemStock);
                continue;
            }
            if (this.isPartOf(itemStock, current)) {
                this.stockSelector.choosenItem = itemStock;
            }
        }
        stock.removeAll(toRemove);

        if (craftType.isCasting()) {
            stock.add(new ItemStack(IRItems.ITEM_CAST_RAIL, 1));
            stock.addAll(IRFuzzy.steelIngotOrFallback().enumerate());
            stock.addAll(IRFuzzy.steelBlockOrFallback().enumerate());
            stock.addAll(IRItems.ITEM_AUGMENT.getItemVariants(ItemTabs.MAIN_TAB));
        } else if (craftType.isPlate()) {
            for (PlateType value : PlateType.values()) {
                if (value == PlateType.BOILER) {
                    continue;
                }
                ItemStack item = new ItemStack(IRItems.ITEM_PLATE, 1);
                ItemPlate.ItemPlateData data = new ItemPlate.ItemPlateData(item);
                data.plateType = value;
                data.write();
                stock.add(item);
            }
        }
        this.stockSelector.setItems(stock);

        this.itemSelector = new ItemPickerGUI(new ArrayList<>(), this::onItemExit);
        if (current != null && (current.is(IRItems.ITEM_ROLLING_STOCK_COMPONENT) || current.is(IRItems.ITEM_ROLLING_STOCK))) {
            this.itemSelector.choosenItem = current;
        }

        // Draw/init
        if (this.stockSelector.choosenItem != null && this.enableItemPicker) {
            this.setupItemSelector();
            if (this.itemSelector.hasOptions()) {
                this.itemSelector.show();
                return;
            }
        }
        this.stockSelector.show();
    }

    public static void showCraftPicker(IScreenBuilder screen, ItemStack current, CraftingType craftType, Consumer<ItemStack> onChoose) {
        new CraftPicker(screen, current, craftType, onChoose);
    }

    private boolean isPartOf(ItemStack stock, ItemStack item) {
        if (stock == null || item == null) return false;
        if (!stock.is(IRItems.ITEM_ROLLING_STOCK)) return false;
        if (!item.is(IRItems.ITEM_ROLLING_STOCK_COMPONENT)) return false;
        return new ItemRollingStockComponent.Data(item).rollingStockDefinition == new ItemRollingStock.Data(stock).rollingStockDefinition;
    }

    private void setupItemSelector() {
        List<ItemStack> filteredItems = new ArrayList<>();
        filteredItems.add(this.stockSelector.choosenItem);
        for (ItemStack item : this.items) {
            if (this.isPartOf(this.stockSelector.choosenItem, item)) {
                filteredItems.add(item);
            }
        }
        this.itemSelector.setItems(filteredItems);
    }

    private void onStockExit(ItemStack stack) {
        if (stack == null) {
            this.onChoose.accept(null);
            return;
        }

        if (!this.enableItemPicker) {
            this.onChoose.accept(stack);
            return;
        }

        this.setupItemSelector();
        if (this.itemSelector.hasOptions()) {
            this.itemSelector.show();
        } else {
            this.itemSelector.choosenItem = null;
            this.onChoose.accept(stack);
        }
    }

    private void onItemExit(ItemStack stack) {
        if (stack == null) {
            this.stockSelector.show();
        } else {
            this.onChoose.accept(stack);
        }
    }
}

package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.ItemComponentType;
import cam72cam.immersiverailroading.library.PlateType;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.CustomItem;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.text.TextColor;
import trackapi.lib.Gauges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ItemPlate extends CustomItem {

    public ItemPlate() {
        super(ImmersiveRailroading.MOD_ID, "item_plate");
    }

    @Override
    public List<CreativeTab> getCreativeTabs() {
        return Collections.singletonList(ItemTabs.MAIN_TAB);
    }

    @Override
    public List<ItemStack> getItemVariants(CreativeTab tab) {
        if (tab != null && !tab.equals(ItemTabs.MAIN_TAB)) return Collections.emptyList();

        List<ItemStack> items = new ArrayList<>();
        for (PlateType plate : PlateType.values()) {

            ItemStack stack = new ItemStack(this, 1);
            if (plate != PlateType.BOILER) {
                ItemPlateData data = new ItemPlateData(stack);
                data.plateType = plate;
                data.gauge = Gauge.getClosestGauge(Gauges.STANDARD);
                data.write();
                items.add(stack);

                continue;
            }

            for (EntityRollingStockDefinition def : DefinitionManager.getDefinitions()) {
                if (!def.getItemComponents().contains(ItemComponentType.BOILER_SEGMENT)) continue;
                stack = stack.copy();
                ItemPlateData data = new ItemPlateData(stack);
                data.plateType = plate;
                data.gauge = Gauge.getClosestGauge(Gauges.STANDARD);
                data.rollingStockDefinition = def;
                data.write();
                items.add(stack);
            }
        }
        return items;
    }

    @Override
    public List<String> getTooltip(ItemStack stack) {
        return Collections.singletonList(GuiText.GAUGE_TOOLTIP.translate(new ItemPlateData(stack).gauge));
    }

    @Override
    public String getCustomName(ItemStack stack) {
        ItemPlateData data = new ItemPlateData(stack);
        if (data.plateType == PlateType.BOILER)
            return data.rollingStockDefinition != null ? TextColor.RESET.wrap(data.plateType + " " + data.rollingStockDefinition.name()) : null;
        return TextColor.RESET.wrap(data.plateType.toString());
    }

    public static class ItemPlateData extends ItemDataSerializer {

        @TagField("plate")
        public PlateType plateType;

        @TagField("defID")
        public EntityRollingStockDefinition rollingStockDefinition;

        @TagField("gauge")
        public Gauge gauge;

        public ItemPlateData(ItemStack stack) {
            super(stack);
            this.gauge = Gauge.getClosestGauge(Gauges.STANDARD);
            this.plateType = PlateType.SMALL;
        }
    }
}

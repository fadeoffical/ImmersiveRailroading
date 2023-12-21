package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.CustomItem;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.serialization.TagField;
import trackapi.lib.Gauges;

import java.util.Collections;
import java.util.List;

public class ItemCastRail extends CustomItem {

    public ItemCastRail() {
        super(ImmersiveRailroading.MODID, "item_cast_rail");
    }

    @Override
    public List<CreativeTab> getCreativeTabs() {
        return Collections.singletonList(ItemTabs.MAIN_TAB);
    }

    @Override
    public int getStackSize() {
        return 16;
    }

    @Override
    public List<String> getTooltip(ItemStack stack) {
        return Collections.singletonList(GuiText.GAUGE_TOOLTIP.translate(new Data(stack).gauge));
    }

    public static class Data extends ItemDataSerializer {
        @TagField("gauge")
        public Gauge gauge;

        public Data(ItemStack stack) {
            super(stack);
            this.gauge = Gauge.getClosestGauge(Gauges.STANDARD);
        }
    }
}

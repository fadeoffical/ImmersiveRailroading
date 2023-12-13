package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.CustomItem;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.text.TextUtil;

import java.util.Collections;
import java.util.List;

public class ItemRail extends CustomItem {
    public ItemRail() {
        super(ImmersiveRailroading.MODID, "item_rail_part");
    }

    @Override
    public List<CreativeTab> getCreativeTabs() {
        return Collections.singletonList(ItemTabs.MAIN_TAB);
    }

    @Override
    public int getStackSize() {
        return 64;
    }

    public String getCustomName(ItemStack stack) {
        return String.format("%s (%s)", TextUtil.translate("item.immersiverailroading:item_rail_part.name"), new Data(stack).gauge.toString());
    }

    public static class Data extends ItemDataSerializer {
        @TagField("gauge")
        public Gauge gauge;

        public Data(ItemStack stack) {
            super(stack);
            if (this.gauge == null) {
                this.gauge = Gauge.getClosestGauge(Gauge.STANDARD);
            }
        }
    }
}

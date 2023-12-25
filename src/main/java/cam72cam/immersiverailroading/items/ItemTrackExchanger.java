package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.*;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;
import trackapi.lib.Gauges;

import java.util.Collections;
import java.util.List;

public class ItemTrackExchanger extends CustomItem {
    public ItemTrackExchanger() {
        super(ImmersiveRailroading.MOD_ID, "item_track_exchanger");
        Fuzzy largeWrench = Fuzzy.get("item_large_wrench").add(new ItemStack(IRItems.ITEM_LARGE_WRENCH, 1));
        Fuzzy trackBlueprint = Fuzzy.get("item_rail").add(new ItemStack(IRItems.ITEM_TRACK_BLUEPRINT, 1));
        Recipes.shapedRecipe(this, 3,
                Fuzzy.GLASS_PANE, Fuzzy.GLASS_PANE, Fuzzy.GLASS_PANE,
                largeWrench, Fuzzy.IRON_INGOT, trackBlueprint,
                Fuzzy.GLASS_PANE, Fuzzy.REDSTONE_DUST, Fuzzy.GLASS_PANE);
    }

    @Override
    public List<CreativeTab> getCreativeTabs() {
        return Collections.singletonList(ItemTabs.MAIN_TAB);
    }

    @Override
    public int getStackSize() {
        return 1;
    }

    @Override
    public List<String> getTooltip(ItemStack stack) {
        return Collections.singletonList(GuiText.TRACK_SWITCHER_TOOLTIP.toString());
    }

    @Override
    public void onClickAir(Player player, World world, Player.Hand hand) {
        if (world.isClient && hand == Player.Hand.PRIMARY) {
            GuiTypes.TRACK_EXCHANGER.open(player);
        }
    }

    public static class Data extends ItemDataSerializer {
        @TagField("track")
        public String track;
        @TagField("railBed")
        public ItemStack railBed;
        @TagField("gauge")
        public Gauge gauge;

        public Data(ItemStack stack) {
            super(stack);
            if (this.track == null) {
                this.track = DefinitionManager.getTracks().get(0).name;
            }
            if (this.railBed == null) {
                this.railBed = ItemStack.EMPTY;
            }
            if (this.gauge == null) {
                this.gauge = Gauge.getClosestGauge(Gauges.STANDARD);
            }
        }
    }
}

package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.ItemStack;

public final class ItemTabs {

    public static final CreativeTab MAIN_TAB = new CreativeTab(ImmersiveRailroading.MOD_ID + ".main", () -> new ItemStack(IRItems.ITEM_LARGE_WRENCH, 1));
    public static CreativeTab LOCOMOTIVE_TAB;
    public static CreativeTab STOCK_TAB;
    public static CreativeTab PASSENGER_TAB;
    // public static CreativeTab COMPONENT_TAB;

    static {
        LOCOMOTIVE_TAB = new CreativeTab(ImmersiveRailroading.MOD_ID + ".locomotive", () -> getIcon(LOCOMOTIVE_TAB));
        STOCK_TAB = new CreativeTab(ImmersiveRailroading.MOD_ID + ".stock", () -> getIcon(STOCK_TAB));
        PASSENGER_TAB = new CreativeTab(ImmersiveRailroading.MOD_ID + ".passenger", () -> getIcon(PASSENGER_TAB));
        // COMPONENT_TAB = new CreativeTab(ImmersiveRailroading.MODID + ".components", () -> getIcon(COMPONENT_TAB));
    }

    private ItemTabs() {}

    private static ItemStack getIcon(CreativeTab tab) {
        return IRItems.ITEM_ROLLING_STOCK.getItemVariants(tab)
                .stream()
                .findFirst()
                .orElse(new ItemStack(IRItems.ITEM_LARGE_WRENCH, 1));
    }
}

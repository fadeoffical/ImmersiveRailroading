package cam72cam.immersiverailroading.inventory;

import cam72cam.mod.item.ItemStack;

import java.util.function.Function;

public interface SlotFilter extends Function<ItemStack, Boolean> {
    SlotFilter FLUID_CONTAINER = ItemStack::isFluidContainer;
    SlotFilter BURNABLE = ItemStack::isFlammable;
    SlotFilter NONE = ItemStack -> false;
    SlotFilter ANY = ItemStack -> true;
}

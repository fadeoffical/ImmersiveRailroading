package cam72cam.immersiverailroading.inventory;

import cam72cam.mod.item.ItemStack;
import cam72cam.mod.item.ItemStackHandler;
import cam72cam.mod.serialization.TagMapped;

import java.util.HashMap;
import java.util.Map;

@TagMapped(ItemStackHandler.TagMapper.class)
public class FilteredStackHandler extends ItemStackHandler {
    public Map<Integer, SlotFilter> filter = new HashMap<>();
    public SlotFilter defaultFilter = SlotFilter.ANY;

    private FilteredStackHandler() {
        // Serialization
        this(0);
    }

    public FilteredStackHandler(int i) {
        super(i);
        super.checkSlot = this::checkSlot;
    }

    private boolean checkSlot(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        SlotFilter chosen = this.defaultFilter;
        if (this.filter.containsKey(slot)) {
            chosen = this.filter.get(slot);
        }

        return chosen.apply(stack);
    }

}

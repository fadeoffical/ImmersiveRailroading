package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BurnUtil {

    public static int getBurnTime(ItemStack stack) {
        return stack.getBurnTime();
    }

    public static int getBurnTime(Fluid fluid) {
        if (ConfigBalance.dieselFuels.containsKey(fluid.ident)) {
            return ConfigBalance.dieselFuels.get(fluid.ident);
        }
        return 0;
    }

    public static List<Fluid> burnableFluids() {
        List<Fluid> values = new ArrayList<>();
        for (String name : ConfigBalance.dieselFuels.keySet()) {
            Fluid found = Fluid.getFluid(name);
            if (found != null) {
                values.add(found);
            }
        }
        return values;
    }
}

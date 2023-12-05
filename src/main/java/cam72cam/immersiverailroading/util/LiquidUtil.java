package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.Config;
import cam72cam.mod.fluid.Fluid;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class LiquidUtil {

    private LiquidUtil() {}

    public static List<Fluid> getWater() {
        return Arrays.stream(Config.ConfigBalance.waterTypes)
                .filter(fluid -> Fluid.getFluid(fluid) != null)
                .map(Fluid::getFluid)
                .collect(Collectors.toList());
    }
}

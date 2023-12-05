package cam72cam.immersiverailroading.util;

import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.mod.fluid.Fluid;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class BurnUtil {

    private BurnUtil() {}

    public static int getBurnTime(Fluid fluid) {
        return ConfigBalance.dieselFuels.getOrDefault(fluid.ident, 0);
    }

    public static List<Fluid> getBurnableFuels() {
        return ConfigBalance.dieselFuels.keySet()
                .stream()
                .map(Fluid::getFluid)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}

package cam72cam.immersiverailroading.gui.overlay;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.entity.*;
import cam72cam.mod.fluid.Fluid;

import java.util.Locale;

public enum Stat {
    SPEED,
    MAX_SPEED,
    UNITS_SPEED,
    LIQUID,
    MAX_LIQUID,
    UNITS_LIQUID,
    BOILER_PRESSURE,
    MAX_BOILER_PRESSURE,
    UNITS_BOILER_PRESSURE,
    TEMPERATURE,
    MAX_TEMPERATURE,
    UNITS_TEMPERATURE,
    BRAKE_PRESSURE,
    MAX_BRAKE_PRESSURE,
    UNITS_BRAKE_PRESSURE,
    ;

    public String getValue(EntityRollingStock stock) {
        Float temp = null;

        switch (this) {
            case SPEED:
                if (stock instanceof EntityMovableRollingStock) {
                    EntityMovableRollingStock movableRollingStock = (EntityMovableRollingStock) stock;
                    return movableRollingStock.getCurrentSpeed().as(ConfigGraphics.speedUnit).formatPrecise();
                }
                return "";
            case MAX_SPEED:
                if (stock instanceof Locomotive) {
                    Locomotive locomotive = (Locomotive) stock;
                    return locomotive.getDefinition().getMaxSpeed(stock.getGauge()).as(ConfigGraphics.speedUnit).format();
                }
                return "";
            case UNITS_SPEED:
                return ConfigGraphics.speedUnit.getDisplayString();
            case LIQUID:
                return stock instanceof FreightTank ?
                        String.format("%.1f",
                                ((FreightTank) stock).getLiquidAmount() / (float) Fluid.BUCKET_VOLUME)
                        : "";
            case MAX_LIQUID:
                return stock instanceof FreightTank ?
                        String.format("%.1f",
                                ((FreightTank) stock).getTankCapacity().asMillibuckets() / (float) Fluid.BUCKET_VOLUME)
                        : "";
            case UNITS_LIQUID:
                return "B";

            case BOILER_PRESSURE:
                return stock instanceof SteamLocomotive ?
                        String.format("%.1f", ConfigGraphics.pressureUnit.convertFromPSI(((SteamLocomotive) stock).getBoilerPressure())) : "";
            case MAX_BOILER_PRESSURE:
                return stock instanceof SteamLocomotive ?
                        String.format("%.1f", ConfigGraphics.pressureUnit.convertFromPSI((float) ((SteamLocomotive) stock).getDefinition()
                                .getMaxPSI(stock.getGauge())))
                        : "";
            case UNITS_BOILER_PRESSURE:
                return ConfigGraphics.pressureUnit.toUnitString();

            case TEMPERATURE:
                if (stock instanceof SteamLocomotive) {
                    temp = ((SteamLocomotive) stock).getBoilerTemperature();
                }
                if (stock instanceof LocomotiveDiesel) {
                    temp = ((LocomotiveDiesel) stock).getEngineTemperature();
                }
                return temp != null ? String.format("%.1f", ConfigGraphics.temperatureUnit.convertFromCelsius(temp)) : "";
            case MAX_TEMPERATURE:
                if (stock instanceof SteamLocomotive) {
                    temp = 100f;
                }
                if (stock instanceof LocomotiveDiesel) {
                    temp = 150f;
                }
                return temp != null ? String.format("%.1f", ConfigGraphics.temperatureUnit.convertFromCelsius(temp)) : "";
            case UNITS_TEMPERATURE:
                return ConfigGraphics.temperatureUnit.getUnitDisplayString();
            case BRAKE_PRESSURE:
                if (stock instanceof EntityMovableRollingStock) {
                    return String.format("%s", (int) (((EntityMovableRollingStock) stock).getBrakePressure() * 100));
                }
                return "";
            case MAX_BRAKE_PRESSURE:
                return "100";
            case UNITS_BRAKE_PRESSURE:
                return "%";
        }
        return "";
    }

    @Override
    public String toString() {
        return "stat." + this.name().toLowerCase(Locale.ROOT);
    }
}

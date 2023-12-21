package cam72cam.immersiverailroading.library.unit;

import java.util.function.Function;

public enum TemperatureUnit {

    // todo: insert degree symbol for Celsius and Fahrenheit (&deg;C, &deg;F)
    //       gradle complains about us-ascii... why are we even using this?

    CELSIUS("C"),
    FAHRENHEIT("F", celsius -> (celsius * 9f / 5f) + 32f),
    KELVIN("K", celsius -> celsius + 270f);

    private final String unit;
    private final Function<Float, Float> conversionFunction;

    TemperatureUnit(String unit, Function<Float, Float> conversionFunction) {
        this.unit = unit;
        this.conversionFunction = conversionFunction;
    }

    TemperatureUnit(String unit) {
        this(unit, celsius -> celsius);
    }

    public float convertFromCelsius(float temperatureCelsius) {
        return this.conversionFunction.apply(temperatureCelsius);
    }

    public String getUnitDisplayString() {
        return this.unit;
    }
}

package cam72cam.immersiverailroading.model;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.gui.overlay.Readouts;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.model.part.PartSound;
import cam72cam.immersiverailroading.model.part.PressureValve;
import cam72cam.immersiverailroading.model.part.SteamChimney;
import cam72cam.immersiverailroading.model.part.Whistle;
import cam72cam.immersiverailroading.registry.LocomotiveSteamDefinition;

import java.util.List;

public class SteamLocomotiveModel extends LocomotiveModel<LocomotiveSteam, LocomotiveSteamDefinition> {
    private final PartSound idleSounds;
    private List<ModelComponent> components;
    private Whistle whistle;
    private SteamChimney chimney;
    private PressureValve pressureValve;
    private ModelComponent firebox;

    public SteamLocomotiveModel(LocomotiveSteamDefinition def) throws Exception {
        super(def);
        this.idleSounds = new PartSound(def.idle, true, 40, ConfigSound.SoundCategories.Locomotive.Steam::idle);
    }

    @Override
    protected void parseControllable(ComponentProvider provider, LocomotiveSteamDefinition def) {
        super.parseControllable(provider, def);
        if (!def.isCabCar()) {
            this.addGauge(provider, ModelComponentType.GAUGE_TEMPERATURE_X, Readouts.TEMPERATURE);
            this.addGauge(provider, ModelComponentType.GAUGE_BOILER_PRESSURE_X, Readouts.BOILER_PRESSURE);
        }

        this.addControl(provider, ModelComponentType.WHISTLE_CONTROL_X);
        this.addControl(provider, ModelComponentType.CYLINDER_DRAIN_CONTROL_X);
    }

    @Override
    protected void effects(LocomotiveSteam stock) {
        super.effects(stock);

        if (this.drivingWheels != null) {
            this.drivingWheels.effects(stock);
        }
        if (this.drivingWheelsFront != null) {
            this.drivingWheelsFront.effects(stock);
        }
        if (this.drivingWheelsRear != null) {
            this.drivingWheelsRear.effects(stock);
        }
        if (this.chimney != null) {
            boolean isEndStroke = (this.drivingWheels != null && this.drivingWheels.isEndStroke(stock)) ||
                    (this.drivingWheelsFront != null && this.drivingWheelsFront.isEndStroke(stock)) ||
                    (this.drivingWheelsRear != null && this.drivingWheelsRear.isEndStroke(stock));
            this.chimney.effects(stock, isEndStroke);
        }
        this.pressureValve.effects(stock, stock.isOverpressure() && Config.isFuelRequired(stock.gauge));
        this.idleSounds.effects(stock, stock.getBoilerTemperature() > stock.ambientTemperature() + 5 ? 0.1f : 0);
        this.whistle.effects(stock, stock.getBoilerPressure() > 0 || !Config.isFuelRequired(stock.gauge) ? stock.getHornTime() : 0, stock.getHornPull());
    }

    @Override
    protected void removed(LocomotiveSteam stock) {
        super.removed(stock);

        this.pressureValve.removed(stock);
        this.idleSounds.removed(stock);
        this.whistle.removed(stock);
        if (this.drivingWheels != null) {
            this.drivingWheels.removed(stock);
        }
        if (this.drivingWheelsFront != null) {
            this.drivingWheelsFront.removed(stock);
        }
        if (this.drivingWheelsRear != null) {
            this.drivingWheelsRear.removed(stock);
        }
    }

    @Override
    protected void parseComponents(ComponentProvider provider, LocomotiveSteamDefinition def) {
        this.firebox = provider.parse(ModelComponentType.FIREBOX);
        this.rocking.push(builder -> {
            builder.add((ModelState.Lighter) stock -> {
                return new ModelState.LightState(null, null, !Config.isFuelRequired(stock.gauge) || ((LocomotiveSteam) stock).getBurnTime()
                        .values()
                        .stream()
                        .anyMatch(x -> x > 1), null);
            });
        }).include(this.firebox);

        this.components = provider.parse(
                ModelComponentType.SMOKEBOX,
                ModelComponentType.PIPING
        );

        this.components.addAll(provider.parseAll(
                ModelComponentType.BOILER_SEGMENT_X
        ));
        this.rocking.include(this.components);

        this.whistle = Whistle.get(provider, this.rocking, def.quill, def.whistle);

        this.chimney = SteamChimney.get(provider);
        this.pressureValve = PressureValve.get(provider, def.pressure);

        super.parseComponents(provider, def);
    }

    @Override
    protected boolean unifiedBogies() {
        return false;
    }
}

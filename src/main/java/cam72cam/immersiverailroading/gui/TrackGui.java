package cam72cam.immersiverailroading.gui;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.gui.component.ListSelector;
import cam72cam.immersiverailroading.items.nbt.RailSettings;
import cam72cam.immersiverailroading.library.*;
import cam72cam.immersiverailroading.net.ItemRailUpdatePacket;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.TrackDefinition;
import cam72cam.immersiverailroading.render.rail.RailRender;
import cam72cam.immersiverailroading.tile.TileRailPreview;
import cam72cam.immersiverailroading.track.BuilderTurnTable;
import cam72cam.immersiverailroading.track.TrackBase;
import cam72cam.immersiverailroading.util.IRFuzzy;
import cam72cam.immersiverailroading.util.PlacementInfo;
import cam72cam.immersiverailroading.util.RailInfo;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Player;
import cam72cam.mod.gui.helpers.GUIHelpers;
import cam72cam.mod.gui.screen.*;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.render.StandardModel;
import cam72cam.mod.render.opengl.RenderState;
import util.Matrix4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static cam72cam.immersiverailroading.gui.ClickListHelper.next;
import static cam72cam.immersiverailroading.gui.component.GuiUtils.fitString;

public class TrackGui implements IScreen {
    private final List<ItemStack> oreDict;
    long frame;
    private TileRailPreview te;
    private Button typeButton;
    private TextField lengthInput;
    private Slider degreesSlider;
    private Slider curvositySlider;
    private CheckBox isPreviewCB;
    private CheckBox isGradeCrossingCB;
    private Button gaugeButton;
    private Button trackButton;
    private Button posTypeButton;
    private Button smoothingButton;
    private Button directionButton;
    private Button bedTypeButton;
    private Button bedFillButton;
    private final RailSettings.Mutable settings;

    private ListSelector<Gauge> gaugeSelector;
    private ListSelector<TrackItems> typeSelector;
    private ListSelector<TrackDefinition> trackSelector;
    private ListSelector<ItemStack> railBedSelector;
    private ListSelector<ItemStack> railBedFillSelector;

    private double zoom = 1;

    public TrackGui() {
        this(MinecraftClient.getPlayer().getHeldItem(Player.Hand.PRIMARY));
    }

    private TrackGui(ItemStack stack) {
        stack = stack.copy();
        this.settings = RailSettings.from(stack).mutable();
        this.oreDict = new ArrayList<>();
        this.oreDict.add(ItemStack.EMPTY);
        this.oreDict.addAll(IRFuzzy.IR_RAIL_BED.enumerate());
    }

    public TrackGui(TileRailPreview te) {
        this(te.getItem());
        this.te = te;
    }

    public void init(IScreenBuilder screen) {

        // Left pane
        int width = 200;
        int height = 20;
        int xtop = -GUIHelpers.getScreenWidth() / 2;
        int ytop = -GUIHelpers.getScreenHeight() / 4;

        this.lengthInput = new TextField(screen, xtop, ytop, width - 1, height);
        this.lengthInput.setText("" + this.settings.getLength());
        this.lengthInput.setValidator(s -> {
            if (s == null || s.length() == 0) {
                return true;
            }
            int val;
            try {
                val = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return false;
            }
            int max = 1000;
            if (this.settings.getType() == TrackItems.TURNTABLE) {
                max = BuilderTurnTable.maxLength(this.settings.getGauge());
            }
            if (val > 0 && val <= max) {
                this.settings.setLength(val);
                return true;
            }
            return false;
        });
        this.lengthInput.setFocused(true);
        ytop += height;

        this.gaugeSelector = new ListSelector<Gauge>(screen, width, 100, height, this.settings.getGauge(),
                Gauge.getRegisteredGauges()
                        .stream()
                        .collect(Collectors.toMap(Gauge::toString, g -> g, (u, v) -> u, LinkedHashMap::new))
        ) {
            @Override
            public void onClick(Gauge gauge) {
                TrackGui.this.settings.setGauge(gauge);
                TrackGui.this.gaugeButton.setText(GuiText.SELECTOR_GAUGE.translate(TrackGui.this.settings.getGauge()));
                if (TrackGui.this.settings.getType() == TrackItems.TURNTABLE) {
                    TrackGui.this.lengthInput.setText("" + Math.min(Integer.parseInt(TrackGui.this.lengthInput.getText()), BuilderTurnTable.maxLength(TrackGui.this.settings.getGauge()))); // revalidate
                }
            }
        };
        this.gaugeButton = new Button(screen, xtop, ytop, width, height, GuiText.SELECTOR_GAUGE.translate(this.settings.getGauge())) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.showSelector(TrackGui.this.gaugeSelector);
            }
        };
        ytop += height;

        this.typeSelector = new ListSelector<TrackItems>(screen, width, 100, height, this.settings.getType(),
                Arrays.stream(TrackItems.values())
                        .filter(i -> i != TrackItems.CROSSING)
                        .collect(Collectors.toMap(TrackItems::toString, g -> g, (u, v) -> u, LinkedHashMap::new))
        ) {
            @Override
            public void onClick(TrackItems option) {
                TrackGui.this.settings.setType(option);
                TrackGui.this.typeButton.setText(GuiText.SELECTOR_TYPE.translate(TrackGui.this.settings.getType()));
                TrackGui.this.degreesSlider.setVisible(TrackGui.this.settings.getType().hasQuarters());
                TrackGui.this.curvositySlider.setVisible(TrackGui.this.settings.getType().hasCurvosity());
                TrackGui.this.smoothingButton.setVisible(TrackGui.this.settings.getType().hasSmoothing());
                TrackGui.this.directionButton.setVisible(TrackGui.this.settings.getType().hasDirection());
                if (TrackGui.this.settings.getType() == TrackItems.TURNTABLE) {
                    TrackGui.this.lengthInput.setText("" + Math.min(Integer.parseInt(TrackGui.this.lengthInput.getText()), BuilderTurnTable.maxLength(TrackGui.this.settings.getGauge()))); // revalidate
                }
            }
        };
        this.typeButton = new Button(screen, xtop, ytop, width, height, GuiText.SELECTOR_TYPE.translate(this.settings.getType())) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.showSelector(TrackGui.this.typeSelector);
            }
        };
        ytop += height;

        this.smoothingButton = new Button(screen, xtop, ytop, width, height, GuiText.SELECTOR_SMOOTHING.translate(this.settings.getSmoothing())) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.settings.setSmoothing(next(TrackGui.this.settings.getSmoothing(), hand));
                TrackGui.this.smoothingButton.setText(GuiText.SELECTOR_SMOOTHING.translate(TrackGui.this.settings.getSmoothing()));
            }
        };
        this.smoothingButton.setVisible(this.settings.getType().hasSmoothing());
        ytop += height;

        this.directionButton = new Button(screen, xtop, ytop, width, height, GuiText.SELECTOR_DIRECTION.translate(this.settings.getDirection())) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.settings.setDirection(next(TrackGui.this.settings.getDirection(), hand));
                TrackGui.this.directionButton.setText(GuiText.SELECTOR_DIRECTION.translate(TrackGui.this.settings.getDirection()));
            }
        };
        this.directionButton.setVisible(this.settings.getType().hasDirection());
        ytop += height;


        this.degreesSlider = new Slider(screen, 25 + xtop, ytop, "", 1, Config.ConfigBalance.AnglePlacementSegmentation, this.settings.getDegrees() / 90 * Config.ConfigBalance.AnglePlacementSegmentation, false) {
            @Override
            public void onSlider() {
                TrackGui.this.settings.setDegrees(TrackGui.this.degreesSlider.getValueInt() * (90F / Config.ConfigBalance.AnglePlacementSegmentation));
                TrackGui.this.degreesSlider.setText(GuiText.SELECTOR_QUARTERS.translate(this.getValueInt() * (90.0 / Config.ConfigBalance.AnglePlacementSegmentation)));
            }
        };
        this.degreesSlider.onSlider();
        ytop += height;


        this.curvositySlider = new Slider(screen, 25 + xtop, ytop, "", 0.25, 1.5, this.settings.getCurvosity(), true) {
            @Override
            public void onSlider() {
                TrackGui.this.settings.setCurvosity((float) this.getValue());
                TrackGui.this.curvositySlider.setText(GuiText.SELECTOR_CURVOSITY.translate(String.format("%.2f", TrackGui.this.settings.getCurvosity())));
            }
        };
        this.curvositySlider.onSlider();
        ytop += height;

        this.directionButton.setVisible(this.settings.getType().hasDirection());
        this.degreesSlider.setVisible(this.settings.getType().hasQuarters());
        this.curvositySlider.setVisible(this.settings.getType().hasCurvosity());
        this.smoothingButton.setVisible(this.settings.getType().hasSmoothing());


        // Bottom Pane
        //width = 200;
        //height = 20;
        //xtop = GUIHelpers.getScreenWidth() / 2 - width;
        //ytop = -GUIHelpers.getScreenHeight() / 4;
        ytop = (int) (GUIHelpers.getScreenHeight() * 0.75 - height * 6);

        this.trackSelector = new ListSelector<TrackDefinition>(screen, width, 250, height,
                DefinitionManager.getTrack(this.settings.getTrack()),
                DefinitionManager.getTracks()
                        .stream()
                        .collect(Collectors.toMap(t -> t.name, g -> g, (u, v) -> u, LinkedHashMap::new))) {
            @Override
            public void onClick(TrackDefinition track) {
                TrackGui.this.settings.setTrack(track.trackID);
                TrackGui.this.trackButton.setText(GuiText.SELECTOR_TRACK.translate(fitString(DefinitionManager.getTrack(TrackGui.this.settings.getTrack()).name, 24)));
            }
        };
        this.trackButton = new Button(screen, xtop, ytop, width, height, GuiText.SELECTOR_TRACK.translate(fitString(DefinitionManager.getTrack(this.settings.getTrack()).name, 24))) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.showSelector(TrackGui.this.trackSelector);
            }
        };
        ytop += height;

        this.railBedSelector = new ListSelector<ItemStack>(screen, width, 250, height, this.settings.getRailBed(),
                this.oreDict.stream()
                        .collect(Collectors.toMap(TrackGui::getStackName, g -> g, (u, v) -> u, LinkedHashMap::new))
        ) {
            @Override
            public void onClick(ItemStack option) {
                TrackGui.this.settings.setRailBed(option);
                TrackGui.this.bedTypeButton.setText(GuiText.SELECTOR_RAIL_BED.translate(getStackName(TrackGui.this.settings.getRailBed())));
            }
        };
        this.bedTypeButton = new Button(screen, xtop, ytop, width, height, GuiText.SELECTOR_RAIL_BED.translate(getStackName(this.settings.getRailBed()))) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.showSelector(TrackGui.this.railBedSelector);
            }
        };
        ytop += height;

        this.railBedFillSelector = new ListSelector<ItemStack>(screen, width, 250, height, this.settings.getRailBedFill(),
                this.oreDict.stream()
                        .collect(Collectors.toMap(TrackGui::getStackName, g -> g, (u, v) -> u, LinkedHashMap::new))
        ) {
            @Override
            public void onClick(ItemStack option) {
                TrackGui.this.settings.setRailBedFill(option);
                TrackGui.this.bedFillButton.setText(GuiText.SELECTOR_RAIL_BED_FILL.translate(getStackName(TrackGui.this.settings.getRailBedFill())));
            }
        };
        this.bedFillButton = new Button(screen, xtop, ytop, width, height, GuiText.SELECTOR_RAIL_BED_FILL.translate(getStackName(this.settings.getRailBedFill()))) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.showSelector(TrackGui.this.railBedFillSelector);
            }
        };
        ytop += height;

        this.posTypeButton = new Button(screen, xtop, ytop, width, height, GuiText.SELECTOR_POSITION.translate(this.settings.getPosType())) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.settings.setPosType(next(TrackGui.this.settings.getPosType(), hand));
                TrackGui.this.posTypeButton.setText(GuiText.SELECTOR_POSITION.translate(TrackGui.this.settings.getPosType()));
            }
        };
        ytop += height;

        this.isPreviewCB = new CheckBox(screen, xtop + 2, ytop + 2, GuiText.SELECTOR_PLACE_BLUEPRINT.toString(), this.settings.isPreview()) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.settings.setPreview(TrackGui.this.isPreviewCB.isChecked());
            }
        };
        ytop += height;

        this.isGradeCrossingCB = new CheckBox(screen, xtop + 2, ytop + 2, GuiText.SELECTOR_GRADE_CROSSING.toString(), this.settings.isGradeCrossing()) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackGui.this.settings.setGradeCrossing(TrackGui.this.isGradeCrossingCB.isChecked());
            }
        };
        ytop += height;

        Slider zoom_slider = new Slider(screen, GUIHelpers.getScreenWidth() / 2 - 150, (int) (GUIHelpers.getScreenHeight() * 0.75 - height), "Zoom: ", 0.1, 2, 1, true) {
            @Override
            public void onSlider() {
                TrackGui.this.zoom = this.getValue();
            }
        };
    }

    private void showSelector(ListSelector<?> selector) {
        boolean isVisible = selector.isVisible();

        this.gaugeSelector.setVisible(false);
        this.typeSelector.setVisible(false);
        this.trackSelector.setVisible(false);
        this.railBedSelector.setVisible(false);
        this.railBedFillSelector.setVisible(false);

        selector.setVisible(!isVisible);
    }

    public static String getStackName(ItemStack stack) {
        if (stack.isEmpty()) {
            return GuiText.NONE.toString();
        }
        return stack.getDisplayName();
    }

    @Override
    public void onEnterKey(IScreenBuilder builder) {
        builder.close();
    }

    @Override
    public void onClose() {
        if (!this.lengthInput.getText().isEmpty()) {
            if (this.te != null) {
                new ItemRailUpdatePacket(this.te.getPos(), this.settings.immutable()).sendToServer();
            } else {
                new ItemRailUpdatePacket(this.settings.immutable()).sendToServer();
            }
        }
    }

    @Override
    public void draw(IScreenBuilder builder, RenderState state) {
        this.frame++;

        GUIHelpers.drawRect(200, 0, GUIHelpers.getScreenWidth() - 200, GUIHelpers.getScreenHeight(), 0xCC000000);
        GUIHelpers.drawRect(0, 0, 200, GUIHelpers.getScreenHeight(), 0xEE000000);

        if (this.gaugeSelector.isVisible()) {
            double textScale = 1.5;
            GUIHelpers.drawCenteredString(GuiText.SELECTOR_GAUGE.translate(this.settings.getGauge().toString()), (int) ((300 + (GUIHelpers.getScreenWidth() - 300) / 2) / textScale), (int) (10 / textScale), 0xFFFFFF, new Matrix4().scale(textScale, textScale, textScale));

            RailInfo info = new RailInfo(
                    this.settings.immutable().with(rendered -> {
                        rendered.setLength(5);
                        rendered.setType(TrackItems.STRAIGHT);
                    }),
                    new PlacementInfo(new Vec3d(0.5, 0, 0.5), TrackDirection.NONE, 0, null),
                    null, SwitchState.NONE, SwitchState.NONE, 0, true);

            double scale = GUIHelpers.getScreenWidth() / 12.0 * this.zoom;

            state.translate(300 + (GUIHelpers.getScreenWidth() - 300) / 2, builder.getHeight(), 100);
            state.rotate(90, 1, 0, 0);
            state.scale(-scale, scale, scale);
            state.translate(0, 0, 1);
            RailRender.get(info).renderRailModel(state);
            state.translate(-0.5, 0, -0.5);
            RailRender.get(info).renderRailBase(state);
            return;
        }

        if (this.trackSelector.isVisible() || this.railBedSelector.isVisible() || this.railBedFillSelector.isVisible()) {
            ListSelector.ButtonRenderer<ItemStack> icons = (button, x, y, value) -> {
                Matrix4 zMatrix = new Matrix4();
                zMatrix.translate(0, 0, 100);

                GUIHelpers.drawItem(value, x + 2, y + 2, zMatrix);
            };

            this.railBedSelector.render(icons);
            this.railBedFillSelector.render(icons);


            double textScale = 1.5;
            String str = this.trackSelector.isVisible() ? GuiText.SELECTOR_TRACK.translate(DefinitionManager.getTrack(this.settings.getTrack()).name) :
                    this.railBedSelector.isVisible() ? GuiText.SELECTOR_RAIL_BED.translate(getStackName(this.settings.getRailBed())) :
                            GuiText.SELECTOR_RAIL_BED_FILL.translate(getStackName(this.settings.getRailBedFill()));

            GUIHelpers.drawCenteredString(str, (int) ((450 + (GUIHelpers.getScreenWidth() - 450) / 2) / textScale), (int) (10 / textScale), 0xFFFFFF, new Matrix4().scale(textScale, textScale, textScale));

            RailInfo info = new RailInfo(
                    this.settings.immutable().with(rendered -> {
                        rendered.setLength(3);
                        rendered.setType(TrackItems.STRAIGHT);
                    }),
                    new PlacementInfo(new Vec3d(0.5, 0, 0.5), TrackDirection.NONE, 0, null),
                    null, SwitchState.NONE, SwitchState.NONE, 0, true);

            double scale = GUIHelpers.getScreenWidth() / 15.0 * this.zoom;

            state.translate(450 + (GUIHelpers.getScreenWidth() - 450) / 2, builder.getHeight() / 2, 500);
            state.rotate(90, 1, 0, 0);
            state.scale(-scale, scale, scale);
            state.translate(0, 0, -1);
            //state.rotate(60, 1, -1, -0.6);
            state.rotate(60, 1, 0, 0);

            state.translate(0, 0, 1);
            state.rotate(this.frame / 2.0, 0, 1, 0);
            state.translate(0, 0, -1);

            RailRender.get(info).renderRailModel(state);
            state.translate(-0.5, 0, -0.5);
            RailRender.get(info).renderRailBase(state);

            if (!info.settings.railBedFill.isEmpty()) {
                StandardModel model = new StandardModel();
                for (TrackBase base : info.getBuilder(MinecraftClient.getPlayer().getWorld()).getTracksForRender()) {
                    Vec3i basePos = base.getPos();
                    model.addItemBlock(info.settings.railBedFill, new Matrix4()
                            .translate(basePos.x, basePos.y - 1, basePos.z)
                    );
                }
                model.render(state);
            }

            return;
        }

        if (this.lengthInput.getText().isEmpty()) {
            return;
        }

        // This could be more efficient...
        RailInfo info = new RailInfo(
                this.settings.immutable().with(b -> {
                    int length = b.getLength();
                    if (length < 5) {
                        length = 5;
                    }
                    if (this.settings.getType() == TrackItems.TURNTABLE) {
                        length = Math.min(25, Math.max(10, length));
                    }
                    b.setLength(length);
                }),
                new PlacementInfo(new Vec3d(0.5, 0, 0.5), this.settings.getDirection(), 0, null),
                null, SwitchState.NONE, SwitchState.NONE, this.settings.getType() == TrackItems.TURNTABLE ? (this.frame / 2.0) % 360 : 0, true);

        int length = info.settings.length;
        double scale = (GUIHelpers.getScreenWidth() / (length * 2.25)) * this.zoom;
        if (this.settings.getType() == TrackItems.TURNTABLE) {
            scale /= 2;
        }

        state.translate(200 + (GUIHelpers.getScreenWidth() - 200) / 2, builder.getHeight() - 30, 100);
        state.rotate(90, 1, 0, 0);
        state.scale(-scale, scale, scale);
        state.translate(0, 0, 1);
        if (this.settings.getType().hasDirection()) {
            switch (this.settings.getDirection()) {
                case LEFT:
                    state.translate(length / 2.0, 0, 0);
                    break;
                case NONE:
                case RIGHT:
                    state.translate(-length / 2.0, 0, 0);
                    break;
            }
        }
        if (this.settings.getType() == TrackItems.CUSTOM) {
            state.translate(-length / 2.0, 0, 0);
        }
        RailRender.get(info).renderRailModel(state);
        state.translate(-0.5, 0, -0.5);
        RailRender.get(info).renderRailBase(state);
        if (!info.settings.railBedFill.isEmpty()) {
            StandardModel model = new StandardModel();
            for (TrackBase base : info.getBuilder(MinecraftClient.getPlayer().getWorld()).getTracksForRender()) {
                Vec3i basePos = base.getPos();
                model.addItemBlock(info.settings.railBedFill, new Matrix4()
                        .translate(basePos.x, basePos.y - 1, basePos.z)
                );
            }
            model.render(state);
        }
    }
}

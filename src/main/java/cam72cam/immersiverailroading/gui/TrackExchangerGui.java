package cam72cam.immersiverailroading.gui;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.items.ItemTrackExchanger;
import cam72cam.immersiverailroading.items.nbt.RailSettings;
import cam72cam.immersiverailroading.library.*;
import cam72cam.immersiverailroading.net.ItemTrackExchangerUpdatePacket;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.util.IRFuzzy;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Player;
import cam72cam.mod.gui.helpers.GUIHelpers;
import cam72cam.mod.gui.helpers.ItemPickerGUI;
import cam72cam.mod.gui.screen.Button;
import cam72cam.mod.gui.screen.IScreen;
import cam72cam.mod.gui.screen.IScreenBuilder;
import cam72cam.mod.item.ItemStack;
import util.Matrix4;

import java.util.ArrayList;
import java.util.List;

import static cam72cam.immersiverailroading.gui.ClickListHelper.next;
import static cam72cam.immersiverailroading.gui.TrackGui.getStackName;

public class TrackExchangerGui implements IScreen {
    List<ItemStack> oreDict;
    private Button trackSelector;
    private Button bedTypeButton;
    private Button gaugeButton;
    private String track;
    private ItemStack railBed;
    private Gauge gauge;

    public TrackExchangerGui() {
        Player player = MinecraftClient.getPlayer();

        ItemTrackExchanger.Data data = new ItemTrackExchanger.Data(player.getHeldItem(Player.Hand.PRIMARY));
        this.track = data.track;
        this.railBed = data.railBed;
        this.gauge = data.gauge;

        this.oreDict = new ArrayList<>();
        this.oreDict.add(ItemStack.EMPTY);
        this.oreDict.addAll(IRFuzzy.IR_RAIL_BED.enumerate());
    }

    @Override
    public void init(IScreenBuilder screen) {
        this.trackSelector = new Button(screen, -100, 22, GuiText.SELECTOR_TRACK.toString(DefinitionManager.getTrack(this.track).name)) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackExchangerGui.this.track = next(DefinitionManager.getTrackIDs(), TrackExchangerGui.this.track, hand);
                TrackExchangerGui.this.trackSelector.setText(GuiText.SELECTOR_TRACK.toString(DefinitionManager.getTrack(TrackExchangerGui.this.track).name));
            }
        };
        this.bedTypeButton = new Button(screen, -100, 2 * 22, GuiText.SELECTOR_RAIL_BED.toString(getStackName(this.railBed))) {
            @Override
            public void onClick(Player.Hand hand) {
                ItemPickerGUI ip = new ItemPickerGUI(TrackExchangerGui.this.oreDict, (ItemStack bed) -> {
                    if (bed != null) {
                        TrackExchangerGui.this.railBed = bed;
                        TrackExchangerGui.this.bedTypeButton.setText(GuiText.SELECTOR_RAIL_BED.toString(getStackName(bed)));
                    }
                    screen.show();
                });
                ip.choosenItem = TrackExchangerGui.this.railBed;
                ip.show();
            }
        };
        this.gaugeButton = new Button(screen, -100, 3 * 22, GuiText.SELECTOR_GAUGE.toString(this.gauge)) {
            @Override
            public void onClick(Player.Hand hand) {
                TrackExchangerGui.this.gauge = next(Gauge.values(), TrackExchangerGui.this.gauge, hand);
                TrackExchangerGui.this.gaugeButton.setText(GuiText.SELECTOR_GAUGE.toString(TrackExchangerGui.this.gauge));
            }
        };
    }

    @Override
    public void onEnterKey(IScreenBuilder builder) {
        builder.close();
    }

    @Override
    public void onClose() {
        new ItemTrackExchangerUpdatePacket(this.track, this.railBed, this.gauge).sendToServer();
    }

    @Override
    public void draw(IScreenBuilder builder) {
        int scale = 8;
        // This could be more efficient...
        RailSettings settings = new RailSettings(this.gauge,
                this.track,
                TrackItems.STRAIGHT,
                10,
                0,
                1, TrackPositionType.FIXED,
                TrackSmoothing.BOTH,
                TrackDirection.NONE,
                this.railBed,
                ItemStack.EMPTY,
                false,
                false
        );
        ItemStack stack = new ItemStack(IRItems.ITEM_TRACK_BLUEPRINT, 1);
        settings.write(stack);

        Matrix4 matrix = new Matrix4();
        matrix.translate(GUIHelpers.getScreenWidth() / 2 + builder.getWidth() / 4, builder.getHeight() / 4, 0);
        matrix.scale(scale, scale, 1);
        GUIHelpers.drawItem(stack, 0, 0, matrix);

        matrix.setIdentity();
        matrix.translate(GUIHelpers.getScreenWidth() / 2 - builder.getWidth() / 4, builder.getHeight() / 4, 0);
        matrix.scale(-scale, scale, 1);
        GUIHelpers.drawItem(stack, 0, 0, matrix);
    }
}

package cam72cam.immersiverailroading.library;

import cam72cam.immersiverailroading.*;
import cam72cam.immersiverailroading.entity.*;
import cam72cam.immersiverailroading.gui.*;
import cam72cam.immersiverailroading.gui.container.*;
import cam72cam.immersiverailroading.multiblock.CastingMultiBlock;
import cam72cam.immersiverailroading.multiblock.PlateRollerMultiBlock;
import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.immersiverailroading.tile.TileRailPreview;
import cam72cam.mod.config.ConfigGui;
import cam72cam.mod.gui.GuiRegistry;
import cam72cam.mod.gui.GuiRegistry.BlockGUI;
import cam72cam.mod.gui.GuiRegistry.EntityGUI;
import cam72cam.mod.gui.GuiRegistry.GUI;
import cam72cam.mod.gui.screen.IScreen;
import cam72cam.mod.resource.Identifier;

public final class GuiTypes {

    public static final EntityGUI<Freight> FREIGHT = GuiRegistry.registerEntityContainer(Freight.class, FreightContainer::new);
    public static final EntityGUI<FreightTank> TANK = GuiRegistry.registerEntityContainer(FreightTank.class, TankContainer::new);
    public static final EntityGUI<Tender> TENDER = GuiRegistry.registerEntityContainer(Tender.class, TenderContainer::new);
    public static final EntityGUI<SteamLocomotive> STEAM_LOCOMOTIVE = GuiRegistry.registerEntityContainer(SteamLocomotive.class, SteamLocomotiveContainer::new);
    public static final EntityGUI<LocomotiveDiesel> DIESEL_LOCOMOTIVE = GuiRegistry.registerEntityContainer(LocomotiveDiesel.class, TankContainer::new);

    public static final GUI RAIL = GuiRegistry.register(new Identifier(ImmersiveRailroading.MOD_ID, "RAIL"), TrackGui::new);
    public static final BlockGUI RAIL_PREVIEW = GuiRegistry.registerBlock(TileRailPreview.class, TrackGui::new);
    public static final GUI TRACK_EXCHANGER = GuiRegistry.register(new Identifier(ImmersiveRailroading.MOD_ID, "TRACK_EXCHANGER"), TrackExchangerGui::new);
    public static final GUI PAINT_BRUSH = GuiRegistry.register(new Identifier(ImmersiveRailroading.MOD_ID, "PAINT_BRUSH"), PaintBrushPicker::new);

    public static final BlockGUI STEAM_HAMMER = GuiRegistry.registerBlockContainer(TileMultiblock.class, SteamHammerContainer::new);
    public static final BlockGUI CASTING = GuiRegistry.registerBlock(TileMultiblock.class, GuiTypes::createMultiblockScreen);
    public static final BlockGUI PLATE_ROLLER = CASTING;
    public static final GUI CONFIG = GuiRegistry.register(new Identifier(ImmersiveRailroading.MOD_ID, "config"), () -> new ConfigGui(Config.class, ConfigGraphics.class, ConfigSound.class, ConfigPermissions.class));

    private static IScreen createMultiblockScreen(TileMultiblock tileMultiBlock) {
        if (!tileMultiBlock.isLoaded()) return null;
        if (tileMultiBlock.getName().equals(CastingMultiBlock.NAME)) return new CastingGUI(tileMultiBlock);
        if (tileMultiBlock.getName().equals(PlateRollerMultiBlock.NAME)) return new PlateRollerGUI(tileMultiBlock);
        return null;
    }

    public static void register() {
        // loads static classes and ctrs
    }

    private GuiTypes() {}

}

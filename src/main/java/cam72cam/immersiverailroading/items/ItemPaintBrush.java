package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.PaintBrushMode;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.*;
import cam72cam.mod.net.Packet;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ItemPaintBrush extends CustomItem {
    private static final Random rand = new Random(System.currentTimeMillis());

    public ItemPaintBrush() {
        super(ImmersiveRailroading.MODID, "item_paint_brush");

        Recipes.shapedRecipe(this, 1,
                Fuzzy.WOOL_BLOCK, Fuzzy.IRON_INGOT, Fuzzy.WOOD_STICK);
    }

    public static String nextRandomTexture(EntityRollingStock stock, String current) {
        List<String> choices = new ArrayList<>(stock.getDefinition().textureNames.keySet());
        if (choices.size() > 1) {
            choices.remove(current);
        }
        return choices.get(rand.nextInt(choices.size()));
    }

    @Override
    public List<CreativeTab> getCreativeTabs() {
        return Collections.singletonList(ItemTabs.MAIN_TAB);
    }

    @Override
    public int getStackSize() {
        return 1;
    }

    @Override
    public List<String> getTooltip(ItemStack stack) {
        PaintBrushMode pbm = new Data(stack).mode;
        List<String> tips = new ArrayList<>();
        tips.add(GuiText.PAINT_BRUSH_MODE_TOOLTIP.translate(pbm.toTranslatedString()));
        tips.add(GuiText.PAINT_BRUSH_DESCRIPTION_TOOLTIP.toString());
        return tips;
    }

    @Override
    public void onClickAir(Player player, World world, Player.Hand hand) {
        if (player.isCrouching()) {
            if (world.isServer) {
                ItemStack item = player.getHeldItem(hand);
                Data data = new Data(item);
                data.mode = PaintBrushMode.values()[(data.mode.ordinal() + 1) % (PaintBrushMode.values().length)];
                data.write();
                player.sendMessage(ChatText.BRUSH_MODE_SET.getMessage(data.mode.toTranslatedString()));
            }
        }
    }

    public static class PaintBrushPacket extends Packet {

        @TagField("stock")
        public EntityRollingStock stock;

        @TagField(value = "mode", typeHint = PaintBrushMode.class)
        public PaintBrushMode mode;

        @TagField("variant")
        public String variant;

        @TagField("gui_connected")
        public boolean gui_connected;

        public PaintBrushPacket(EntityRollingStock stock, PaintBrushMode mode, String variant, boolean gui_connected) {
            this.stock = stock;
            this.mode = mode;
            this.variant = variant;
            this.gui_connected = gui_connected;
        }

        public PaintBrushPacket() {
        }

        @Override
        protected void handle() {
            if (this.stock == null) {
                ImmersiveRailroading.warn("Invalid paint brush packet!");
                return;
            }
            switch (this.mode) {
                case GUI:
                    if (this.gui_connected) {
                        EntityCoupleableRollingStock coupled = (EntityCoupleableRollingStock) this.stock;
                        for (EntityCoupleableRollingStock stock : coupled.getTrain(false)) {
                            if (stock.getDefinition().textureNames.containsKey(this.variant)) {
                                stock.setTexture(this.variant);
                            }
                        }
                    } else {
                        this.stock.setTexture(this.variant);
                    }
                    break;
                case RANDOM_SINGLE:
                    this.stock.setTexture(nextRandomTexture(this.stock, this.stock.getTexture()));
                    break;
                case RANDOM_COUPLED:
                    EntityCoupleableRollingStock coupled = (EntityCoupleableRollingStock) this.stock;
                    for (EntityCoupleableRollingStock stock : coupled.getTrain(false)) {
                        stock.setTexture(nextRandomTexture(stock, stock.getTexture()));
                    }
                    break;
            }

            if (this.mode == PaintBrushMode.RANDOM_COUPLED) return;
            if (this.stock.getDefinition().textureNames.isEmpty()) {
                this.getPlayer().sendMessage(ChatText.BRUSH_NO_VARIANTS.getMessage());
            } else if (Config.ConfigDebug.debugPaintBrush) {
                //This is a debug log so use the untranslated Mode name
                this.getPlayer().sendMessage(ChatText.BRUSH_NEXT.getMessage(
                        this.stock.getDefinition().textureNames.getOrDefault(this.stock.getTexture(), "Unknown"),
                        this.mode.toTranslatedString()));
            }
        }
    }

    public static class Data extends ItemDataSerializer {
        @TagField(value = "mode", typeHint = PaintBrushMode.class)
        public PaintBrushMode mode;

        public Data(ItemStack stack) {
            super(stack);

            if (this.mode == null) {
                this.mode = PaintBrushMode.GUI;
            }
        }
    }
}

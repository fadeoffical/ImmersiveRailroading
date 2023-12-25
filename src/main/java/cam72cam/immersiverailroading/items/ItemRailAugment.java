package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.library.augment.Augment;
import cam72cam.immersiverailroading.library.augment.AugmentRegistry;
import cam72cam.immersiverailroading.library.augment.impl.DetectorAugment;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.CustomItem;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.text.TextUtil;
import cam72cam.mod.util.Facing;
import cam72cam.mod.world.World;
import trackapi.lib.Gauges;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ItemRailAugment extends CustomItem {

    public ItemRailAugment() {
        super(ImmersiveRailroading.MOD_ID, "item_augment");
    }

    @Override
    public List<CreativeTab> getCreativeTabs() {
        return Collections.singletonList(ItemTabs.MAIN_TAB);
    }

    @Override
    public int getStackSize() {
        return 16;
    }

    @Override
    public List<ItemStack> getItemVariants(CreativeTab tab) {
        if (tab != null && !tab.equals(ItemTabs.MAIN_TAB)) return Collections.emptyList();

        return AugmentRegistry.getAugments().keySet().stream().map(augmentId -> {
            ItemStack stack = new ItemStack(this, 1);
            Data data = new Data(stack);
            data.setAugment(AugmentRegistry.getNewAugment(augmentId)); // I don't like this
            data.write();
            return stack;
        }).collect(Collectors.toList());
    }

    @Override
    public List<String> getTooltip(ItemStack stack) {
        return Collections.singletonList(GuiText.GAUGE_TOOLTIP.translate(new Data(stack).getGauge()));
    }

    @Override
    public ClickResult onClickBlock(Player player, World world, Vec3i position, Player.Hand hand, Facing facing, Vec3d hit) {
        if (!player.hasPermission(Permissions.AUGMENT_TRACK)) return ClickResult.PASS;
        if (!BlockUtil.isIRRail(world, position)) return ClickResult.PASS;

        TileRailBase tileRailBase = world.getBlockEntity(position, TileRailBase.class);
        if (tileRailBase == null) return ClickResult.PASS;

        ItemStack stack = player.getHeldItem(hand);
        Data data = new Data(stack);
        if (tileRailBase.getAugment() != null) return ClickResult.REJECTED;
        if (!player.isCreative() && Gauge.getClosestGauge(tileRailBase.getTrackGauge()) != data.getGauge()) return ClickResult.REJECTED;

        TileRail parent = tileRailBase.getParentTile();
        if (parent == null) return ClickResult.REJECTED;

        if (!data.getAugment().canApplyAugment(tileRailBase)) return ClickResult.REJECTED;

        if (world.isServer) {
            Augment augment = AugmentRegistry.getNewAugment(data.getAugment().getId());
            tileRailBase.setAugment(augment);
            if (!player.isCreative()) stack.setCount(stack.getCount() - 1);
        }
        return ClickResult.ACCEPTED;
    }

    @Override
    public String getCustomName(ItemStack stack) {
        return TextUtil.translate("item.immersiverailroading:item_augment." + new Data(stack).getAugment().getId() + ".name");
    }

    public static class Data extends ItemDataSerializer {

        @TagField("gauge")
        private Gauge gauge;

        @TagField("augmentId")
        private Augment augment;

        public Data(ItemStack stack) {
            super(stack);
            this.setGauge(Gauge.getClosestGauge(Gauges.STANDARD));
            this.setAugment(AugmentRegistry.getNewAugment(DetectorAugment.ID));
        }

        public Gauge getGauge() {
            return this.gauge;
        }

        public void setGauge(Gauge gauge) {
            this.gauge = gauge;
        }

        public Augment getAugment() {
            return this.augment;
        }

        public void setAugment(Augment augment) {
            this.augment = augment;
        }
    }
}

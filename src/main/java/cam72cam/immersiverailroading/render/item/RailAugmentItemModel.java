package cam72cam.immersiverailroading.render.item;

import cam72cam.immersiverailroading.items.ItemRailAugment;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.render.Color;
import cam72cam.mod.render.ItemRender;
import cam72cam.mod.render.StandardModel;
import cam72cam.mod.world.World;
import util.Matrix4;

public final class RailAugmentItemModel implements ItemRender.IItemModel {

    private static final float HEIGHT = 0.4f;

    public static RailAugmentItemModel create() {
        return new RailAugmentItemModel();
    }

    private RailAugmentItemModel() {}

    @Override
    public StandardModel getModel(World world, ItemStack stack) {
        Color color = new ItemRailAugment.Data(stack).getAugment().getColor();
        Matrix4 model = new Matrix4().translate(0, HEIGHT, 0).scale(1, HEIGHT / 2, 1);
        return new StandardModel().addColorBlock(color, model);
    }
}

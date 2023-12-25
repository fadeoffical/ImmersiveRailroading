package cam72cam.immersiverailroading.blocks;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.mod.block.BlockTypeEntity;

public abstract class BlockRailBase extends BlockTypeEntity {

    public BlockRailBase(String name) {
        super(ImmersiveRailroading.MOD_ID, name);
    }

    @Override
    public boolean isConnectable() {
        return false;
    }
}

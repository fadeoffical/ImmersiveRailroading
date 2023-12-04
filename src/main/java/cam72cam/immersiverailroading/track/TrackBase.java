package cam72cam.immersiverailroading.track;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.IRBlocks;
import cam72cam.immersiverailroading.blocks.BlockRailBase;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.tile.TileRailGag;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.util.SingleCache;

public abstract class TrackBase {
    private final SingleCache<Vec3i, Vec3i> downCache = new SingleCache<>(Vec3i::down);
    public BuilderBase builder;
    public boolean solidNotRequired;
    protected Vec3i rel;
    private final SingleCache<Vec3i, Vec3i> posCache = new SingleCache<>(pos -> pos.add(this.rel));
    protected BlockRailBase block;
    private float bedHeight;
    private float railHeight;
    private boolean flexible = false;
    private Vec3i parent;

    public TrackBase(BuilderBase builder, Vec3i rel, BlockRailBase block) {
        this.builder = builder;
        this.rel = rel;
        this.block = block;
    }

    @SuppressWarnings("deprecation")
    public boolean canPlaceTrack() {
        Vec3i pos = this.getPos();

        return this.isDownSolid(true) && (BlockUtil.canBeReplaced(this.builder.world, pos, this.flexible || this.builder.overrideFlexible) || this.isOverTileRail());
    }

    public Vec3i getPos() {
        return this.posCache.get(this.builder.pos);
    }

    public boolean isDownSolid(boolean countFill) {
        Vec3i pos = this.downCache.get(this.getPos());
        return
                // Config to bypass solid block requirement
                !Config.ConfigDamage.requireSolidBlocks ||
                        // Turn table override
                        this.solidNotRequired ||
                        // Valid block beneath
                        this.builder.world.isTopSolid(pos) ||
                        // BlockType below is replaceable and we will replace it with something
                        countFill && (BlockUtil.canBeReplaced(this.builder.world, pos, false) && !this.builder.info.settings.railBedFill.isEmpty()) ||
                        // BlockType below is an IR Rail
                        BlockUtil.isIRRail(this.builder.world, pos);
    }

    public boolean isOverTileRail() {
        return this.builder.world.getBlockEntity(this.getPos(), TileRail.class) != null && this instanceof TrackGag;
    }

    public TileRailBase placeTrack(boolean actuallyPlace) {
        Vec3i pos = this.getPos();

        if (!actuallyPlace) {
            TileRailGag tr = (TileRailGag) IRBlocks.BLOCK_RAIL_GAG.createBlockEntity(this.builder.world, pos);
            if (this.parent != null) {
                tr.setParent(this.parent);
            } else {
                tr.setParent(this.builder.getParentPos());
            }
            tr.setRailHeight(this.getRailHeight());
            tr.setBedHeight(this.getBedHeight());
            return tr;
        }

        if (!this.builder.info.settings.railBedFill.isEmpty() && BlockUtil.canBeReplaced(this.builder.world, pos.down(), false)) {
            this.builder.world.setBlock(pos.down(), this.builder.info.settings.railBedFill);
        }


        TagCompound replaced = null;
        int hasSnow = 0;
        TileRailBase te = null;
        if (!this.builder.world.isAir(pos)) {
            if (this.builder.world.isBlock(pos, IRBlocks.BLOCK_RAIL_GAG)) {
                te = this.builder.world.getBlockEntity(pos, TileRailBase.class);
                if (te != null) {
                    replaced = te.getData();
                }
            } else {
                hasSnow = this.builder.world.getSnowLevel(pos);
                this.builder.world.breakBlock(pos);
            }
        }

        if (te != null) {
            te.setWillBeReplaced(true);
        }
        this.builder.world.setBlock(pos, this.block);
        if (te != null) {
            te.setWillBeReplaced(false);
        }

        TileRailBase tr = this.builder.world.getBlockEntity(pos, TileRailBase.class);
        tr.setReplaced(replaced);
        if (this.parent != null) {
            tr.setParent(this.parent);
        } else {
            tr.setParent(this.builder.getParentPos());
        }
        tr.setRailHeight(this.getRailHeight());
        tr.setBedHeight(this.getBedHeight());
        for (int i = 0; i < hasSnow; i++) {
            tr.handleSnowTick();
        }
        return tr;
    }

    public float getRailHeight() {
        return this.railHeight;
    }

    public float getBedHeight() {
        return this.bedHeight;
    }

    public void setBedHeight(float height) {
        this.bedHeight = height;
    }

    public void setRailHeight(float height) {
        this.railHeight = height;
    }

    public void setHeight(float height) {
        this.setBedHeight(height);
        this.setRailHeight(height);
    }

    public void setFlexible() {
        this.flexible = true;
    }

    public boolean isFlexible() {
        return this.flexible;
    }

    public void overrideParent(Vec3i blockPos) {
        this.parent = blockPos;
    }
}

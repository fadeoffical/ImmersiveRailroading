package cam72cam.immersiverailroading.multiblock;

import cam72cam.immersiverailroading.IRBlocks;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.IRFuzzy;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.IInventory;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Rotation;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.world.BlockInfo;
import cam72cam.mod.world.World;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Multiblock {
    protected static final FuzzyProvider AIR = null;
    protected final List<Vec3i> componentPositions;
    // z y x
    private final FuzzyProvider[][][] components;
    private final String name;

    protected Multiblock(String name, FuzzyProvider[][][] components) {
        this.name = name;
        this.components = components;
        this.componentPositions = new ArrayList<>();
        for (int z = 0; z < components.length; z++) {
            FuzzyProvider[][] zcomp = components[z];
            for (int y = 0; y < components[z].length; y++) {
                FuzzyProvider[] ycomp = zcomp[y];
                for (int x = 0; x < ycomp.length; x++) {
                    if (components[z][y][x] != null) {
                        this.componentPositions.add(new Vec3i(x, y, z));
                    }
                }
            }
        }
    }

    protected static FuzzyProvider STEEL() {
        return IRFuzzy::steelBlockOrFallback;
    }

    protected static FuzzyProvider CASING() {
        return () -> IRFuzzy.IR_CASTING_CASING.isEmpty() ? Fuzzy.NETHER_BRICK : IRFuzzy.IR_CASTING_CASING;
    }

    protected static FuzzyProvider L_ENG() {
        return () -> IRFuzzy.IR_LIGHT_ENG.isEmpty() ? Fuzzy.IRON_BLOCK : IRFuzzy.IR_LIGHT_ENG;
    }

    protected static FuzzyProvider H_ENG() {
        return () -> IRFuzzy.IR_HEAVY_ENG.isEmpty() ? Fuzzy.IRON_BLOCK : IRFuzzy.IR_HEAVY_ENG;
    }

    protected static FuzzyProvider S_SCAF() {
        return () -> IRFuzzy.IR_SCAFFOLDING.isEmpty() ? Fuzzy.IRON_BARS : IRFuzzy.IR_SCAFFOLDING;
    }

    public boolean tryCreate(World world, Vec3i pos) {
        for (Vec3i activationLocation : this.componentPositions) {
            for (Rotation rot : Rotation.values()) {
                Vec3i origin = pos.subtract(activationLocation.rotate(rot));
                boolean valid = true;
                for (Vec3i offset : this.componentPositions) {
                    valid = valid && this.checkValid(world, origin, offset, rot);
                }
                if (valid) {
                    if (world.isServer) {
                        this.instance(world, origin, rot).onCreate();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkValid(World world, Vec3i origin, Vec3i offset, Rotation rot) {
        Vec3i pos = origin.add(offset.rotate(rot));
        Fuzzy component = this.lookup(offset);
        return component.matches(world.getItemStack(pos));
    }

    public MultiblockInstance instance(World world, Vec3i origin, Rotation rot) {
        return this.newInstance(world, origin, rot);
    }

    private Fuzzy lookup(Vec3i offset) {
        return this.components[offset.z][offset.y][offset.x].get();
    }

    protected abstract MultiblockInstance newInstance(World world, Vec3i origin, Rotation rot);

    public void place(World world, Player player, Vec3i pos, Rotation rot) {
        Map<String, Integer> missing = new HashMap<String, Integer>();
        Vec3i origin = pos.subtract(this.placementPos().rotate(rot));
        for (Vec3i offset : this.componentPositions) {
            Fuzzy component = this.lookup(offset);
            Vec3i compPos = origin.add(offset.rotate(rot));
            if (!component.matches(world.getItemStack(compPos))) {
                if (!world.isAir(compPos)) {
                    if (BlockUtil.canBeReplaced(world, compPos, false)) {
                        world.breakBlock(compPos, true);
                    } else {
                        player.sendMessage(ChatText.INVALID_BLOCK.getMessage(compPos.x, compPos.y, compPos.z));
                        return;
                    }
                }
            }
        }

        for (Vec3i offset : this.componentPositions) {
            Fuzzy component = this.lookup(offset);
            Vec3i compPos = origin.add(offset.rotate(rot));
            if (!component.matches(world.getItemStack(compPos))) {
                if (!this.place(component, world, player, compPos)) {
                    Set<String> exStrs = component.enumerate()
                            .stream()
                            .map(ItemStack::getDisplayName)
                            .collect(Collectors.toSet());
                    String example = String.join(" | ", exStrs);
                    if (exStrs.size() > 1) {
                        example = "[ " + example + " ]";
                    }
                    if (!missing.containsKey(example)) {
                        missing.put(example, 0);
                    }
                    missing.put(example, missing.get(example) + 1);
                }
            }
        }

        if (missing.size() != 0) {
            player.sendMessage(ChatText.BUILD_MISSING.getMessage("", ""));
            for (String name : missing.keySet()) {
                player.sendMessage(PlayerMessage.direct(String.format("  - %d x %s", missing.get(name), name)));
            }
        }
    }

    public abstract Vec3i placementPos();

    private boolean place(Fuzzy fuzzy, World world, Player player, Vec3i pos) {
        if (player.isCreative()) {
            if (fuzzy.example() != null) {
                world.setBlock(pos, fuzzy.example());
            }
            return true;
        } else {
            IInventory inv = player.getInventory();
            for (int slot = 0; slot < inv.getSlotCount(); slot++) {
                ItemStack stack = inv.get(slot);
                if (fuzzy.matches(stack)) {

                    int count = stack.getCount();

                    ItemStack backup = player.getHeldItem(Player.Hand.PRIMARY).copy();
                    player.setHeldItem(Player.Hand.PRIMARY, stack.copy());
                    ClickResult result = player.clickBlock(Player.Hand.PRIMARY, pos, new Vec3d(0.5, 0, 0.5));
                    player.setHeldItem(Player.Hand.PRIMARY, backup);

                    if (result == ClickResult.ACCEPTED) {
                        if (inv.get(slot).getCount() == count) {
                            //Decrement inv slot if not already decremented
                            stack.setCount(stack.getCount() - 1);
                            inv.set(slot, stack);
                        }
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public Map<Vec3i, ItemStack> blueprint() {
        Map<Vec3i, ItemStack> result = new HashMap<>();
        for (Vec3i offset : this.componentPositions) {
            Fuzzy component = this.lookup(offset);
            result.put(offset, component.example());
        }
        return result;
    }

    public String getName() {
        return this.name;
    }

    @FunctionalInterface
    public interface FuzzyProvider {
        Fuzzy get();
    }

    public abstract class MultiblockInstance {
        protected final World world;
        protected final Vec3i origin;
        protected final Rotation rot;

        public MultiblockInstance(World world, Vec3i origin, Rotation rot) {
            this.world = world;
            this.origin = origin;
            this.rot = rot;
        }

        public void onCreate() {
            for (Vec3i offset : Multiblock.this.componentPositions) {
                Vec3i pos = this.getPos(offset);
                BlockInfo origState = this.world.getBlock(pos);

                this.world.setBlock(pos, IRBlocks.BLOCK_MULTIBLOCK);

                TileMultiblock te = this.world.getBlockEntity(pos, TileMultiblock.class);

                te.configure(Multiblock.this.name, this.rot, offset, origState);
            }
        }

        /*
         * Helpers
         */
        protected Vec3i getPos(Vec3i offset) {
            return this.origin.add(offset.rotate(this.rot));
        }

        public abstract boolean onBlockActivated(Player player, Player.Hand hand, Vec3i offset);

        public abstract int getInvSize(Vec3i offset);

        public abstract boolean isRender(Vec3i offset);

        public abstract void tick(Vec3i offset);

        public abstract boolean canInsertItem(Vec3i offset, int slot, ItemStack stack);

        public abstract boolean isOutputSlot(Vec3i offset, int slot);

        public abstract int getSlotLimit(Vec3i offset, int slot);

        public abstract boolean canRecievePower(Vec3i offset);

        public void onBreak() {
            for (Vec3i offset : Multiblock.this.componentPositions) {
                Vec3i pos = this.getPos(offset);
                TileMultiblock te = this.world.getBlockEntity(pos, TileMultiblock.class);
                if (te == null) {
                    this.world.breakBlock(pos, true);
                    continue;
                }
                te.onBreakEvent();
            }
        }

        protected TileMultiblock getTile(Vec3i offset) {
            TileMultiblock te = this.world.getBlockEntity(this.getPos(offset), TileMultiblock.class);
            if (te == null) {
                if (this.world.isServer) {
                    ImmersiveRailroading.warn("Multiblock TE is null: %s %s %s %s", this.getPos(offset), offset, this.world.isClient, this.getClass());
                }
                return null;
            }
            if (!te.isLoaded()) {
                if (this.world.isServer) {
                    ImmersiveRailroading.info("Multiblock is still loading: %s %s %s %s", this.getPos(offset), offset, this.world.isClient, this.getClass());
                }
                return null;
            }
            return te;
        }
    }
}

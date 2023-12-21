package cam72cam.immersiverailroading.tile;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.CraftingMachineMode;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.multiblock.MultiBlock.MultiblockInstance;
import cam72cam.immersiverailroading.multiblock.MultiBlockRegistry;
import cam72cam.immersiverailroading.net.MultiblockSelectCraftPacket;
import cam72cam.mod.block.BlockEntityTickable;
import cam72cam.mod.energy.Energy;
import cam72cam.mod.energy.IEnergy;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.item.IInventory;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.item.ItemStackHandler;
import cam72cam.mod.math.Rotation;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.util.Facing;
import cam72cam.mod.world.BlockInfo;

public class TileMultiblock extends BlockEntityTickable {

    @TagField("replaced")
    private BlockInfo replaced;

    @TagField("offset")
    private Vec3i offset;

    @TagField("rotation")
    private Rotation rotation;

    @TagField("name")
    private String name;

    @TagField("craftMode")
    private CraftingMachineMode craftMode = CraftingMachineMode.STOPPED;

    private long ticks;
    private MultiblockInstance mb;

    //Crafting
    @TagField("craftProgress")
    private int craftProgress = 0;

    @TagField("craftItem")
    private ItemStack craftItem = ItemStack.EMPTY;

    @TagField
    private final ItemStackHandler container = new ItemStackHandler(0);

    @TagField("energyStorage")
    private final Energy energy = new Energy(0, 1000);

    public void configure(String name, Rotation rot, Vec3i offset, BlockInfo replaced) {
        this.name = name;
        this.rotation = rot;
        this.offset = offset;
        this.replaced = replaced;

        this.container.setSize(this.getMultiblock().getInvSize(offset));

        this.markDirty();
    }

    public MultiblockInstance getMultiblock() {
        if (this.mb == null && this.isLoaded()) {
            this.mb = MultiBlockRegistry.get(this.name).instance(this.getWorld(), this.getOrigin(), this.rotation);
        }
        return this.mb;
    }

    public boolean isLoaded() {
        //TODO FIX ME bad init
        return this.name != null && !this.name.isEmpty();
    }

    public Vec3i getOrigin() {
        return this.getPos().subtract(this.offset.rotate(this.rotation));
    }

    @Override
    public void load(TagCompound nbt) {
        this.container.onChanged(slot -> this.markDirty());
        this.container.setSlotLimit(slot -> this.getMultiblock().getSlotLimit(this.offset, slot));
        this.energy.onChanged(this::markDirty);
    }

    @Override
    public void onBreak() {
        try {
            // MultiBlock break
            this.breakBlock();
        } catch (Exception ex) {
            ImmersiveRailroading.catching(ex);
            // Something broke
            // TODO figure out why
            this.getWorld().setToAir(this.getPos());
        }
    }

    /*
     * BlockType Functions to pass on to the multiblock
     */
    public void breakBlock() {
        if (this.getMultiblock() != null) {
            this.getMultiblock().onBreak();
        }
    }

    @Override
    public boolean onClick(Player player, Player.Hand hand, Facing facing, Vec3d hit) {
        if (!player.hasPermission(Permissions.MACHINIST)) return false;

        return this.onBlockActivated(player, hand);
    }

    public boolean onBlockActivated(Player player, Player.Hand hand) {
        return this.getMultiblock().onBlockActivated(player, hand, this.offset);
    }

    @Override
    public ItemStack onPick() {
        return ItemStack.EMPTY;
    }

    @Override
    public IInventory getInventory(Facing facing) {
        if (this.getMultiblock() == null || this.getMultiblock().getInvSize(this.offset) == 0) {
            return null;
        }

        if (this.container.getSlotCount() != this.getMultiblock().getInvSize(this.offset)) {
            this.container.setSize(this.getMultiblock().getInvSize(this.offset));
        }

        return new IInventory() {
            @Override
            public int getSlotCount() {
                return TileMultiblock.this.container.getSlotCount();
            }

            @Override
            public ItemStack get(int slot) {
                return TileMultiblock.this.container.get(slot);
            }

            @Override
            public void set(int slot, ItemStack stack) {
                TileMultiblock.this.container.set(slot, stack);
            }

            @Override
            public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
                if (TileMultiblock.this.getMultiblock().canInsertItem(TileMultiblock.this.offset, slot, stack)) {
                    return TileMultiblock.this.container.insert(slot, stack, simulate);
                }
                return stack;
            }

            @Override
            public ItemStack extract(int slot, int amount, boolean simulate) {
                if (TileMultiblock.this.getMultiblock().isOutputSlot(TileMultiblock.this.offset, slot)) {
                    return TileMultiblock.this.container.extract(slot, amount, simulate);
                }
                return ItemStack.EMPTY;
            }

            @Override
            public int getLimit(int slot) {
                return TileMultiblock.this.container.getLimit(slot);
            }
        };
    }

    @Override
    public IEnergy getEnergy(Facing facing) {
        return this.isLoaded() && this.getMultiblock().canRecievePower(this.offset) ? this.energy : null;
    }

    /*
     * Event Handlers
     */

    @Override
    public IBoundingBox getRenderBoundingBox() {
        return IBoundingBox.INFINITE;
    }

    @Override
    public void update() {
        this.ticks += 1;

        if (this.offset != null && this.getMultiblock() != null) {
            this.getMultiblock().tick(this.offset);
        } else if (this.ticks > 20) {
            // todo: use proper logger
            System.out.println("Error in multiblock, reverting");
            this.getWorld().breakBlock(this.getPos());
        }
    }

    public String getName() {
        return this.name;
    }

    public long getRenderTicks() {
        return this.ticks;
    }

    public ItemStackHandler getContainer() {
        if (this.container.getSlotCount() != this.getMultiblock().getInvSize(this.offset)) {
            this.container.setSize(this.getMultiblock().getInvSize(this.offset));
        }
        return this.container;
    }

    public void onBreakEvent() {
        for (int slot = 0; slot < this.container.getSlotCount(); slot++) {
            ItemStack item = this.container.get(slot);
            if (!item.isEmpty()) {
                this.getWorld().dropItem(item, this.getPos());
            }
        }

        if (this.replaced != null) {
            this.getWorld().setBlock(this.getPos(), this.replaced);
        }
    }

    public boolean isRender() {
        return this.getMultiblock().isRender(this.offset);
    }

    public double getRotation() {
        return 180 - Facing.EAST.rotate(this.rotation).getAngle();
    }

    /*
     * Crafting
     */
    public int getCraftProgress() {
        return this.craftProgress;
    }

    /*
     * Capabilities
     */

    public void setCraftProgress(int progress) {
        if (this.craftProgress != progress) {
            this.craftProgress = progress;
            this.markDirty();
        }
    }

    public CraftingMachineMode getCraftMode() {
        return this.craftMode;
    }

    public void setCraftMode(CraftingMachineMode mode) {
        if (this.getWorld().isServer) {
            if (this.craftMode != mode) {
                this.craftMode = mode;
                this.markDirty();
            }
        } else {
            new MultiblockSelectCraftPacket(this.getPos(), this.craftItem, mode).sendToServer();
        }
    }

    public ItemStack getCraftItem() {
        return this.craftItem;
    }

    public void setCraftItem(ItemStack selected) {
        if (this.getWorld().isServer) {
            if (selected == null || !selected.equals(this.craftItem)) {
                this.craftItem = selected == null ? null : selected.copy();
                this.craftProgress = 0;
                this.markDirty();
            }
        } else {
            new MultiblockSelectCraftPacket(this.getPos(), selected, this.craftMode).sendToServer();
        }
    }
}

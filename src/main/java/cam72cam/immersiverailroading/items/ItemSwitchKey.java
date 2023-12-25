package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.Permissions;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.IRFuzzy;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.*;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.util.Facing;
import cam72cam.mod.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ItemSwitchKey extends CustomItem {
    public static final long CLICK_COOLDOWN_MILLIS = 250L;
    public static final String LAST_USED_ON_KEY = "lastUsedOn";
    public static final String FORCED_INTO_STATE_KEY = "forcedIntoState";
    public static final String LAST_USED_AT_KEY = "lastUsedAt";

    public ItemSwitchKey() {
        super(ImmersiveRailroading.MOD_ID, "item_switch_key");

        Fuzzy steel = Fuzzy.STEEL_INGOT;
        IRFuzzy.registerSteelRecipe(this, 2,
                null, steel,
                null, steel,
                steel, steel);
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
        Data data = new Data(stack);
        if (data.isEmpty()) {
            return Collections.singletonList(GuiText.SWITCH_KEY_TOOLTIP.toString());
        } else {
            return Arrays.asList(
                    GuiText.SWITCH_KEY_TOOLTIP.toString(),
                    GuiText.SWITCH_KEY_DATA_TOOLTIP.translate(
                            data.lastUsedOn.toString(),
                            data.forcedIntoState.toString())
            );
        }
    }

    @Override
    public ClickResult onClickBlock(Player player, World world, Vec3i pos, Player.Hand hand, Facing facing, Vec3d inBlockPos) {
        if (world.isAir(pos)) {
            return ClickResult.PASS;
        }

        if (BlockUtil.isIRRail(world, pos)) {
            TileRailBase tileRail = world.getBlockEntity(pos, TileRailBase.class);
            if (tileRail == null) {
                return ClickResult.PASS;
            }

            TileRail tileSwitch = tileRail.findSwitchParent();
            if (tileSwitch == null) {
                return ClickResult.PASS;
            }

            return this.lockSwitch(player, world, player.getHeldItem(hand), tileRail, tileSwitch);
        }

        return this.resetSwitchFromNbt(player, world, hand);
    }

    @Override
    public void onClickAir(Player player, World world, Player.Hand hand) {
        this.resetSwitchFromNbt(player, world, hand);
    }

    private ClickResult resetSwitchFromNbt(Player player, World world, Player.Hand hand) {
        ItemStack stack = player.getHeldItem(hand);
        Data data = new Data(stack);

        if (!player.hasPermission(Permissions.SWITCH_CONTROL) || data.isInClickCooldown()) {
            return ClickResult.REJECTED;
        }

        if (data.isEmpty()) {
            if (world.isClient) {
                player.sendMessage(PlayerMessage.translate(ChatText.SWITCH_CANT_RESET.toString()));
            }
            return ClickResult.REJECTED;
        }

        TileRailBase lastUsedOn = data.getLastUsedOnSwitch(world);
        if (lastUsedOn == null) {
            // Clear out invalid data
            if (world.isServer) {
                data.clear();
                data.write();
            }
            return ClickResult.REJECTED;
        }


        if (lastUsedOn.isSwitchForced()) {
            if (world.isServer) {
                lastUsedOn.setSwitchForced(SwitchState.NONE);
                data.getLastUsedOnSwitch(world).markDirty();

                data.clear();
                data.write();
            }

            if (world.isClient) {
                player.sendMessage(PlayerMessage.translate(ChatText.SWITCH_RESET.toString()));
            }

            return ClickResult.ACCEPTED;
        } else {
            // Clear out invalid data
            if (world.isServer) {
                data.clear();
                data.write();
            }

            if (world.isClient) {
                player.sendMessage(PlayerMessage.translate(ChatText.SWITCH_ALREADY_RESET.toString()));
            }

            return ClickResult.REJECTED;
        }
    }

    private ClickResult lockSwitch(Player player, World world, ItemStack stack, TileRailBase clickedTileRail, TileRail tileSwitch) {
        Data data = new Data(stack);
        if (data.isInClickCooldown()) {
            return ClickResult.REJECTED;
        }

        SwitchState newSwitchForcedState = tileSwitch.cycleSwitchForced();
        clickedTileRail.markDirty();

        if (tileSwitch.isSwitchForced()) {
            if (world.isServer) {
                data.lastUsedOn = clickedTileRail.getPos();
                data.forcedIntoState = newSwitchForcedState;
                data.lastUsedAt = System.currentTimeMillis();
                data.write();
            }

            if (world.isClient) {
                player.sendMessage(ChatText.SWITCH_LOCKED.getMessage(newSwitchForcedState.toString()));
            }

        } else {
            if (world.isServer) {
                data.clear();
                data.write();
            }

            if (world.isClient) {
                player.sendMessage(ChatText.SWITCH_UNLOCKED.getMessage());
            }

        }
        return ClickResult.ACCEPTED;
    }

    public static class Data extends ItemDataSerializer {
        @TagField(value = LAST_USED_ON_KEY)
        public Vec3i lastUsedOn;

        @TagField(value = FORCED_INTO_STATE_KEY)
        public SwitchState forcedIntoState;

        @TagField(value = LAST_USED_AT_KEY)
        public Long lastUsedAt;

        public Data(ItemStack stack) {
            super(stack);

            if (this.lastUsedAt == null) {
                this.lastUsedAt = 0L;
            }
        }

        public boolean isInClickCooldown() {
            return System.currentTimeMillis() < this.lastUsedAt + CLICK_COOLDOWN_MILLIS;
        }

        public TileRailBase getLastUsedOnSwitch(World world) {
            if (this.isEmpty()) {
                return null;
            }

            return world.getBlockEntity(this.lastUsedOn, TileRailBase.class);
        }

        public boolean isEmpty() {
            return this.lastUsedOn == null;
        }

        public void clear() {
            this.lastUsedOn = null;
            this.forcedIntoState = null;
            this.lastUsedAt = System.currentTimeMillis();
        }
    }
}

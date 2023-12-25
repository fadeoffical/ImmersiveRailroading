package cam72cam.immersiverailroading.items;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.CraftingType;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.augment.Augment;
import cam72cam.immersiverailroading.registry.CarPassengerDefinition;
import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveDefinition;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.ItemCastingCost;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.ArmorSlot;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.util.Facing;
import cam72cam.mod.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ItemRollingStock extends BaseItemRollingStock {

    public ItemRollingStock() {
        super(ImmersiveRailroading.MOD_ID, "item_rolling_stock");
    }

    @Override
    public List<CreativeTab> getCreativeTabs() {
        return Arrays.asList(ItemTabs.STOCK_TAB, ItemTabs.LOCOMOTIVE_TAB, ItemTabs.PASSENGER_TAB);
    }

    @Override
    public int getStackSize() {
        return 1;
    }

    @Override
    public List<ItemStack> getItemVariants(CreativeTab tab) {
        List<ItemStack> items = new ArrayList<>();
        for (EntityRollingStockDefinition def : DefinitionManager.getDefinitions()) {
            if (tab != null) {
                boolean isCabCar = (def instanceof LocomotiveDefinition && ((LocomotiveDefinition) def).isCabCar());
                if (def instanceof CarPassengerDefinition || isCabCar) {
                    if (!tab.equals(ItemTabs.PASSENGER_TAB)) {
                        continue;
                    }
                } else if (def instanceof LocomotiveDefinition && !isCabCar) {
                    if (!tab.equals(ItemTabs.LOCOMOTIVE_TAB)) {
                        continue;
                    }
                } else {
                    if (!tab.equals(ItemTabs.STOCK_TAB)) {
                        continue;
                    }
                }
            }
            ItemStack stack = new ItemStack(this, 1);
            Data data = new Data(stack);
            data.rollingStockDefinition = def;
            data.gauge = def.recommended_gauge;
            data.write();
            if (tab == null && def.textureNames.size() > 1 && ConfigGraphics.stockItemVariants) {
                for (String texture : def.textureNames.keySet()) {
                    ItemStack textured = stack.copy();
                    data = new Data(textured);
                    data.texture = texture;
                    data.write();
                    items.add(textured);
                }
            } else {
                items.add(stack);
            }
        }
        return items;
    }

    @Override
    public List<String> getTooltip(ItemStack stack) {
        List<String> tooltip = new ArrayList<>();

        Data data = new Data(stack);

        Gauge gauge = data.gauge;
        EntityRollingStockDefinition def = data.rollingStockDefinition;
        if (def != null) {
            tooltip.addAll(def.getTooltip(gauge));
        }
        tooltip.add(GuiText.GAUGE_TOOLTIP.translate(gauge));
        String texture = data.texture;
        if (texture != null && def != null && def.textureNames.get(texture) != null) {
            tooltip.add(GuiText.TEXTURE_TOOLTIP.translate(def.textureNames.get(texture)));
        }

        if (def != null) {
            tooltip.addAll(def.getExtraTooltipInfo());
        }

        return tooltip;
    }

    @Override
    public ClickResult onClickBlock(Player player, World world, Vec3i pos, Player.Hand hand, Facing facing, Vec3d hit) {
        if (BlockUtil.isIRRail(world, pos)) {
            TileRailBase railBase = world.getBlockEntity(pos, TileRailBase.class);
            railBase.ifAugmentPresent(augment -> {
                if (augment.doesNotFilter()) return;
                if (!world.isServer) return;

                Data data = new Data(player.getHeldItem(hand));
                EntityRollingStockDefinition rollingStockDefinition = data.rollingStockDefinition;
                String definitionId = rollingStockDefinition == null ? null : rollingStockDefinition.defID;
                String stockName = rollingStockDefinition == null ? "Unknown" : rollingStockDefinition.name();

                augment.updateFilter(definitionId, result -> {
                    if (result == Augment.FilterUpdateResult.SET) {
                        player.sendMessage(ChatText.SET_AUGMENT_FILTER.getMessage(stockName));
                    } else if (result == Augment.FilterUpdateResult.RESET) {
                        player.sendMessage(ChatText.RESET_AUGMENT_FILTER.getMessage());
                    } else if (result == Augment.FilterUpdateResult.UNSUPPORTED) {
                        player.sendMessage(ChatText.UNSUPPORTED_AUGMENT_FILTER.getMessage());
                    }
                });
            });
            return ClickResult.ACCEPTED;
        }
        return tryPlaceStock(player, world, pos, hand, null);
    }


    @Override
    public boolean isValidArmor(ItemStack stack, ArmorSlot armorType, Entity entity) {
        return ConfigGraphics.trainsOnTheBrain && armorType == ArmorSlot.HEAD;
    }

    public List<ItemStack> getCastableComponents(ItemStack stock) {
        if (!stock.is(IRItems.ITEM_ROLLING_STOCK)) {
            return Collections.emptyList();
        }
        EntityRollingStockDefinition rollingStockDefiniton = new ItemRollingStockComponent.Data(stock).rollingStockDefinition;
        if (rollingStockDefiniton == null) {
            return Collections.emptyList();
        }
        Gauge gauge = new ItemRollingStockComponent.Data(stock).gauge;
        return rollingStockDefiniton.getItemComponents().stream()
                .filter(component -> !component.isWooden(rollingStockDefiniton))
                .filter(component -> component.crafting == CraftingType.CASTING || component.crafting == CraftingType.CASTING_HAMMER)
                .filter(component -> component.getCastCost(rollingStockDefiniton, gauge) != ItemCastingCost.BAD_CAST_COST)
                .map(component -> {
                    ItemStack item = new ItemStack(IRItems.ITEM_ROLLING_STOCK_COMPONENT, 1);
                    ItemRollingStockComponent.Data data = new ItemRollingStockComponent.Data(item);
                    data.rollingStockDefinition = rollingStockDefiniton;
                    data.gauge = gauge;
                    data.componentType = component;
                    data.rawCast = true;
                    data.write();
                    return item;
                })
                .collect(Collectors.toList());
    }

    public static class Data extends BaseItemRollingStock.Data {
        public Data(ItemStack stack) {
            super(stack);
        }
    }
}

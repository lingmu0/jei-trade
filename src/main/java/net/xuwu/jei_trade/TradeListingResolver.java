package net.xuwu.jei_trade;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the vanilla 1.21.1 trade factories as typed definitions, following
 * Advanced Loot Info's approach. Unknown and third-party factories still use
 * the sampler in {@link TradeCatalogBuilder}.
 */
final class TradeListingResolver {
    private TradeListingResolver() {
    }

    static TradeRecipe resolve(Level world, VillagerTrades.ItemListing listing,
                               ResourceLocation entityType, ResourceLocation profession,
                               List<ResourceLocation> workstations, int level) {
        try {
            return resolveTyped(world, listing, entityType, profession, workstations, level);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Jei_trade.LOGGER.debug("Could not read typed trade definition {}", listing.getClass(), ex);
            return null;
        }
    }

    private static TradeRecipe resolveTyped(Level world, VillagerTrades.ItemListing listing,
                                            ResourceLocation entityType, ResourceLocation profession,
                                            List<ResourceLocation> workstations, int level)
            throws ReflectiveOperationException {
        if (listing instanceof VillagerTrades.DyedArmorForEmeralds trade) {
            int value = intField(trade, "value");
            return definition(entityType, profession, workstations, level,
                    List.of(new ItemStack(Items.EMERALD)), value,
                    List.of(ItemStack.EMPTY), 0,
                    List.of(itemField(trade, "item").getDefaultInstance()), 1,
                    intField(trade, "maxUses"), intField(trade, "villagerXp"), 0.2F,
                    List.of(Component.translatable("jei_trade.detail.random_dye")));
        }
        if (listing instanceof VillagerTrades.EmeraldForItems trade) {
            ItemCost cost = field(trade, "itemStack", ItemCost.class);
            return definition(entityType, profession, workstations, level,
                    List.of(cost.itemStack()), cost.count(),
                    List.of(ItemStack.EMPTY), 0,
                    List.of(new ItemStack(Items.EMERALD)), intField(trade, "emeraldAmount"),
                    intField(trade, "maxUses"), intField(trade, "villagerXp"),
                    floatField(trade, "priceMultiplier"), optionalDetail(trade, "enchantmentProvider"));
        }
        if (listing instanceof VillagerTrades.EmeraldsForVillagerTypeItem trade) {
            Map<?, ?> trades = field(trade, "trades", Map.class);
            List<ItemStack> outputs = trades.values().stream()
                    .map(value -> ((net.minecraft.world.item.Item) value).getDefaultInstance())
                    .distinct()
                    .toList();
            return definition(entityType, profession, workstations, level,
                    List.of(new ItemStack(Items.EMERALD)), intField(trade, "cost"),
                    List.of(ItemStack.EMPTY), 0, outputs, 1,
                    intField(trade, "maxUses"), intField(trade, "villagerXp"), 0.2F,
                    List.of(Component.translatable("jei_trade.detail.villager_type_output")));
        }
        if (listing instanceof VillagerTrades.EnchantBookForEmeralds trade) {
            TagKey<Enchantment> tag = field(trade, "tradeableEnchantments", TagKey.class);
            int minLevel = intField(trade, "minLevel");
            int maxLevel = intField(trade, "maxLevel");
            return definition(entityType, profession, workstations, level,
                    List.of(new ItemStack(Items.EMERALD)), 5, 64,
                    List.of(ItemStack.EMPTY), 0,
                    enchantedBookVariants(world.registryAccess().registryOrThrow(Registries.ENCHANTMENT),
                            tag, minLevel, maxLevel), 1,
                    12, intField(trade, "villagerXp"), 0.2F,
                    List.of(Component.translatable("jei_trade.detail.random_book_enchant")));
        }
        if (listing instanceof VillagerTrades.EnchantedItemForEmeralds trade) {
            ItemStack item = stackField(trade, "itemStack");
            int baseCost = intField(trade, "baseEmeraldCost");
            return definition(entityType, profession, workstations, level,
                    List.of(new ItemStack(Items.EMERALD)), baseCost + 5, baseCost + 19,
                    List.of(ItemStack.EMPTY), 0, List.of(item), item.getCount(),
                    intField(trade, "maxUses"), intField(trade, "villagerXp"),
                    floatField(trade, "priceMultiplier"),
                    List.of(Component.translatable("jei_trade.detail.random_enchant", 5, 19),
                            treasureDetail(world, item)));
        }
        if (listing instanceof VillagerTrades.ItemsAndEmeraldsToItems trade) {
            ItemCost from = field(trade, "fromItem", ItemCost.class);
            ItemStack to = stackField(trade, "toItem");
            return definition(entityType, profession, workstations, level,
                    List.of(from.itemStack()), from.count(),
                    List.of(new ItemStack(Items.EMERALD)), intField(trade, "emeraldCost"),
                    List.of(to), to.getCount(), intField(trade, "maxUses"),
                    intField(trade, "villagerXp"), floatField(trade, "priceMultiplier"),
                    optionalDetail(trade, "enchantmentProvider"));
        }
        if (listing instanceof VillagerTrades.ItemsForEmeralds trade) {
            ItemStack item = stackField(trade, "itemStack");
            return definition(entityType, profession, workstations, level,
                    List.of(new ItemStack(Items.EMERALD)), intField(trade, "emeraldCost"),
                    List.of(ItemStack.EMPTY), 0, List.of(item), item.getCount(),
                    intField(trade, "maxUses"), intField(trade, "villagerXp"),
                    floatField(trade, "priceMultiplier"), optionalDetail(trade, "enchantmentProvider"));
        }
        if (listing instanceof VillagerTrades.SuspiciousStewForEmerald trade) {
            SuspiciousStewEffects effects = field(trade, "effects", SuspiciousStewEffects.class);
            ItemStack stew = Items.SUSPICIOUS_STEW.getDefaultInstance();
            stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, effects);
            return definition(entityType, profession, workstations, level,
                    List.of(new ItemStack(Items.EMERALD)), 1,
                    List.of(ItemStack.EMPTY), 0, List.of(stew), 1,
                    12, intField(trade, "xp"), floatField(trade, "priceMultiplier"),
                    List.of(Component.translatable("jei_trade.detail.stew_effects")));
        }
        if (listing instanceof VillagerTrades.TippedArrowForItemsAndEmeralds trade) {
            ItemStack to = stackField(trade, "toItem");
            return definition(entityType, profession, workstations, level,
                    List.of(itemField(trade, "fromItem").getDefaultInstance()), intField(trade, "fromCount"),
                    List.of(new ItemStack(Items.EMERALD)), intField(trade, "emeraldCost"),
                    List.of(to), intField(trade, "toCount"), intField(trade, "maxUses"),
                    intField(trade, "villagerXp"), floatField(trade, "priceMultiplier"), List.of());
        }
        if (listing instanceof VillagerTrades.TreasureMapForEmeralds trade) {
            String displayName = field(trade, "displayName", String.class);
            ItemStack map = Items.MAP.getDefaultInstance();
            map.set(DataComponents.ITEM_NAME, Component.translatable(displayName));
            return definition(entityType, profession, workstations, level,
                    List.of(new ItemStack(Items.EMERALD)), intField(trade, "emeraldCost"),
                    List.of(new ItemStack(Items.COMPASS)), 1, List.of(map), 1,
                    intField(trade, "maxUses"), intField(trade, "villagerXp"), 0.2F,
                    List.of(Component.translatable("jei_trade.detail.treasure_map",
                            Component.translatable(displayName))));
        }
        return null;
    }

    private static TradeRecipe definition(ResourceLocation entityType, ResourceLocation profession,
                                          List<ResourceLocation> workstations, int level,
                                          List<ItemStack> buyA, int buyAMin, int buyAMax,
                                          List<ItemStack> buyB, int buyBCount,
                                          List<ItemStack> result, int resultCount,
                                          int maxUses, int xp, float priceMultiplier,
                                          List<Component> details) {
        return definition(entityType, profession, workstations, level,
                buyA, buyAMin, buyAMax, buyB, buyBCount, buyBCount,
                result, resultCount, resultCount, maxUses, xp, priceMultiplier, details);
    }

    private static TradeRecipe definition(ResourceLocation entityType, ResourceLocation profession,
                                          List<ResourceLocation> workstations, int level,
                                          List<ItemStack> buyA, int buyACount,
                                          List<ItemStack> buyB, int buyBCount,
                                          List<ItemStack> result, int resultCount,
                                          int maxUses, int xp, float priceMultiplier,
                                          List<Component> details) {
        return TradeRecipe.fromDefinition(entityType, profession, workstations, level,
                buyA, buyACount, buyACount, buyB, buyBCount, buyBCount,
                result, resultCount, resultCount, maxUses, xp, priceMultiplier, details);
    }

    private static TradeRecipe definition(ResourceLocation entityType, ResourceLocation profession,
                                          List<ResourceLocation> workstations, int level,
                                          List<ItemStack> buyA, int buyAMin, int buyAMax,
                                          List<ItemStack> buyB, int buyBMin, int buyBMax,
                                          List<ItemStack> result, int resultMin, int resultMax,
                                          int maxUses, int xp, float priceMultiplier,
                                          List<Component> details) {
        return TradeRecipe.fromDefinition(entityType, profession, workstations, level,
                buyA, buyAMin, buyAMax, buyB, buyBMin, buyBMax,
                result, resultMin, resultMax, maxUses, xp, priceMultiplier, details);
    }

    private static List<ItemStack> enchantedBookVariants(Registry<Enchantment> enchantments,
                                                         TagKey<Enchantment> tag,
                                                         int minLevel, int maxLevel) {
        List<ItemStack> variants = new ArrayList<>();
        for (Holder<Enchantment> holder : enchantments.getTagOrEmpty(tag)) {
            Enchantment enchantment = holder.value();
            for (int level = Math.max(minLevel, enchantment.getMinLevel());
                 level <= Math.min(maxLevel, enchantment.getMaxLevel()); level++) {
                variants.add(EnchantedBookItem.createForEnchantment(
                        new EnchantmentInstance(holder, level)));
                if (variants.size() >= 256) return variants;
            }
        }
        return variants.isEmpty() ? List.of(new ItemStack(Items.ENCHANTED_BOOK)) : variants;
    }

    private static List<Component> optionalDetail(Object target, String fieldName)
            throws ReflectiveOperationException {
        Optional<?> provider = field(target, fieldName, Optional.class);
        return provider.isPresent()
                ? List.of(Component.translatable("jei_trade.detail.random_enchant_output"))
                : List.of();
    }

    private static Component treasureDetail(Level world, ItemStack item) {
        Registry<Enchantment> enchantments = world.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT);
        boolean hasCandidate = false;
        boolean hasTreasure = false;
        boolean hasNonTreasure = false;
        boolean hasUnknown = false;
        List<Holder<Enchantment>> tradedEnchantments = new ArrayList<>();
        for (Holder<Enchantment> holder : enchantments.getTagOrEmpty(EnchantmentTags.ON_TRADED_EQUIPMENT)) {
            tradedEnchantments.add(holder);
        }
        for (EnchantmentInstance candidate : EnchantmentHelper.getAvailableEnchantmentResults(
                19, item, tradedEnchantments.stream())) {
            hasCandidate = true;
            if (candidate.enchantment.is(EnchantmentTags.TREASURE)) {
                hasTreasure = true;
            } else if (candidate.enchantment.is(EnchantmentTags.NON_TREASURE)) {
                hasNonTreasure = true;
            } else {
                hasUnknown = true;
            }
        }
        return Component.translatable("jei_trade.detail.treasure_enchant",
                Component.translatable(treasureStatusKey(hasCandidate, hasTreasure,
                        hasNonTreasure, hasUnknown)));
    }

    private static String treasureStatusKey(boolean hasCandidate, boolean hasTreasure,
                                             boolean hasNonTreasure, boolean hasUnknown) {
        if (!hasCandidate) return "jei_trade.unknown";
        if (hasTreasure && !hasNonTreasure && !hasUnknown) return "jei_trade.yes";
        if (!hasTreasure && hasNonTreasure && !hasUnknown) return "jei_trade.no";
        return "jei_trade.possible";
    }

    private static int intField(Object target, String name) throws ReflectiveOperationException {
        return field(target, name, Integer.class);
    }

    private static float floatField(Object target, String name) throws ReflectiveOperationException {
        return field(target, name, Float.class);
    }

    private static ItemStack stackField(Object target, String name) throws ReflectiveOperationException {
        return field(target, name, ItemStack.class).copy();
    }

    private static net.minecraft.world.item.Item itemField(Object target, String name)
            throws ReflectiveOperationException {
        return field(target, name, net.minecraft.world.item.Item.class);
    }

    private static <T> T field(Object target, String name, Class<T> type)
            throws ReflectiveOperationException {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return type.cast(field.get(target));
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }
}

package net.xuwu.jei_trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;

import java.util.ArrayList;
import java.util.List;

/** Reads the registered datapack definition without generating exploration maps or world chunks. */
final class TradeListingResolver {
    private TradeListingResolver() {}

    static TradeRecipe resolve(Level level, VillagerTrade trade, Identifier entityType,
                               Identifier profession, List<Identifier> workstations, int merchantLevel) {
        try {
            var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
            JsonObject json = VillagerTrade.CODEC.encodeStart(ops, trade).getOrThrow().getAsJsonObject();
            JsonArray modifiers = json.has("given_item_modifiers")
                    ? json.getAsJsonArray("given_item_modifiers") : new JsonArray();
            boolean map = containsFunction(modifiers, "minecraft:exploration_map");
            boolean book = (modifiers.size() == 1
                    || modifiers.size() == 2 && isPreviewGuard(modifiers.get(1), "minecraft:stored_enchantments"))
                    && isFunction(modifiers.get(0), "minecraft:enchant_randomly")
                    && !modifiers.get(0).getAsJsonObject().has("conditions");
            if (!map && !book && !modifiers.isEmpty()) return null;

            TradeCost wants = TradeCost.CODEC.parse(ops, json.get("wants")).getOrThrow();
            TradeCost additional = json.has("additional_wants")
                    ? TradeCost.CODEC.parse(ops, json.get("additional_wants")).getOrThrow() : null;
            ItemStack output = ItemStackTemplate.CODEC.parse(ops, json.get("gives")).getOrThrow().create();
            int[] countA = range(json.getAsJsonObject("wants").get("count"), 1);
            int[] countB = additional == null ? new int[]{0, 0}
                    : range(json.getAsJsonObject("additional_wants").get("count"), 1);
            if (countA == null || countB == null) return null;
            List<ItemStack> outputs = new ArrayList<>();
            List<Component> details = new ArrayList<>();
            if (json.has("merchant_predicate")) {
                details.add(Component.translatable("jei_trade.detail.conditional_trade"));
            }
            if (book) {
                if (!output.is(Items.BOOK) && !output.is(Items.ENCHANTED_BOOK)) return null;
                JsonObject enchant = modifiers.get(0).getAsJsonObject();
                var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                List<Holder<Enchantment>> enchantments = enchant.has("options")
                        ? RegistryCodecs.homogeneousList(Registries.ENCHANTMENT)
                            .parse(ops, enchant.get("options")).getOrThrow().stream().toList()
                        : registry.listElements().map(h -> (Holder<Enchantment>) h).toList();
                var doubles = json.has("double_trade_price_enchantments")
                        ? RegistryCodecs.homogeneousList(Registries.ENCHANTMENT)
                            .parse(ops, json.get("double_trade_price_enchantments")).getOrThrow()
                        : null;
                boolean extraCost = enchant.has("include_additional_cost_component")
                        && enchant.get("include_additional_cost_component").getAsBoolean();
                int minExtra = Integer.MAX_VALUE;
                int maxExtra = 0;
                boolean onlyCompatible = !enchant.has("only_compatible")
                        || enchant.get("only_compatible").getAsBoolean();
                for (Holder<Enchantment> holder : enchantments) {
                    if (!output.is(Items.BOOK) && onlyCompatible && !output.supportsEnchantment(holder)) continue;
                    for (int n = holder.value().getMinLevel(); n <= holder.value().getMaxLevel(); n++) {
                        ItemStack enchanted = new ItemStack(Items.ENCHANTED_BOOK);
                        enchanted.applyComponents(output.getComponentsPatch());
                        enchanted.enchant(holder, n);
                        if (outputs.size() < 256) outputs.add(enchanted);
                        int multiplier = doubles != null && doubles.contains(holder) ? 2 : 1;
                        minExtra = Math.min(minExtra, (2 + 3 * n) * multiplier);
                        maxExtra = Math.max(maxExtra, (6 + 13 * n) * multiplier);
                    }
                }
                if (outputs.isEmpty()) return null;
                if (extraCost) {
                    countA[0] += minExtra;
                    countA[1] += maxExtra;
                }
                details.add(Component.translatable("jei_trade.detail.random_book_enchant"));
            } else if (map) {
                // Apply names/components, but replace exploration functions before evaluating them.
                ServerLevel world = (ServerLevel) level;
                Entity merchant = EntityType.WANDERING_TRADER.create(world, EntitySpawnReason.COMMAND);
                if (merchant == null) return null;
                try {
                    var context = TradeCatalogBuilder.context(world, merchant, 0);
                    for (JsonElement modifier : modifiers) {
                        output = LootItemFunctions.ROOT_CODEC.parse(ops, withoutMapSearch(modifier))
                                .getOrThrow().apply(output, context);
                    }
                } finally {
                    merchant.discard();
                }
                if (output.isEmpty()) return null;
                ItemStack mapStack = new ItemStack(Items.FILLED_MAP, output.getCount());
                mapStack.applyComponents(output.getComponentsPatch());
                outputs.add(mapStack);
                Component name = mapStack.get(DataComponents.ITEM_NAME);
                details.add(Component.translatable("jei_trade.detail.treasure_map",
                        name == null ? mapStack.getHoverName() : name));
            } else {
                outputs.add(output);
            }
            int maxA = wants.item().value().getDefaultMaxStackSize();
            countA[0] = Math.clamp(countA[0], 1, maxA);
            countA[1] = Math.clamp(countA[1], countA[0], maxA);
            if (additional != null) {
                int maxB = additional.item().value().getDefaultMaxStackSize();
                countB[0] = Math.clamp(countB[0], 1, maxB);
                countB[1] = Math.clamp(countB[1], countB[0], maxB);
            }
            int[] uses = range(json.get("max_uses"), 4);
            int[] xp = range(json.get("xp"), 1);
            if (uses == null || xp == null) return null;
            JsonElement discount = json.get("reputation_discount");
            if (discount != null && !discount.isJsonPrimitive()) return null;
            float multiplier = discount == null ? 0 : discount.getAsFloat();
            ItemStack costA = new ItemCost(wants.item(), countA[0], wants.components()).itemStack();
            ItemStack costB = additional == null ? ItemStack.EMPTY
                    : new ItemCost(additional.item(), countB[0], additional.components()).itemStack();
            return TradeRecipe.fromDefinition(entityType, profession, workstations, merchantLevel,
                    List.of(costA), countA[0], countA[1], List.of(costB), countB[0], countB[1],
                    outputs, output.getCount(), output.getCount(), uses[1], xp[1], multiplier, details);
        } catch (RuntimeException ex) {
            Jei_trade.LOGGER.debug("Could not read datapack trade definition", ex);
            return null;
        }
    }

    /** Ensures a fallback sampler cannot locate structures or create persistent map data. */
    static VillagerTrade forSampling(Level level, VillagerTrade trade) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        JsonObject json = VillagerTrade.CODEC.encodeStart(ops, trade).getOrThrow().getAsJsonObject();
        if (!containsFunction(json, "minecraft:exploration_map")) return trade;
        JsonObject safe = withoutMapSearch(json).getAsJsonObject();
        JsonElement gives = safe.get("gives");
        if (gives.isJsonObject()) {
            gives.getAsJsonObject().addProperty("id", "minecraft:filled_map");
        } else {
            safe.addProperty("gives", "minecraft:filled_map");
        }
        return VillagerTrade.CODEC.parse(ops, safe).getOrThrow();
    }

    private static int[] range(JsonElement element, int fallback) {
        if (element == null) return new int[]{fallback, fallback};
        if (element.isJsonPrimitive()) {
            int n = (int) Math.floor(element.getAsDouble());
            return new int[]{n, n};
        }
        JsonObject object = element.getAsJsonObject();
        String type = object.has("type") ? object.get("type").getAsString() : "";
        if (type.equals("minecraft:constant")) return range(object.get("value"), fallback);
        if (type.equals("minecraft:uniform")) {
            int[] min = range(object.get("min"), fallback);
            int[] max = range(object.get("max"), fallback);
            return min == null || max == null ? null : new int[]{min[0], max[1]};
        }
        return null;
    }

    private static boolean isFunction(JsonElement element, String name) {
        return element.isJsonObject() && element.getAsJsonObject().has("function")
                && name.equals(element.getAsJsonObject().get("function").getAsString());
    }

    private static boolean containsFunction(JsonElement element, String name) {
        if (isFunction(element, name)) return true;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) if (containsFunction(child, name)) return true;
        } else if (element.isJsonObject()) {
            for (JsonElement child : element.getAsJsonObject().asMap().values()) {
                if (containsFunction(child, name)) return true;
            }
        }
        return false;
    }

    /** Vanilla guards discard previews lacking generated map IDs or stored enchantments. */
    private static boolean isPreviewGuard(JsonElement element, String component) {
        if (!isFunction(element, "minecraft:filtered")) return false;
        JsonObject function = element.getAsJsonObject();
        if (function.has("on_pass") || !function.has("on_fail")
                || !isFunction(function.get("on_fail"), "minecraft:discard")) return false;
        if (!function.has("item_filter") || !function.get("item_filter").isJsonObject()) return false;
        JsonObject filter = function.getAsJsonObject("item_filter");
        if (!filter.has("predicates") || !filter.get("predicates").isJsonObject()
                || !filter.getAsJsonObject("predicates").has(component)) return false;
        JsonObject predicates = filter.getAsJsonObject("predicates");
        if (predicates.size() != 1 || filter.size() != 2 || !filter.has("items")) return false;
        String expectedItem = component.equals("minecraft:map_id")
                ? "minecraft:filled_map" : "minecraft:enchanted_book";
        JsonElement items = filter.get("items");
        if (items.isJsonArray() && items.getAsJsonArray().size() == 1) items = items.getAsJsonArray().get(0);
        if (!items.isJsonPrimitive() || !items.getAsString().equals(expectedItem)) return false;
        JsonElement predicate = predicates.get(component);
        if (component.equals("minecraft:stored_enchantments")) {
            if (!predicate.isJsonArray() || predicate.getAsJsonArray().size() != 1) return false;
            predicate = predicate.getAsJsonArray().get(0);
        }
        return predicate.isJsonObject() && predicate.getAsJsonObject().size() == 0;
    }

    private static JsonElement withoutMapSearch(JsonElement element) {
        if (isFunction(element, "minecraft:exploration_map")
                || isPreviewGuard(element, "minecraft:map_id")) {
            JsonObject identity = new JsonObject();
            identity.addProperty("function", "minecraft:sequence");
            identity.add("functions", new JsonArray());
            return identity;
        }
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            element.getAsJsonObject().asMap().forEach((key, value) -> result.add(key, withoutMapSearch(value)));
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) result.add(withoutMapSearch(child));
            return result;
        }
        return element.deepCopy();
    }
}

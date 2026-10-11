package net.xuwu.jei_trade;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds a deterministic catalog from the registered datapack trades and actual merchant offers. */
public final class TradeCatalogBuilder {
    private static final int UNKNOWN_LISTING_SAMPLES = 100;
    private static final int NORMAL_MERCHANT_ENTITY_SAMPLES = 100;
    private static final int EXTENDED_MERCHANT_ENTITY_SAMPLES = 100;
    private static final int MAX_VARIANTS = 256;

    private TradeCatalogBuilder() {
    }

    /** Builds the normal catalog synchronously for explicit tooling. */
    public static List<TradeRecipe> build(Level level) {
        return buildNormal(level);
    }

    public static List<TradeRecipe> buildNormal(Level level) {
        return buildToCompletion(normalSession(level));
    }

    /** Builds the command-only catalog using real merchant entities. */
    public static List<TradeRecipe> buildReal(Level level) {
        return buildToCompletion(extendedSession(level));
    }

    private static List<TradeRecipe> buildToCompletion(Session session) {
        while (!session.advance(Long.MAX_VALUE / 2)) {
            // Explicit synchronous tooling can consume the entire session at once.
        }
        return session.snapshot();
    }

    public static Session session(Level level) {
        return normalSession(level);
    }

    /** Creates the no-cache mode with datapack definitions and bounded merchant sampling. */
    public static Session nullSamplingSession(Level level) {
        return normalSession(level);
    }

    private static Session normalSession(Level level) {
        return new Session(level, false);
    }

    /** Creates the command-only rebuild mode with the full 100-sample random discovery pass. */
    public static Session extendedSession(Level level) {
        return new Session(level, true);
    }

    /**
     * Cooperative catalog builder. Minecraft entity/world calls remain on the server thread, but a
     * call advances by only one preemptible sampling unit before checking the time budget again.
     */
    public static final class Session {
        private final Level level;
        private final boolean extendedCustomSampling;
        private final List<ListingTask> listingTasks = new ArrayList<>();
        private final List<EntityType<?>> entityTypes = new ArrayList<>();
        private final Map<String, TradeRecipe> exactEntries = new LinkedHashMap<>();
        private final Map<String, TradeAccumulator> sampledEntries = new LinkedHashMap<>();
        private int listingCursor;
        private int entityCursor;
        private MerchantTask merchantTask;
        private boolean complete;

        private Session(Level level, boolean extendedCustomSampling) {
            this.level = level;
            this.extendedCustomSampling = extendedCustomSampling;
            Registry<VillagerProfession> professions = level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION);
            Registry<TradeSet> tradeSets = level.registryAccess().lookupOrThrow(Registries.TRADE_SET);
            Identifier villagerId = Identifier.withDefaultNamespace("villager");
            for (VillagerProfession profession : professions) {
                Identifier professionId = professions.getKey(profession);
                List<Identifier> workstations = findWorkstations(level, profession);
                for (var entry : profession.tradeSetsByLevel().int2ObjectEntrySet()) {
                    TradeSet set = tradeSets.getValue(entry.getValue());
                    if (set == null) continue;
                    for (Holder<VillagerTrade> trade : set.getTrades()) {
                        listingTasks.add(new ListingTask(trade.value(), villagerId, professionId,
                                workstations, entry.getIntKey()));
                    }
                }
            }
            Identifier wanderingTraderId = Identifier.withDefaultNamespace("wandering_trader");
            for (var key : List.of(TradeSets.WANDERING_TRADER_BUYING,
                    TradeSets.WANDERING_TRADER_UNCOMMON, TradeSets.WANDERING_TRADER_COMMON)) {
                TradeSet set = tradeSets.getValue(key);
                if (set == null) continue;
                for (Holder<VillagerTrade> trade : set.getTrades()) {
                    listingTasks.add(new ListingTask(trade.value(), wanderingTraderId, null, List.of(), 0));
                }
            }

            for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
                if (entityType != EntityType.VILLAGER && entityType != EntityType.WANDERING_TRADER) {
                    entityTypes.add(entityType);
                }
            }
        }

        public boolean advance(long budgetNanos) {
            if (complete) return true;
            long deadline = budgetNanos >= Long.MAX_VALUE / 2
                    ? Long.MAX_VALUE
                    : System.nanoTime() + Math.max(1L, budgetNanos);
            boolean advanced = false;

            while (hasWork() && (!advanced || System.nanoTime() < deadline)) {
                if (listingCursor < listingTasks.size()) {
                    if (listingTasks.get(listingCursor).advance(this)) listingCursor++;
                } else {
                    if (merchantTask == null) merchantTask = new MerchantTask(entityTypes.get(entityCursor));
                    if (merchantTask.advance(this)) {
                        merchantTask = null;
                        entityCursor++;
                    }
                }
                advanced = true;
            }

            complete = !hasWork();
            return complete;
        }

        private boolean hasWork() {
            return listingCursor < listingTasks.size() || entityCursor < entityTypes.size();
        }

        private int merchantSampleLimit() {
            return extendedCustomSampling
                    ? EXTENDED_MERCHANT_ENTITY_SAMPLES
                    : NORMAL_MERCHANT_ENTITY_SAMPLES;
        }

        private void mergeExact(TradeRecipe recipe) {
            exactEntries.merge(recipe.fingerprint(), recipe, TradeRecipe::merge);
        }

        private boolean acceptOffer(Identifier entityType, Identifier profession,
                                    List<Identifier> workstations, int merchantLevel,
                                    MerchantOffer offer) {
            String key = TradeRecipe.logicalFingerprint(entityType, profession, workstations, merchantLevel,
                    offer.getCostA(), offer.getCostB(), offer.getResult());
            TradeAccumulator accumulator = sampledEntries.get(key);
            if (accumulator == null) {
                sampledEntries.put(key, new TradeAccumulator(entityType, profession, workstations,
                        merchantLevel, offer));
                return true;
            }
            return accumulator.accept(offer);
        }

        public boolean isComplete() {
            return complete;
        }

        public int processedTasks() {
            return listingCursor + entityCursor;
        }

        public int totalTasks() {
            return listingTasks.size() + entityTypes.size();
        }

        public String progressDescription() {
            if (listingCursor < listingTasks.size()) {
                ListingTask task = listingTasks.get(listingCursor);
                return "trades " + listingCursor + "/" + listingTasks.size()
                        + ", sample " + task.sampleNumber() + "/" + UNKNOWN_LISTING_SAMPLES;
            }
            int sample = merchantTask == null ? 0 : merchantTask.sampleNumber();
            return "entities " + entityCursor + "/" + entityTypes.size()
                    + (sample > 0 ? ", sample " + sample + "/" + merchantSampleLimit() : "");
        }

        public List<TradeRecipe> snapshot() {
            Map<String, TradeRecipe> result = new LinkedHashMap<>(exactEntries);
            for (TradeAccumulator accumulator : sampledEntries.values()) {
                TradeRecipe recipe = accumulator.build();
                result.merge(recipe.fingerprint(), recipe, TradeRecipe::merge);
            }
            return List.copyOf(result.values());
        }
    }

    private static final class ListingTask {
        private final VillagerTrade listing;
        private VillagerTrade samplingListing;
        private final Identifier entityType;
        private final Identifier profession;
        private final List<Identifier> workstations;
        private final int level;
        private boolean resolverChecked;
        private int sample;

        private ListingTask(VillagerTrade listing, Identifier entityType,
                            Identifier profession, List<Identifier> workstations, int level) {
            this.listing = listing;
            this.entityType = entityType;
            this.profession = profession;
            this.workstations = workstations;
            this.level = level;
        }

        private boolean advance(Session session) {
            if (!resolverChecked) {
                resolverChecked = true;
                TradeRecipe exact = TradeListingResolver.resolve(session.level, listing,
                        entityType, profession, workstations, level);
                if (exact != null) {
                    session.mergeExact(exact);
                    return true;
                }
                try {
                    samplingListing = TradeListingResolver.forSampling(session.level, listing);
                } catch (RuntimeException ex) {
                    Jei_trade.LOGGER.debug("Could not prepare trade preview {} level {}", profession, level, ex);
                    return true;
                }
            }

            try {
                ServerLevel world = (ServerLevel) session.level;
                Entity merchant = entityType.equals(Identifier.withDefaultNamespace("villager"))
                        ? EntityType.VILLAGER.create(world, EntitySpawnReason.COMMAND)
                        : EntityType.WANDERING_TRADER.create(world, EntitySpawnReason.COMMAND);
                if (merchant == null) return true;
                try {
                    merchant.setPos(world.getRespawnData().pos().getBottomCenter());
                    if (merchant instanceof Villager villager) {
                        var types = world.registryAccess().lookupOrThrow(Registries.VILLAGER_TYPE).listElements().toList();
                        var professionHolder = world.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION)
                                .get(profession).orElseThrow();
                        villager.setVillagerData(villager.getVillagerData().withProfession(professionHolder)
                                .withLevel(level).withType(types.get(sample % types.size())));
                    }
                    MerchantOffer offer = samplingListing.getOffer(context(world, merchant, sample));
                    if (offer != null) {
                        session.acceptOffer(entityType, profession, workstations, level, offer);
                    }
                } finally {
                    merchant.discard();
                }
            } catch (RuntimeException ex) {
                Jei_trade.LOGGER.debug("Could not create example trade {} level {}", profession, level, ex);
            } finally {
                sample++;
            }
            return sample >= UNKNOWN_LISTING_SAMPLES;
        }

        private int sampleNumber() {
            return sample;
        }
    }

    private static final class MerchantTask {
        private final EntityType<?> type;
        private int samples;

        private MerchantTask(EntityType<?> type) {
            this.type = type;
        }

        private boolean advance(Session session) {
            Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            Entity entity = null;
            try {
                entity = type.create(session.level, EntitySpawnReason.COMMAND);
                if (!(entity instanceof Merchant merchant)) return true;

                MerchantOffers offers = merchant.getOffers();
                Identifier professionId = null;
                List<Identifier> workstations = List.of();
                int merchantLevel = 0;
                if (merchant instanceof VillagerDataHolder holder) {
                    VillagerProfession profession = holder.getVillagerData().profession().value();
                    professionId = session.level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getKey(profession);
                    workstations = findWorkstations(session.level, profession);
                    merchantLevel = holder.getVillagerData().level();
                }

                if (offers != null) {
                    for (MerchantOffer offer : offers) {
                        session.acceptOffer(entityId, professionId, workstations, merchantLevel, offer);
                    }
                }
                samples++;
                return samples >= session.merchantSampleLimit();
            } catch (RuntimeException | LinkageError ex) {
                Jei_trade.LOGGER.debug("Could not inspect merchant entity type {}", entityId, ex);
                return true;
            } finally {
                if (entity != null) entity.discard();
            }
        }

        private int sampleNumber() {
            return samples;
        }
    }

    private static final class TradeAccumulator {
        private final Identifier entityType;
        private final Identifier profession;
        private final List<Identifier> workstations;
        private final int level;
        private final Map<String, ItemStack> buyA = new LinkedHashMap<>();
        private final Map<String, ItemStack> buyB = new LinkedHashMap<>();
        private final Map<String, ItemStack> result = new LinkedHashMap<>();
        private int buyAMin = Integer.MAX_VALUE;
        private int buyAMax;
        private int buyBMin = Integer.MAX_VALUE;
        private int buyBMax;
        private int resultMin = Integer.MAX_VALUE;
        private int resultMax;
        private int uses;
        private int maxUses;
        private int xp;
        private float priceMultiplier;
        private int demand;
        private int specialPrice;
        private boolean rewardExp;

        private TradeAccumulator(Identifier entityType, Identifier profession,
                                 List<Identifier> workstations, int level, MerchantOffer offer) {
            this.entityType = entityType;
            this.profession = profession;
            this.workstations = List.copyOf(workstations);
            this.level = level;
            this.uses = offer.getUses();
            this.xp = offer.getXp();
            this.priceMultiplier = offer.getPriceMultiplier();
            this.demand = offer.getDemand();
            this.specialPrice = offer.getSpecialPriceDiff();
            this.rewardExp = offer.shouldRewardExp();
            accept(offer);
        }

        private boolean accept(MerchantOffer offer) {
            boolean changed = addVariant(buyA, offer.getCostA());
            changed |= addVariant(buyB, offer.getCostB());
            changed |= addVariant(result, offer.getResult());
            changed |= updateRanges(offer.getCostA(), offer.getCostB(), offer.getResult());
            if (offer.getMaxUses() > maxUses) {
                maxUses = offer.getMaxUses();
                changed = true;
            }
            return changed;
        }

        private boolean updateRanges(ItemStack costA, ItemStack costB, ItemStack output) {
            int oldBuyAMin = buyAMin;
            int oldBuyAMax = buyAMax;
            int oldBuyBMin = buyBMin;
            int oldBuyBMax = buyBMax;
            int oldResultMin = resultMin;
            int oldResultMax = resultMax;
            buyAMin = Math.min(buyAMin, count(costA));
            buyAMax = Math.max(buyAMax, count(costA));
            buyBMin = Math.min(buyBMin, count(costB));
            buyBMax = Math.max(buyBMax, count(costB));
            resultMin = Math.min(resultMin, count(output));
            resultMax = Math.max(resultMax, count(output));
            return oldBuyAMin != buyAMin || oldBuyAMax != buyAMax
                    || oldBuyBMin != buyBMin || oldBuyBMax != buyBMax
                    || oldResultMin != resultMin || oldResultMax != resultMax;
        }

        private TradeRecipe build() {
            return TradeRecipe.fromSamples(entityType, profession, workstations, level,
                    List.copyOf(buyA.values()), normalizedMin(buyAMin), buyAMax,
                    List.copyOf(buyB.values()), normalizedMin(buyBMin), buyBMax,
                    List.copyOf(result.values()), normalizedMin(resultMin), resultMax,
                    uses, maxUses, xp, priceMultiplier, demand, specialPrice, rewardExp, List.of());
        }

        private static boolean addVariant(Map<String, ItemStack> variants, ItemStack stack) {
            String key = TradeRecipe.stackFingerprintIgnoringCount(stack);
            if (variants.containsKey(key) || variants.size() >= MAX_VARIANTS) return false;
            variants.put(key, stack.copy());
            return true;
        }

        private static int normalizedMin(int value) {
            return value == Integer.MAX_VALUE ? 0 : value;
        }

        private static int count(ItemStack stack) {
            return stack == null || stack.isEmpty() ? 0 : stack.getCount();
        }
    }

    static LootContext context(ServerLevel level, Entity merchant, int sample) {
        return new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, merchant.position())
                .withParameter(LootContextParams.THIS_ENTITY, merchant)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE))
                .withOptionalRandomSeed(42L + sample).create(java.util.Optional.empty());
    }

    public static List<Identifier> findWorkstations(Level level, VillagerProfession profession) {
        Registry<PoiType> poiRegistry = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE);
        Set<Identifier> ids = new LinkedHashSet<>();
        for (Map.Entry<ResourceKey<PoiType>, PoiType> entry : poiRegistry.entrySet()) {
            ResourceKey<PoiType> key = entry.getKey();
            var holderOptional = poiRegistry.get(key);
            if (holderOptional.isEmpty()) continue;
            Holder<PoiType> holder = holderOptional.get();
            if (!profession.heldJobSite().test(holder)) continue;
            for (BlockState state : entry.getValue().matchingStates()) {
                Block block = state.getBlock();
                if (!block.asItem().equals(Items.AIR)) {
                    Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
                    if (blockId != null) ids.add(blockId);
                }
            }
        }
        return List.copyOf(ids);
    }

    public static List<TradeRecipe> deduplicate(List<TradeRecipe> recipes) {
        Map<String, TradeRecipe> unique = new LinkedHashMap<>();
        for (TradeRecipe recipe : recipes) unique.merge(recipe.fingerprint(), recipe, TradeRecipe::merge);
        return new ArrayList<>(unique.values());
    }

}

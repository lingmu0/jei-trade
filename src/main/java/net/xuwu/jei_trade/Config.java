package net.xuwu.jei_trade;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Configuration for the synchronized trade catalog and client-side recipe visibility. */
public final class Config {
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_SERVER_CATALOG = SERVER_BUILDER
            .comment("Send the server's villager and merchant trade catalog to clients.")
            .define("enableServerCatalog", true);

    public static final ModConfigSpec.BooleanValue OBSERVE_MERCHANTS = SERVER_BUILDER
            .comment("Add offers from custom Merchant entities when players interact with them.")
            .define("observeMerchants", true);

    public static final ModConfigSpec.IntValue MAX_CATALOG_ENTRIES = SERVER_BUILDER
            .comment("Maximum number of trade entries sent to one client.")
            .defineInRange("maxCatalogEntries", 10000, 100, 100000);

    public static final ModConfigSpec.IntValue REBUILD_BUDGET_MICROS = SERVER_BUILDER
            .comment("Maximum main-thread time budget per server tick while the trade catalog is sampling.",
                    "Entity and trade callbacks are advanced in small server-thread slices for compatibility with threaded chunk engines.")
            .defineInRange("rebuildBudgetMicros", 1000, 100, 10000);

    public static final ModConfigSpec.BooleanValue SHOW_JER_VILLAGER_TRADES = CLIENT_BUILDER
            .comment("Show Just Enough Resources' villager trade recipe category in JEI.")
            .define("showJerVillagerTrades", false);

    public static final ModConfigSpec.BooleanValue SHOW_ADVANCED_LOOT_INFO_VILLAGER_TRADES = CLIENT_BUILDER
            .comment("Show Advanced Loot Info's villager trade recipe category in JEI.")
            .define("showAdvancedLootInfoVillagerTrades", false);

    public static final ModConfigSpec SPEC = SERVER_BUILDER.build();
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private Config() {
    }

}

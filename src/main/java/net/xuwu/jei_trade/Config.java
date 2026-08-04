package net.xuwu.jei_trade;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Small server configuration for the synchronized trade catalog. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_SERVER_CATALOG = BUILDER
            .comment("Send the server's villager and merchant trade catalog to clients.")
            .define("enableServerCatalog", true);

    public static final ModConfigSpec.BooleanValue OBSERVE_MERCHANTS = BUILDER
            .comment("Add offers from custom Merchant entities when players interact with them.")
            .define("observeMerchants", true);

    public static final ModConfigSpec.IntValue MAX_CATALOG_ENTRIES = BUILDER
            .comment("Maximum number of trade entries sent to one client.")
            .defineInRange("maxCatalogEntries", 10000, 100, 100000);

    public static final ModConfigSpec.IntValue REBUILD_BUDGET_MICROS = BUILDER
            .comment("Maximum main-thread time budget per server tick while /jeitrade rebuild is sampling.",
                    "Minecraft entity and trade callbacks are not thread-safe, so they are advanced in small tick slices.")
            .defineInRange("rebuildBudgetMicros", 1000, 100, 10000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

}

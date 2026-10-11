# JEI Trade

View villager, wandering trader, and modded merchant trades in JEI, with a server-authoritative catalog for multiplayer.

This branch targets **Minecraft 26.1.2**, **NeoForge 26.1.2.116 or newer**, and **Java 25**. Install **JEI 29.43 or newer (29.x)** on the client to view the trade category.

## Catalog and cache

The catalog reads the active datapack trade registries and samples custom merchant entities. It is synchronized to connected clients. Merchant offers encountered during play are added to the catalog.

- `/jeitrade status` reports catalog progress.
- `/jeitrade rebuild` rebuilds the catalog and saves a cache; operator permission level 2 is required.
- The cache is stored at `config/jei_trade/trade_catalog-26.1.2.bin` under the game or server directory. Newly encountered trades and variants are appended to an existing cache.
- Client configuration controls whether JER and Advanced Loot Info villager trade categories are shown alongside JEI Trade.

## Build

Run `./gradlew build` (or `gradlew.bat build` on Windows). Gradle resolves the Java 25 toolchain automatically.

See [CHANGELOG.md](CHANGELOG.md) for release notes and [NOTICE.md](NOTICE.md) for attribution.

# Changelog

## 1.0.2 - Minecraft 26.1.2 (NeoForge)

### Changed
- Ported JEI Trade to Minecraft 26.1.2 and NeoForge, using Java 25.
- Migrated villager and wandering trader discovery to the datapack trade and trade-set registries, including trades added by mods and datapacks.
- Updated the JEI integration, profession names, spawn eggs, networking, command permissions, and resource metadata for 26.1.2.
- Moved merchant sampling into short server-thread slices while keeping catalog orchestration and cache I/O in the background.
- Retained the client settings for showing or hiding JER and Advanced Loot Info villager trade categories.

### Fixed
- Newly encountered merchant trades and variants are merged into an existing cache.
- Trades observed while a cache loads or a catalog rebuild runs are preserved.
- Exploration map previews no longer locate structures or create persistent map data during datapack trade discovery.
- Quantity ranges use JEI slot overlays with the new GUI extraction API.

package net.xuwu.jei_trade;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Server-aware data provider for the JEI villager trade viewer. */
@Mod(Jei_trade.MODID)
public final class Jei_trade {
    public static final String MODID = "jei_trade";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Jei_trade(IEventBus modBus, ModContainer modContainer) {
        TradeNetworking.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}

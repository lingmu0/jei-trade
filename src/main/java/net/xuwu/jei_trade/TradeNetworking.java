package net.xuwu.jei_trade;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/** Registers and sends the NeoForge custom payload used for server-authoritative catalog sync. */
public final class TradeNetworking {
    private static final String PROTOCOL = "2";

    private TradeNetworking() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(TradeNetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToClient(
                TradeSyncPacket.TYPE,
                TradeSyncPacket.STREAM_CODEC,
                TradeSyncPacket::handle);
    }

    public static void send(ServerPlayer player, List<TradeRecipe> recipes) {
        player.connection.send(new TradeSyncPacket(recipes));
    }
}

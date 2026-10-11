package net.xuwu.jei_trade;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server-to-client payload containing the complete synchronized trade catalog. */
public final class TradeSyncPacket implements CustomPacketPayload {
    public static final Type<TradeSyncPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Jei_trade.MODID, "trade_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeSyncPacket> STREAM_CODEC =
            StreamCodec.of(TradeSyncPacket::write, TradeSyncPacket::read);

    private final List<TradeRecipe> recipes;

    public TradeSyncPacket(List<TradeRecipe> recipes) {
        this.recipes = List.copyOf(recipes);
    }

    private static void write(RegistryFriendlyByteBuf buf, TradeSyncPacket packet) {
        buf.writeVarInt(packet.recipes.size());
        packet.recipes.forEach(recipe -> recipe.write(buf));
    }

    private static TradeSyncPacket read(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), Config.MAX_CATALOG_ENTRIES.get());
        List<TradeRecipe> recipes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) recipes.add(TradeRecipe.read(buf));
        return new TradeSyncPacket(recipes);
    }

    public static void handle(TradeSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientTradeCatalog.replace(packet.recipes));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

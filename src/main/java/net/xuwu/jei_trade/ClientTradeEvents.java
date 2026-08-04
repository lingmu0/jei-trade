package net.xuwu.jei_trade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.xuwu.jei_trade.client.TradeJeiPlugin;

@EventBusSubscriber(modid = Jei_trade.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientTradeEvents {
    private ClientTradeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MerchantScreen screen) {
            MerchantMenu menu = screen.getMenu();
            ClientTradeCatalog.observeOffers(menu.getOffers(), menu.getTraderLevel());
        }
        TradeJeiPlugin.refreshRecipes();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTradeCatalog.clear();
    }
}

package com.reiko.economy.client;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.client.renderer.ShopRenderer;
import com.reiko.economy.client.screen.AdminShopSetupScreen;
import com.reiko.economy.client.screen.AdminShopTradeScreen;
import com.reiko.economy.client.screen.AtmScreen;
import com.reiko.economy.client.screen.ShopBuyScreen;
import com.reiko.economy.client.screen.ShopOwnerScreen;
import com.reiko.economy.network.ClientBalance;
import com.reiko.economy.registry.ModBlockEntities;
import com.reiko.economy.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations: screens for the shop menus and the renderer for shop blocks. FML sends each
 * listener to the right event bus based on its event type.
 */
@EventBusSubscriber(modid = ReikoEconomy.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SHOP_OWNER.get(), ShopOwnerScreen::new);
        event.register(ModMenus.SHOP_BUY.get(), ShopBuyScreen::new);
        event.register(ModMenus.ADMIN_SHOP_TRADE.get(), AdminShopTradeScreen::new);
        event.register(ModMenus.ADMIN_SHOP_SETUP.get(), AdminShopSetupScreen::new);
        event.register(ModMenus.ATM.get(), AtmScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP.get(), ShopRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ADMIN_SHOP.get(), ShopRenderer::new);
    }

    /** Forget the last world's balance when leaving it. */
    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBalance.clear();
    }
}

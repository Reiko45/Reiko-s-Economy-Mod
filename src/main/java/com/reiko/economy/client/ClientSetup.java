package com.reiko.economy.client;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.client.renderer.ShopRenderer;
import com.reiko.economy.client.screen.AdminShopSetupScreen;
import com.reiko.economy.client.screen.AdminShopTradeScreen;
import com.reiko.economy.client.screen.AtmScreen;
import com.reiko.economy.client.screen.AuctionScreen;
import com.reiko.economy.client.screen.JobBoardScreen;
import com.reiko.economy.client.screen.ShopBuyScreen;
import com.reiko.economy.client.screen.ShopOwnerScreen;
import com.reiko.economy.client.screen.TradeScreen;
import com.reiko.economy.client.screen.VillagerTradeScreen;
import com.reiko.economy.client.screen.WalletScreen;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.network.ClientBalance;
import com.reiko.economy.registry.ModBlockEntities;
import com.reiko.economy.registry.ModItems;
import com.reiko.economy.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

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
        event.register(ModMenus.WALLET.get(), WalletScreen::new);
        event.register(ModMenus.JOB_BOARD.get(), JobBoardScreen::new);
        event.register(ModMenus.AUCTION.get(), AuctionScreen::new);
        event.register(ModMenus.TRADE.get(), TradeScreen::new);
    }

    /** A wallet with cash in it shows a bill sticking out. */
    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.WALLET.get(),
                ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "filled"),
                (stack, level, entity, seed) -> Purse.contents(stack).isEmpty() ? 0.0F : 1.0F));
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP.get(), ShopRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ADMIN_SHOP.get(), ShopRenderer::new);
    }

    /** Villagers trade in money: swap vanilla's trading screen for ours, unless the server turned that off. */
    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof MerchantScreen merchant && EconomyConfig.villagersUseMoney()) {
            Minecraft minecraft = Minecraft.getInstance();
            event.setNewScreen(new VillagerTradeScreen(merchant.getMenu(), minecraft.player.getInventory(), merchant.getTitle()));
        }
    }

    /** Forget the last world's balance when leaving it. */
    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBalance.clear();
    }
}

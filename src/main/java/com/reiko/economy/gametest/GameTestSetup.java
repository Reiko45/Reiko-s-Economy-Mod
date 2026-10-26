package com.reiko.economy.gametest;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.TaxRates;
import com.reiko.economy.shop.ShopDirectory;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Prepares the game-test server: turns taxes off, so tests check exact amounts without working out tax (the
 * tax tests switch their own rates on and back off), and forgets shops left over from the last run.
 */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class GameTestSetup {
    private GameTestSetup() {
    }

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        if (event.getServer() instanceof GameTestServer) {
            EconomyConfig.overrideTaxes(TaxRates.NONE);
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer() instanceof GameTestServer) {
            ShopDirectory.get(event.getServer()).clear();
        }
    }
}

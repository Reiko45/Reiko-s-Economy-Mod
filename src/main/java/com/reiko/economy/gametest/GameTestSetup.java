package com.reiko.economy.gametest;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.TaxRates;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Turns taxes off on the game-test server, so tests check exact amounts without working out tax. The tax tests
 * switch their own rates on and back off.
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
}

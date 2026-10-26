package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.TaxService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Takes the wealth tax on schedule, and tells players who were offline what it took. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class TaxEvents {
    private static final int CHECK_EVERY_TICKS = 20;

    private TaxEvents() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY_TICKS == 0) {
            TaxService.runWealthTax(server, CreditService.now(server));
        }
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TaxService.deliver(player);
        }
    }
}

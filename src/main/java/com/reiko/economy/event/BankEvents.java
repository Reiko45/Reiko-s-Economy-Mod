package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.LoanService;
import com.reiko.economy.core.SavingsService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Pays savings interest and takes loan payments on schedule, and tells players who were offline what their
 * savings earned. (Missed and taken loan payments reach offline players through the shop notices.)
 */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class BankEvents {
    private static final int CHECK_EVERY_TICKS = 20;

    private BankEvents() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY_TICKS == 0) {
            long now = CreditService.now(server);
            SavingsService.runInterest(server, now);
            LoanService.runPayments(server, now);
        }
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SavingsService.deliver(player);
        }
    }
}

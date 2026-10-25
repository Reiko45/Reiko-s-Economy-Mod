package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.core.CreditService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs credit card statements on time and reminds players about payments when they join. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class CreditEvents {
    private static final int CHECK_EVERY_TICKS = 20;

    private CreditEvents() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY_TICKS == 0) {
            CreditService.runStatements(server, CreditService.now(server));
        }
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreditService.remind(player);
        }
    }
}

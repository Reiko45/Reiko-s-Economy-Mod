package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.command.EconomyCommands;
import com.reiko.economy.core.EconomyService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game events that run on the logical server (dedicated server, singleplayer and LAN alike). */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher());
    }

    /** Every player gets a bank account the first time they join. */
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EconomyService.INSTANCE.ensureAccount(player.server, player.getUUID(), player.getGameProfile().getName());
        }
    }
}

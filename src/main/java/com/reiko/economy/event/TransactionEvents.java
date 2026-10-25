package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.api.TransactionEvent;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.TransactionLog;
import com.reiko.economy.network.ModNetwork;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/** After every balance change: record it in the transaction log and tell affected online players. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class TransactionEvents {
    private TransactionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onTransaction(TransactionEvent.Post event) {
        MinecraftServer server = event.getServer();
        TransactionLog.get(server).add(new TransactionLog.Entry(System.currentTimeMillis(), event.getType(),
                event.getFrom(), event.getTo(), event.getAmount(), event.getReason()), EconomyConfig.logMaxEntries());
        syncBalance(server, event.getFrom());
        syncBalance(server, event.getTo());
    }

    private static void syncBalance(MinecraftServer server, @Nullable UUID player) {
        if (player == null) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            ModNetwork.sendBalance(online, EconomyService.INSTANCE.getBalance(server, player));
        }
    }
}

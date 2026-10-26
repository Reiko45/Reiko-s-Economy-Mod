package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.auction.AuctionService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Ends auctions and buy-now listings when their time is up. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class AuctionEvents {
    private static final int CHECK_EVERY_TICKS = 20;

    private AuctionEvents() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_EVERY_TICKS == 0) {
            AuctionService.tick(event.getServer());
        }
    }
}

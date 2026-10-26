package com.reiko.economy.event;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.command.EconomyPermissions;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.trade.TradeRules;
import com.reiko.economy.trade.TradeService;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Sneak + right-click on a player to ask them to trade, and the housekeeping that keeps trades safe: trades end
 * when a player leaves, dies or walks away, and requests expire.
 */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class TradeEvents {
    private static final int CHECK_EVERY_TICKS = 5;

    private TradeEvents() {
    }

    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Player target)
                || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return;
        }
        TradeRules rules = EconomyConfig.tradeRules();
        if (!rules.enabled() || !rules.sneakClick()) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && target instanceof ServerPlayer other) {
            if (!EconomyPermissions.has(serverPlayer, EconomyPermissions.TRADE)) {
                return;
            }
            ShopActionResult result = TradeService.request(serverPlayer, other);
            if (!TradeService.isSilent(result)) {
                serverPlayer.sendSystemMessage(result.success() ? result.message()
                        : result.message().copy().withStyle(ChatFormatting.RED));
            }
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TradeService.playerLeft(player); // fires before the player is saved, so their offer is saved with them
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TradeService.playerDied(player); // before the inventory drops, so the offer drops (or is kept) with it
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_EVERY_TICKS == 0) {
            TradeService.tick(event.getServer());
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        TradeService.clear();
    }
}

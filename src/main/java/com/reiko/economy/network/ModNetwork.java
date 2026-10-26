package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.client.ClientPayloadHandlers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's network payloads. Handlers run on the main game thread. */
@EventBusSubscriber(modid = ReikoEconomy.MODID)
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "2";

    private ModNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(SetShopPricesPayload.TYPE, SetShopPricesPayload.STREAM_CODEC, ServerPayloadHandlers::handleSetPrices);
        registrar.playToServer(ShopTradePayload.TYPE, ShopTradePayload.STREAM_CODEC, ServerPayloadHandlers::handleTrade);
        registrar.playToServer(AtmActionPayload.TYPE, AtmActionPayload.STREAM_CODEC, ServerPayloadHandlers::handleAtm);
        registrar.playToServer(JobActionPayload.TYPE, JobActionPayload.STREAM_CODEC, ServerPayloadHandlers::handleJob);
        registrar.playToServer(JobContractPayload.TYPE, JobContractPayload.STREAM_CODEC, ServerPayloadHandlers::handleContract);
        registrar.playToServer(WalletActionPayload.TYPE, WalletActionPayload.STREAM_CODEC, ServerPayloadHandlers::handleWallet);
        registrar.playToServer(VillagerTradePayload.TYPE, VillagerTradePayload.STREAM_CODEC, ServerPayloadHandlers::handleVillagerTrade);
        // The lambdas keep client classes from loading on a dedicated server: they are only resolved when a
        // packet actually arrives, which only happens on the client.
        registrar.playToClient(BalanceSyncPayload.TYPE, BalanceSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientBalance.set(payload.balance()));
        registrar.playToClient(ShopFeedbackPayload.TYPE, ShopFeedbackPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleFeedback(payload));
        registrar.playToClient(JobBoardSyncPayload.TYPE, JobBoardSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleJobBoard(payload));
    }

    /** Sends the player their balance, if their client has the mod. */
    public static void sendBalance(ServerPlayer player, long balance) {
        if (player.connection.hasChannel(BalanceSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new BalanceSyncPayload(balance));
        }
    }
}

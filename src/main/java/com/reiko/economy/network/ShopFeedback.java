package com.reiko.economy.network;

import com.reiko.economy.shop.ShopActionResult;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends a shop action's result to the player: into the open shop screen if possible, otherwise to chat. */
public final class ShopFeedback {
    private ShopFeedback() {
    }

    public static void send(ServerPlayer player, ShopActionResult result) {
        if (player.connection.hasChannel(ShopFeedbackPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new ShopFeedbackPayload(result.success(), result.message()));
        } else {
            player.sendSystemMessage(result.message());
        }
    }
}

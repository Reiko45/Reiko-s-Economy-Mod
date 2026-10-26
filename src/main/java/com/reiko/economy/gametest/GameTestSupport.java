package com.reiko.economy.gametest;

import com.mojang.authlib.GameProfile;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.shop.ShopActionResult;
import net.minecraft.world.item.ItemStack;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/** Helpers shared by the game tests. */
final class GameTestSupport {
    static final EconomyService ECO = EconomyService.INSTANCE;

    private GameTestSupport() {
    }

    /** A fresh account with the given balance, for a player who isn't online. */
    static UUID newAccount(MinecraftServer server, long balance) {
        UUID id = UUID.randomUUID();
        ECO.ensureAccount(server, id, "test-" + id.toString().substring(0, 8));
        ECO.setBalance(server, id, balance, "gametest.setup");
        return id;
    }

    static void expect(GameTestHelper helper, TransactionResult actual, TransactionResult expected) {
        helper.assertValueEqual(actual, expected, "transaction result");
    }

    /**
     * Logs a fake player into the server, like vanilla's {@code makeMockServerPlayerInLevel} but with a unique
     * name so commands can target each player. Joining fires the normal login event, which opens an account.
     */
    static ServerPlayer joinMockPlayer(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        return joinMockPlayer(helper, id, "eco_" + id.toString().replace("-", "").substring(0, 10));
    }

    /** Logs a fake player with a given identity into the server, for example to bring one back after leaving. */
    static ServerPlayer joinMockPlayer(GameTestHelper helper, UUID id, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /** Issues the player a debit card with the given PIN and returns it. */
    static ItemStack giveCard(GameTestHelper helper, ServerPlayer player, String pin) {
        ShopActionResult result = CardService.issue(player, pin);
        helper.assertTrue(result.success(), "issuing a card: " + result.message().getString());
        ItemStack card = DebitCardItem.findCard(player);
        helper.assertFalse(card.isEmpty(), "the card should be in the inventory");
        return card;
    }

    /** Logs the players out. Players who already left are skipped. */
    static void leave(MinecraftServer server, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                server.getPlayerList().remove(player);
            }
        }
    }
}

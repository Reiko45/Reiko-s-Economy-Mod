package com.reiko.economy.gametest;

import com.mojang.authlib.GameProfile;
import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.api.TransactionEvent;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Ledger;
import com.reiko.economy.core.Money;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the Phase 1 ledger and commands. They only load in development when game tests are
 * enabled, and run with {@code gradlew runGameTestServer} or {@code /test runall} in a dev world.
 *
 * <p>Each test uses fresh random accounts, so tests can run in parallel without touching each other.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private static final String EMPTY = "empty";
    private static final EconomyService ECO = EconomyService.INSTANCE;

    private EconomyGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void joiningCreatesAccountWithStartingBalance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            helper.assertTrue(ECO.hasAccount(server, player.getUUID()), "joining should open a bank account");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), EconomyConfig.startingBalance(), "starting balance");
            String storedName = ECO.findAccount(server, player.getUUID()).map(Ledger.Account::lastKnownName).orElse("");
            helper.assertValueEqual(storedName, player.getGameProfile().getName(), "stored name");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void transferMovesMoneyAndRejectsBadTransfers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID alice = newAccount(server, 5000);
        UUID bob = newAccount(server, 0);

        expect(helper, ECO.transfer(server, alice, bob, 2000, "gametest"), TransactionResult.SUCCESS);
        helper.assertValueEqual(ECO.getBalance(server, alice), 3000L, "alice after transfer");
        helper.assertValueEqual(ECO.getBalance(server, bob), 2000L, "bob after transfer");

        expect(helper, ECO.transfer(server, alice, bob, 3001, "gametest"), TransactionResult.INSUFFICIENT_FUNDS);
        expect(helper, ECO.transfer(server, alice, alice, 100, "gametest"), TransactionResult.SAME_ACCOUNT);
        expect(helper, ECO.transfer(server, alice, UUID.randomUUID(), 100, "gametest"), TransactionResult.NO_ACCOUNT);
        expect(helper, ECO.transfer(server, alice, bob, 0, "gametest"), TransactionResult.INVALID_AMOUNT);
        expect(helper, ECO.transfer(server, alice, bob, -5, "gametest"), TransactionResult.INVALID_AMOUNT);

        helper.assertValueEqual(ECO.getBalance(server, alice), 3000L, "failed transfers leave alice unchanged");
        helper.assertValueEqual(ECO.getBalance(server, bob), 2000L, "failed transfers leave bob unchanged");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void depositWithdrawAndSetRespectLimits(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID player = newAccount(server, 1000);

        expect(helper, ECO.deposit(server, player, 0, "gametest"), TransactionResult.INVALID_AMOUNT);
        expect(helper, ECO.deposit(server, player, Money.MAX_BALANCE, "gametest"), TransactionResult.BALANCE_LIMIT);
        expect(helper, ECO.withdraw(server, player, 1001, "gametest"), TransactionResult.INSUFFICIENT_FUNDS);
        expect(helper, ECO.setBalance(server, player, -1, "gametest"), TransactionResult.INVALID_AMOUNT);
        expect(helper, ECO.deposit(server, UUID.randomUUID(), 100, "gametest"), TransactionResult.NO_ACCOUNT);
        helper.assertValueEqual(ECO.getBalance(server, player), 1000L, "rejected changes leave the balance alone");

        expect(helper, ECO.deposit(server, player, 250, "gametest"), TransactionResult.SUCCESS);
        expect(helper, ECO.withdraw(server, player, 1250, "gametest"), TransactionResult.SUCCESS);
        helper.assertValueEqual(ECO.getBalance(server, player), 0L, "balance after deposit and full withdrawal");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cancelledEventBlocksTheChange(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID player = newAccount(server, 1000);
        Consumer<TransactionEvent.Pre> blocker = event -> {
            if ("gametest.blocked".equals(event.getReason())) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(TransactionEvent.Pre.class, blocker);
        try {
            expect(helper, ECO.deposit(server, player, 500, "gametest.blocked"), TransactionResult.CANCELLED);
            helper.assertValueEqual(ECO.getBalance(server, player), 1000L, "a cancelled deposit changes nothing");
            expect(helper, ECO.deposit(server, player, 500, "gametest.allowed"), TransactionResult.SUCCESS);
            helper.assertValueEqual(ECO.getBalance(server, player), 1500L, "other deposits still work");
        } finally {
            NeoForge.EVENT_BUS.unregister(blocker);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void ledgerSurvivesSaveAndLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID player = UUID.randomUUID();
        ECO.ensureAccount(server, player, "Saver");
        expect(helper, ECO.setBalance(server, player, 123456, "gametest"), TransactionResult.SUCCESS);

        Ledger ledger = Ledger.get(server);
        helper.assertTrue(ledger.isDirty(), "changes should mark the ledger for saving");

        CompoundTag saved = ledger.save(new CompoundTag(), server.registryAccess());
        Ledger reloaded = Ledger.load(saved, server.registryAccess());
        Ledger.Account account = reloaded.find(player).orElse(null);
        helper.assertTrue(account != null, "account should be in the reloaded ledger");
        helper.assertValueEqual(account.balance(), 123456L, "reloaded balance");
        helper.assertValueEqual(account.lastKnownName(), "Saver", "reloaded name");
        helper.assertValueEqual(reloaded.accounts().size(), ledger.accounts().size(), "reloaded account count");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void payCommandMovesMoney(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alice = joinMockPlayer(helper);
        ServerPlayer bob = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, alice.getUUID(), 10000, "gametest");
            ECO.setBalance(server, bob.getUUID(), 0, "gametest");
            String toBob = selector(bob);

            run(server, alice, "pay " + toBob + " 12.50");
            helper.assertValueEqual(ECO.getBalance(server, alice.getUUID()), 8750L, "alice after /pay");
            helper.assertValueEqual(ECO.getBalance(server, bob.getUUID()), 1250L, "bob after /pay");

            run(server, alice, "pay " + toBob + " 1000");
            run(server, alice, "pay " + toBob + " abc");
            run(server, alice, "pay " + toBob + " 0");
            run(server, alice, "pay " + selector(alice) + " 1");
            helper.assertValueEqual(ECO.getBalance(server, alice.getUUID()), 8750L, "bad /pay commands change nothing");
            helper.assertValueEqual(ECO.getBalance(server, bob.getUUID()), 1250L, "bad /pay commands give nothing");
        } finally {
            server.getPlayerList().remove(alice);
            server.getPlayerList().remove(bob);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void adminCommandsChangeBalances(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer target = joinMockPlayer(helper);
        try {
            String name = selector(target);
            ECO.setBalance(server, target.getUUID(), 0, "gametest");

            runAsConsole(server, "eco give " + name + " 5");
            helper.assertValueEqual(ECO.getBalance(server, target.getUUID()), 500L, "after /eco give");
            runAsConsole(server, "eco take " + name + " 2.5");
            helper.assertValueEqual(ECO.getBalance(server, target.getUUID()), 250L, "after /eco take");
            runAsConsole(server, "eco take " + name + " 100");
            helper.assertValueEqual(ECO.getBalance(server, target.getUUID()), 250L, "/eco take never goes negative");
            runAsConsole(server, "eco set " + name + " 42");
            helper.assertValueEqual(ECO.getBalance(server, target.getUUID()), 4200L, "after /eco set");
            runAsConsole(server, "eco reset " + name);
            helper.assertValueEqual(ECO.getBalance(server, target.getUUID()), EconomyConfig.startingBalance(), "after /eco reset");
        } finally {
            server.getPlayerList().remove(target);
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static UUID newAccount(MinecraftServer server, long balance) {
        UUID id = UUID.randomUUID();
        ECO.ensureAccount(server, id, "test-" + id.toString().substring(0, 8));
        ECO.setBalance(server, id, balance, "gametest.setup");
        return id;
    }

    private static void expect(GameTestHelper helper, TransactionResult actual, TransactionResult expected) {
        helper.assertValueEqual(actual, expected, "transaction result");
    }

    /**
     * Targets one player by name with a selector. Plain names won't work here: commands look names up in the
     * server's player-name cache, which real servers and singleplayer have but the game-test server does not.
     */
    private static String selector(ServerPlayer player) {
        return "@a[name=" + player.getGameProfile().getName() + ",limit=1]";
    }

    /** Runs a command as the player. Permission level 2 is only there so the player may use selectors. */
    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), command);
    }

    private static void runAsConsole(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    /**
     * Logs a fake player into the server, like vanilla's {@code makeMockServerPlayerInLevel} but with a unique
     * name so commands can target each player by name. Joining fires the normal login event.
     */
    private static ServerPlayer joinMockPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        String name = "eco_" + id.toString().replace("-", "").substring(0, 10);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}

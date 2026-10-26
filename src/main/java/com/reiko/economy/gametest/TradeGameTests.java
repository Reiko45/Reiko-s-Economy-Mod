package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxLedger;
import com.reiko.economy.core.TaxRates;
import com.reiko.economy.menu.TradeMenu;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.trade.TradeService;
import com.reiko.economy.trade.TradeSession;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for trading between players. They assume the default rules: 16 blocks, requests lasting 60
 * seconds and a 3 second wait after each change. The tests that accept move the trade clock past the wait.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class TradeGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final long WAIT = 3_000;
    /** Only the 2% /pay fee, which trades pay too. */
    private static final TaxRates FEE = new TaxRates(0, 200, 0, 0, 7 * 24_000L, 0, 0);

    private TradeGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void aRequestOpensTheTradeWindowForBoth(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinNear(helper, alex);
        try {
            check(helper, !TradeService.accept(sam, null).success(), "nothing to accept yet");
            ShopActionResult asked = TradeService.request(alex, sam);
            check(helper, asked.success(), "ask: " + asked.message().getString());
            check(helper, !TradeService.request(alex, sam).success(), "asking twice is refused");
            check(helper, !TradeService.request(alex, alex).success(), "no trading with yourself");

            check(helper, TradeService.accept(sam, null).success(), "accept the latest request");
            TradeSession session = TradeService.session(alex);
            check(helper, session != null && session == TradeService.session(sam), "both are in the same trade");
            check(helper, alex.containerMenu instanceof TradeMenu menu && menu.session() == session, "alex has the window");
            check(helper, sam.containerMenu instanceof TradeMenu menu && menu.session() == session, "so does sam");
            check(helper, !TradeService.request(alex, sam).success(), "can't ask while trading");

            alex.closeContainer();
            check(helper, TradeService.session(alex) == null && TradeService.session(sam) == null, "closing ends the trade");
            check(helper, sam.containerMenu == sam.inventoryMenu, "and closes the other window");
            check(helper, session.finished(), "the session is finished");
        } finally {
            leave(server, alex, sam);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void askingBackOrSneakClickingStartsTheTrade(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinNear(helper, alex);
        try {
            int asked = server.getCommands().getDispatcher().execute("trade " + select(sam), alex.createCommandSourceStack().withPermission(2));
            helper.assertValueEqual(asked, 1, "/trade sends a request");
            int back = server.getCommands().getDispatcher().execute("trade " + select(alex), sam.createCommandSourceStack().withPermission(2));
            helper.assertValueEqual(back, 1, "/trade back");
            check(helper, TradeService.session(alex) != null && TradeService.session(alex) == TradeService.session(sam),
                    "asking back accepts");
            sam.closeContainer();

            alex.setShiftKeyDown(true);
            CommonHooks.onInteractEntity(alex, sam, InteractionHand.MAIN_HAND);
            alex.setShiftKeyDown(false);
            check(helper, TradeService.session(alex) == null, "sneak-clicking only asks");
            int accepted = server.getCommands().getDispatcher().execute("trade accept " + select(alex), sam.createCommandSourceStack().withPermission(2));
            helper.assertValueEqual(accepted, 1, "/trade accept <name>");
            check(helper, TradeService.session(sam) != null, "the sneak-click request was accepted");
            sam.closeContainer();

            TradeService.request(alex, sam);
            check(helper, TradeService.deny(sam, alex).success(), "decline");
            check(helper, !TradeService.accept(sam, alex).success(), "a declined request is gone");
        } finally {
            leave(server, alex, sam);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void bothAcceptingSwapsItemsAndMoneyWithTheFee(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinNear(helper, alex);
        long[] now = {System.currentTimeMillis()};
        TradeService.setClockForTests(() -> now[0]);
        EconomyConfig.overrideTaxes(FEE);
        try {
            ECO.setBalance(server, alex.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, sam.getUUID(), 0, "gametest.setup");
            long feesBefore = TaxLedger.get(server).collected(TaxKind.TRADE);
            TradeSession session = start(helper, alex, sam);
            TradeSession.Side alexSide = session.side(alex);
            TradeSession.Side samSide = session.side(sam);

            alexSide.items().setItem(0, new ItemStack(Items.DIAMOND, 5));
            check(helper, !TradeService.offerMoney(alex, session, "99").success(), "$99 + $1.98 fee is more than alex has");
            check(helper, TradeService.offerMoney(alex, session, "50").success(), "offer $50");
            samSide.items().setItem(0, new ItemStack(Items.OAK_LOG, 32));

            check(helper, !TradeService.accept(alex, session, session.revision()).success(), "too soon after a change");
            now[0] += WAIT;
            check(helper, TradeService.accept(alex, session, session.revision()).success(), "alex accepts");
            check(helper, alexSide.accepted() && !session.finished(), "waiting for sam");

            int seen = session.revision();
            samSide.items().setItem(1, new ItemStack(Items.OAK_LOG, 1));
            check(helper, !alexSide.accepted(), "a change clears alex's accept");
            now[0] += WAIT;
            check(helper, !TradeService.accept(alex, session, seen).success(), "accepting what was seen before the change fails");
            check(helper, TradeService.accept(alex, session, session.revision()).success(), "alex accepts the new offer");
            check(helper, TradeService.accept(sam, session, session.revision()).success(), "sam accepts");

            check(helper, session.finished() && TradeService.session(alex) == null, "the trade is done");
            helper.assertValueEqual(alex.getInventory().countItem(Items.OAK_LOG), 33, "alex got the logs");
            helper.assertValueEqual(alex.getInventory().countItem(Items.DIAMOND), 0, "and gave the diamonds");
            helper.assertValueEqual(sam.getInventory().countItem(Items.DIAMOND), 5, "sam got the diamonds");
            helper.assertValueEqual(ECO.getBalance(server, sam.getUUID()), 5_000L, "sam got exactly $50");
            helper.assertValueEqual(ECO.getBalance(server, alex.getUUID()), 10_000L - 5_000 - 100, "alex paid $50 and the $1 fee");
            helper.assertValueEqual(TaxLedger.get(server).collected(TaxKind.TRADE) - feesBefore, 100L, "the fee left the economy");
            check(helper, alex.containerMenu == alex.inventoryMenu && sam.containerMenu == sam.inventoryMenu, "both windows closed");
        } finally {
            EconomyConfig.overrideTaxes(TaxRates.NONE);
            TradeService.setClockForTests(null);
            leave(server, alex, sam);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aTradeWithoutRoomOrMoneyIsRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinNear(helper, alex);
        long[] now = {System.currentTimeMillis()};
        TradeService.setClockForTests(() -> now[0]);
        try {
            sam.setGameMode(GameType.SURVIVAL);
            for (int i = 0; i < sam.getInventory().items.size(); i++) {
                sam.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            }
            ECO.setBalance(server, alex.getUUID(), 1_000, "gametest.setup");
            ECO.setBalance(server, sam.getUUID(), 0, "gametest.setup");
            TradeSession session = start(helper, alex, sam);
            check(helper, !TradeService.accept(alex, session, session.revision()).success(), "nothing offered on either side");
            session.side(alex).items().setItem(0, new ItemStack(Items.EMERALD, 3));
            now[0] += WAIT;
            check(helper, TradeService.accept(alex, session, session.revision()).success(), "alex accepts");
            ShopActionResult refused = TradeService.accept(sam, session, session.revision());
            check(helper, !refused.success(), "sam's inventory is full");
            check(helper, !session.finished() && !session.side(alex).accepted(), "the trade stays open, accepts cleared");
            helper.assertValueEqual(session.side(alex).items().getItem(0).getCount(), 3, "the emeralds are still offered");

            sam.getInventory().items.set(0, ItemStack.EMPTY);
            check(helper, TradeService.offerMoney(alex, session, "10").success(), "alex adds $10");
            ECO.setBalance(server, alex.getUUID(), 500, "gametest.spent");
            now[0] += WAIT;
            check(helper, !TradeService.accept(alex, session, session.revision()).success(), "alex can't cover $10 anymore");
            ECO.setBalance(server, alex.getUUID(), 1_000, "gametest.setup");
            check(helper, TradeService.accept(alex, session, session.revision()).success(), "with the money back he can");
            check(helper, TradeService.accept(sam, session, session.revision()).success(), "and sam now has room");
            helper.assertValueEqual(sam.getInventory().countItem(Items.EMERALD), 3, "sam got the emeralds");
            helper.assertValueEqual(ECO.getBalance(server, sam.getUUID()), 1_000L, "and the $10");
        } finally {
            TradeService.setClockForTests(null);
            leave(server, alex, sam);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cancellingLeavingOrWalkingAwayGivesItemsBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinNear(helper, alex);
        ServerPlayer kim = joinNear(helper, alex);
        try {
            TradeSession first = start(helper, alex, sam);
            first.side(alex).items().setItem(0, new ItemStack(Items.DIAMOND, 4));
            first.side(sam).items().setItem(5, new ItemStack(Items.GOLD_INGOT, 7));
            sam.containerMenu.setCarried(new ItemStack(Items.APPLE, 2));
            alex.closeContainer();
            helper.assertValueEqual(alex.getInventory().countItem(Items.DIAMOND), 4, "alex's diamonds came back");
            helper.assertValueEqual(sam.getInventory().countItem(Items.GOLD_INGOT), 7, "sam's gold came back");
            helper.assertValueEqual(sam.getInventory().countItem(Items.APPLE), 2, "and the apples on sam's cursor");

            TradeSession second = start(helper, alex, sam);
            second.side(sam).items().setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
            sam.moveTo(sam.getX() + 100, sam.getY(), sam.getZ());
            TradeService.tick(server);
            check(helper, second.finished(), "walking too far apart ends the trade");
            helper.assertValueEqual(sam.getInventory().countItem(Items.GOLD_INGOT), 14, "the second 7 gold came back too");
            check(helper, !TradeService.request(alex, sam).success(), "and sam is too far to ask");
            sam.moveTo(alex.getX(), alex.getY(), alex.getZ());

            TradeSession third = start(helper, alex, kim);
            third.side(kim).items().setItem(0, new ItemStack(Items.EMERALD, 9));
            leave(server, kim);
            check(helper, third.finished(), "leaving ends the trade");
            helper.assertValueEqual(kim.getInventory().countItem(Items.EMERALD), 9, "kim's emeralds were back before kim was saved");
            check(helper, alex.containerMenu == alex.inventoryMenu, "alex's window closed");
        } finally {
            leave(server, alex, sam, kim);
        }
        helper.succeed();
    }

    // ---- helpers ----

    /**
     * Joins a player right next to {@code other}. New players spawn at a random spot near the world spawn, which
     * can be more than the 16 blocks a trade allows.
     */
    private static ServerPlayer joinNear(GameTestHelper helper, ServerPlayer other) {
        ServerPlayer player = joinMockPlayer(helper);
        player.moveTo(other.getX(), other.getY(), other.getZ());
        return player;
    }

    private static TradeSession start(GameTestHelper helper, ServerPlayer a, ServerPlayer b) {
        ShopActionResult asked = TradeService.request(a, b);
        check(helper, asked.success(), "ask: " + asked.message().getString());
        ShopActionResult accepted = TradeService.accept(b, a);
        check(helper, accepted.success(), "accept: " + accepted.message().getString());
        TradeSession session = TradeService.session(a);
        check(helper, session != null, "the trade started");
        return session;
    }

    private static String select(ServerPlayer player) {
        return "@p[name=" + player.getGameProfile().getName() + "]";
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

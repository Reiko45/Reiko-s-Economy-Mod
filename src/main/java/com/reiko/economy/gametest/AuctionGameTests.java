package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.auction.AuctionData;
import com.reiko.economy.auction.AuctionPage;
import com.reiko.economy.auction.AuctionQuery;
import com.reiko.economy.auction.AuctionService;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxLedger;
import com.reiko.economy.shop.ShopActionResult;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the auction house. They assume the default rules: a $1.00 listing fee, a 5% cut, bids raised
 * by at least 5%, buy-now listings lasting 7 days and 10 listings per player. Each test sets the clock.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class AuctionGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    private AuctionGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void aBuyNowListingSellsWithAFeeAndACut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = joinMockPlayer(helper);
        ServerPlayer buyer = joinMockPlayer(helper);
        long now = System.currentTimeMillis();
        AuctionService.setClockForTests(now);
        try {
            ECO.setBalance(server, seller.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, buyer.getUUID(), 50_000, "gametest.setup");
            long collectedBefore = TaxLedger.get(server).collected(TaxKind.AUCTION);

            ShopActionResult listed = AuctionService.list(seller, new ItemStack(Items.DIAMOND, 16), 10_000, 0);
            check(helper, listed.success(), "list 16 diamonds for $100: " + listed.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, seller.getUUID()), 9_900L, "the $1 listing fee");
            UUID id = only(server, seller);

            check(helper, !AuctionService.buy(seller, id, 10_000).success(), "sellers can't buy their own");
            check(helper, !AuctionService.buy(buyer, id, 9_000).success(), "a changed price is caught");
            ShopActionResult bought = AuctionService.buy(buyer, id, 10_000);
            check(helper, bought.success(), "buy it: " + bought.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 40_000L, "the buyer paid $100");
            helper.assertValueEqual(ECO.getBalance(server, seller.getUUID()), 9_900L + 9_500, "the seller got $95 after the 5% cut");
            helper.assertValueEqual(buyer.getInventory().countItem(Items.DIAMOND), 16, "the diamonds are in the buyer's inventory");
            check(helper, AuctionData.get(server).find(id).isEmpty(), "the listing is gone");
            helper.assertValueEqual(TaxLedger.get(server).collected(TaxKind.AUCTION) - collectedBefore, 100L + 500, "fee and cut left the economy");
            check(helper, !AuctionService.buy(buyer, id, 10_000).success(), "it can't be bought twice");
        } finally {
            AuctionService.setClockForTests(null);
            leave(server, seller, buyer);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void bidsAreHeldRefundedWhenBeatenAndTheWinnerGetsTheItem(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = joinMockPlayer(helper);
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinMockPlayer(helper);
        long now = System.currentTimeMillis();
        AuctionService.setClockForTests(now);
        try {
            ECO.setBalance(server, seller.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, alex.getUUID(), 5_000, "gametest.setup");
            ECO.setBalance(server, sam.getUUID(), 5_000, "gametest.setup");
            check(helper, AuctionService.list(seller, new ItemStack(Items.DIAMOND_SWORD), 1_000, 24).success(), "auction a sword from $10");
            UUID id = only(server, seller);
            check(helper, !AuctionService.buy(alex, id, 1_000).success(), "an auction can't be bought outright");

            check(helper, !AuctionService.bid(alex, id, 900).success(), "below the starting bid");
            check(helper, AuctionService.bid(alex, id, 1_000).success(), "Alex bids $10");
            helper.assertValueEqual(ECO.getBalance(server, alex.getUUID()), 4_000L, "Alex's $10 is held");
            check(helper, !AuctionService.bid(alex, id, 2_000).success(), "Alex is already winning");
            check(helper, !AuctionService.bid(sam, id, 1_040).success(), "$10.40 isn't 5% more");
            check(helper, AuctionService.bid(sam, id, 1_050).success(), "Sam bids $10.50");
            helper.assertValueEqual(ECO.getBalance(server, alex.getUUID()), 5_000L, "Alex got the $10 back");
            helper.assertValueEqual(ECO.getBalance(server, sam.getUUID()), 3_950L, "Sam's $10.50 is held");

            // A bid in the last minute pushes the end back.
            AuctionData.Listing listing = AuctionData.get(server).find(id).orElseThrow();
            long late = listing.endsAt() - 30_000;
            AuctionService.setClockForTests(late);
            check(helper, AuctionService.bid(alex, id, 1_500).success(), "Alex bids $15 with 30 seconds left");
            helper.assertValueEqual(listing.endsAt(), late + 60_000, "the end moved to a minute away");

            AuctionService.setClockForTests(late + 61_000);
            AuctionService.tick(server);
            check(helper, AuctionData.get(server).find(id).isEmpty(), "the auction ended");
            helper.assertValueEqual(alex.getInventory().countItem(Items.DIAMOND_SWORD), 1, "Alex won the sword");
            helper.assertValueEqual(ECO.getBalance(server, alex.getUUID()), 3_500L, "Alex paid $15");
            helper.assertValueEqual(ECO.getBalance(server, sam.getUUID()), 5_000L, "Sam got the $10.50 back");
            helper.assertValueEqual(ECO.getBalance(server, seller.getUUID()), 10_000L - 100 + 1_500 - 75, "the seller got $15 less the 75-cent cut");
        } finally {
            AuctionService.setClockForTests(null);
            leave(server, seller, alex, sam);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void unsoldItemsComeBackOrWaitToBeCollected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = joinMockPlayer(helper);
        ServerPlayer bidder = joinMockPlayer(helper);
        long now = System.currentTimeMillis();
        AuctionService.setClockForTests(now);
        try {
            ECO.setBalance(server, seller.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, bidder.getUUID(), 10_000, "gametest.setup");
            check(helper, AuctionService.list(seller, new ItemStack(Items.EMERALD, 5), 500, 0).success(), "buy-now emeralds");
            check(helper, AuctionService.list(seller, new ItemStack(Items.GOLDEN_APPLE), 300, 1).success(), "a one-hour auction");
            UUID apple = AuctionData.get(server).listings().stream()
                    .filter(listing -> listing.seller().equals(seller.getUUID()) && listing.auction()).findFirst().orElseThrow().id();

            AuctionService.setClockForTests(now + 2 * HOUR);
            AuctionService.tick(server);
            helper.assertValueEqual(seller.getInventory().countItem(Items.GOLDEN_APPLE), 1, "the auction nobody bid on came back");
            helper.assertValueEqual(mine(server, seller), 1, "the buy-now listing is still up");

            // The seller's inventory is full when the buy-now listing runs out.
            for (int i = 0; i < seller.getInventory().items.size(); i++) {
                seller.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
            AuctionService.setClockForTests(now + 8 * DAY);
            AuctionService.tick(server);
            helper.assertValueEqual(AuctionData.get(server).claims(seller.getUUID()).size(), 1, "the emeralds wait to be collected");
            AuctionPage collect = new AuctionQuery(AuctionQuery.COLLECT, "", 0, 1).run(server, seller.getUUID());
            helper.assertValueEqual(collect.waiting(), 1, "the Collect tab shows it");
            check(helper, !AuctionService.collect(seller).success(), "still no room");
            seller.getInventory().items.set(0, ItemStack.EMPTY);
            check(helper, AuctionService.collect(seller).success(), "collect it");
            helper.assertValueEqual(seller.getInventory().countItem(Items.EMERALD), 5, "the emeralds are back");
            check(helper, AuctionData.get(server).claims(seller.getUUID()).isEmpty(), "nothing left waiting");
            check(helper, !AuctionService.cancel(seller, apple).success(), "an ended listing can't be taken down");
        } finally {
            AuctionService.setClockForTests(null);
            leave(server, seller, bidder);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void listingsCanBeTakenDownUnlessSomeoneBid(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = joinMockPlayer(helper);
        ServerPlayer bidder = joinMockPlayer(helper);
        AuctionService.setClockForTests(System.currentTimeMillis());
        try {
            ECO.setBalance(server, seller.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, bidder.getUUID(), 10_000, "gametest.setup");

            // /ah sell lists what's in your hand.
            seller.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK, 3));
            int result = server.getCommands().getDispatcher().execute("ah sell 12.50", seller.createCommandSourceStack());
            helper.assertValueEqual(result, 1, "/ah sell worked");
            check(helper, seller.getMainHandItem().isEmpty(), "the books left the hand");
            UUID books = only(server, seller);
            helper.assertValueEqual(AuctionData.get(server).find(books).orElseThrow().price(), 1_250L, "for $12.50");
            check(helper, AuctionService.cancel(seller, books).success(), "take it down");
            helper.assertValueEqual(seller.getInventory().countItem(Items.BOOK), 3, "the books came back");
            check(helper, !AuctionService.cancel(bidder, books).success(), "it's gone");

            check(helper, AuctionService.list(seller, new ItemStack(Items.SADDLE), 500, 6).success(), "auction a saddle");
            UUID saddle = only(server, seller);
            check(helper, !AuctionService.cancel(bidder, saddle).success(), "only the seller can take it down");
            check(helper, AuctionService.bid(bidder, saddle, 500).success(), "someone bids");
            check(helper, !AuctionService.cancel(seller, saddle).success(), "not once someone has bid");

            AuctionPage mine = new AuctionQuery(AuctionQuery.MINE, "", 0, 1).run(server, seller.getUUID());
            helper.assertValueEqual(mine.total(), 1, "My listings shows the saddle");
            AuctionPage found = new AuctionQuery(AuctionQuery.BROWSE, "sadd", 0, 1).run(server, bidder.getUUID());
            check(helper, found.rows().stream().anyMatch(row -> saddle.equals(row.id()) && row.leading()), "search finds it, and the bidder is winning");

            for (int i = 0; i < 9; i++) {
                check(helper, AuctionService.list(seller, new ItemStack(Items.STICK), 1, 0).success(), "listing " + (i + 2));
            }
            check(helper, !AuctionService.list(seller, new ItemStack(Items.STICK), 1, 0).success(), "the 11th is over the limit");
        } finally {
            AuctionService.removeAll(server, seller.getUUID());
            AuctionService.setClockForTests(null);
            leave(server, seller, bidder);
        }
        helper.succeed();
    }

    // ---- helpers ----

    /** The player's only listing. */
    private static UUID only(MinecraftServer server, ServerPlayer seller) {
        return AuctionData.get(server).listings().stream().filter(listing -> listing.seller().equals(seller.getUUID()))
                .reduce((a, b) -> {
                    throw new IllegalStateException("more than one listing");
                }).orElseThrow().id();
    }

    private static int mine(MinecraftServer server, ServerPlayer seller) {
        return (int) AuctionData.get(server).listings().stream().filter(listing -> listing.seller().equals(seller.getUUID())).count();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

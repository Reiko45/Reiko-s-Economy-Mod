package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.AtmService;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.Denomination;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.DebitCard;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.registry.ModItems;
import com.reiko.economy.shop.Payment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** In-game tests for debit cards and paying shops by card or cash. */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class CardGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SHOP_POS = new BlockPos(1, 1, 1);

    private CardGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void firstCardIsFreeAndANewOneCancelsTheOld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 1000, "gametest.setup");
            ItemStack first = giveCard(helper, player, "1234").copy();
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 1000L, "first card is free");
            DebitCard data = DebitCardItem.cardOf(first);
            helper.assertValueEqual(data.account(), player.getUUID(), "card draws from the player's account");

            player.getInventory().clearContent();
            ItemStack second = giveCard(helper, player, "5678");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 500L, "a second card costs 5.00");
            check(helper, !CardService.authorize(server, first, "1234").ok(), "the old card is cancelled");
            check(helper, CardService.authorize(server, second, "5678").ok(), "the new card works with its PIN");
            check(helper, !CardService.authorize(server, second, "1234").ok(), "the old PIN doesn't work on the new card");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cardRequiresAValidPinRoomAndTheFee(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            for (String bad : new String[] {"", "12", "12345", "abcd", "12a4"}) {
                check(helper, !CardService.issue(player, bad).success(), "PIN '" + bad + "' is rejected");
            }
            check(helper, DebitCardItem.findCard(player).isEmpty(), "no card was handed out");

            ECO.setBalance(server, player.getUUID(), 0, "gametest.setup");
            giveCard(helper, player, "1111");
            check(helper, !CardService.issue(player, "2222").success(), "can't get a second card without the fee");

            for (int i = 0; i < player.getInventory().items.size(); i++) {
                player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
            ECO.setBalance(server, player.getUUID(), 1000, "gametest.setup");
            check(helper, !CardService.issue(player, "3333").success(), "a full inventory refuses a card");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 1000L, "no fee taken when refused");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void threeWrongPinsLockTheCard(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 1000, "gametest.setup");
            ItemStack card = giveCard(helper, player, "4242");
            check(helper, !CardService.authorize(server, card, "0000").ok(), "wrong PIN 1");
            check(helper, CardService.authorize(server, card, "4242").ok(), "a right PIN resets the count");
            check(helper, !CardService.authorize(server, card, "0000").ok(), "wrong PIN 1 again");
            check(helper, !CardService.authorize(server, card, "1111").ok(), "wrong PIN 2");
            check(helper, !CardService.authorize(server, card, "2222").ok(), "wrong PIN 3 locks it");
            check(helper, !CardService.authorize(server, card, "4242").ok(), "a locked card refuses even the right PIN");
            check(helper, !AtmService.withdraw(player, card, "4242", "5").success(), "a locked card can't withdraw");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 1000L, "nothing withdrawn");

            player.getInventory().clearContent();
            ItemStack replacement = giveCard(helper, player, "4242");
            check(helper, CardService.authorize(server, replacement, "4242").ok(), "a new card works again");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aLentCardPaysFromTheOwnersAccount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer cardOwner = joinMockPlayer(helper);
        ServerPlayer friend = joinMockPlayer(helper);
        ServerPlayer shopOwner = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, cardOwner.getUUID(), 5000, "gametest.setup");
            ECO.setBalance(server, friend.getUUID(), 0, "gametest.setup");
            ECO.setBalance(server, shopOwner.getUUID(), 0, "gametest.setup");
            ItemStack card = giveCard(helper, cardOwner, "7777");
            friend.getInventory().add(card.copy());
            cardOwner.getInventory().clearContent();

            ShopBlockEntity shop = placeShop(helper, shopOwner, new ItemStack(Items.IRON_INGOT, 4), "8", 16);
            check(helper, !shop.sellTo(friend, 1, 800, Payment.card("0000")).success(), "a wrong PIN buys nothing");
            check(helper, shop.sellTo(friend, 2, 800, Payment.card("7777")).success(), "the friend buys with the lent card");
            helper.assertValueEqual(ECO.getBalance(server, cardOwner.getUUID()), 3400L, "the card's owner paid 16.00");
            helper.assertValueEqual(ECO.getBalance(server, friend.getUUID()), 0L, "the friend's own account is untouched");
            helper.assertValueEqual(ECO.getBalance(server, shopOwner.getUUID()), 1600L, "the shop owner got 16.00");
            helper.assertValueEqual(friend.getInventory().countItem(Items.IRON_INGOT), 8, "the friend got the iron");

            ItemStack lent = DebitCardItem.findCard(friend);
            check(helper, AtmService.withdraw(friend, lent, "7777", "20").success(), "the friend withdraws with it");
            helper.assertValueEqual(ECO.getBalance(server, cardOwner.getUUID()), 1400L, "from the card owner's account");
            helper.assertValueEqual(cashIn(friend), 2000L, "the cash goes to the friend");
        } finally {
            leave(server, cardOwner, friend, shopOwner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cashPaymentGivesChange(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        ServerPlayer shopOwner = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, buyer.getUUID(), 1000, "gametest.setup");
            ECO.setBalance(server, shopOwner.getUUID(), 0, "gametest.setup");
            buyer.getInventory().add(new ItemStack(ModItems.cash(Denomination.TWENTY), 1));
            buyer.getInventory().add(new ItemStack(ModItems.cash(Denomination.QUARTER), 3));

            ShopBlockEntity shop = placeShop(helper, shopOwner, new ItemStack(Items.BREAD, 3), "4.50", 9);
            check(helper, shop.sellTo(buyer, 1, 450, Payment.cash()).success(), "pay 4.50 in cash");
            helper.assertValueEqual(cashIn(buyer), 2075L - 450L, "20.75 - 4.50 left in cash");
            // The three quarters go first, then the $20 covers the rest: 16.25 change = $10 + $5 + $1 + 25¢.
            helper.assertValueEqual(buyer.getInventory().countItem(ModItems.cash(Denomination.TWENTY)), 0, "the $20 was handed over");
            helper.assertValueEqual(buyer.getInventory().countItem(ModItems.cash(Denomination.TEN)), 1, "change: one $10");
            helper.assertValueEqual(buyer.getInventory().countItem(ModItems.cash(Denomination.FIVE)), 1, "change: one $5");
            helper.assertValueEqual(buyer.getInventory().countItem(ModItems.cash(Denomination.ONE)), 1, "change: one $1");
            helper.assertValueEqual(buyer.getInventory().countItem(ModItems.cash(Denomination.QUARTER)), 1, "change: one quarter");
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 1000L, "paying cash leaves the balance alone");
            helper.assertValueEqual(ECO.getBalance(server, shopOwner.getUUID()), 450L, "the owner is credited 4.50");
            helper.assertValueEqual(buyer.getInventory().countItem(Items.BREAD), 3, "got the bread");

            check(helper, !shop.sellTo(buyer, 5, 450, Payment.cash()).success(), "22.50 is more cash than they have");
            helper.assertValueEqual(cashIn(buyer), 1625L, "a refused cash payment takes nothing");
            check(helper, !shop.sellTo(buyer, 1, 450, Payment.card("1234")).success(), "no card, no card payment");
        } finally {
            leave(server, buyer, shopOwner);
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static ShopBlockEntity placeShop(GameTestHelper helper, ServerPlayer owner, ItemStack bundle, String price, int stock) {
        helper.setBlock(SHOP_POS, ModBlocks.SHOP.get());
        ShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
        shop.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        check(helper, shop.trySetTemplate(bundle).success(), "set item");
        check(helper, shop.trySetPrice(price).success(), "set price");
        ItemHandlerHelper.insertItem(shop.stock(), bundle.copyWithCount(stock * bundle.getCount() > 64 ? 64 : stock * bundle.getCount()), false);
        return shop;
    }

    private static long cashIn(ServerPlayer player) {
        long total = 0;
        for (ItemStack stack : player.getInventory().items) {
            total += CashItem.valueOf(stack);
        }
        return total;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

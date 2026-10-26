package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.core.CreditLedger;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.Cards;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * In-game tests for credit cards. They assume the default credit settings: a $500 starting limit, statements
 * with 5% interest, a 10% or $10 minimum, a $25 late fee, $100 limit steps and freezing after 3 misses.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class CreditGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SHOP_POS = new BlockPos(1, 1, 1);

    private CreditGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void aCreditCardStartsWithTheLimitAndReplacingItGoesOnTheBill(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 1000, "gametest.setup");
            ItemStack first = giveCreditCard(helper, player, "1234").copy();
            check(helper, Cards.isCredit(first), "it's a credit card");
            helper.assertValueEqual(account(server, player).limit(), 50_000L, "starts with a $500 limit");
            helper.assertValueEqual(account(server, player).balance(), 0L, "the first one is free");

            player.getInventory().clearContent();
            ItemStack second = giveCreditCard(helper, player, "5678");
            helper.assertValueEqual(account(server, player).balance(), 500L, "the $5 replacement fee goes on the bill");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 1000L, "not out of the bank");
            check(helper, !CreditService.authorize(server, first, "1234").ok(), "the old card is cancelled");
            check(helper, CreditService.authorize(server, second, "5678").ok(), "the new card works");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void buyingOnCreditPaysTheShopOwnerAndAddsToTheBill(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, buyer.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, owner.getUUID(), 0, "gametest.setup");
            UUID debit = Cards.data(giveCard(helper, buyer, "1111")).cardId();
            UUID credit = Cards.data(giveCreditCard(helper, buyer, "2222")).cardId();
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.IRON_INGOT, 4), "50");

            ShopActionResult result = shop.sellTo(buyer, 4, 5_000, Payment.card("2222", credit));
            check(helper, result.success(), "buy $200 on credit: " + result.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 20_000L, "the owner is paid now");
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 10_000L, "the buyer's bank isn't touched");
            helper.assertValueEqual(account(server, buyer).balance(), 20_000L, "the buyer owes $200 on the card");

            check(helper, shop.sellTo(buyer, 1, 5_000, Payment.card("1111", debit)).success(), "the debit card still pays from the bank");
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 5_000L, "$50 from the bank");
            helper.assertValueEqual(account(server, buyer).balance(), 20_000L, "and nothing more on credit");

            check(helper, !shop.sellTo(buyer, 7, 5_000, Payment.card("2222", credit)).success(), "$350 more is over the $500 limit");
            check(helper, !shop.sellTo(buyer, 1, 5_000, Payment.card("1111", credit)).success(), "the debit PIN doesn't work on the credit card");
            helper.assertValueEqual(account(server, buyer).balance(), 20_000L, "refused purchases add nothing");
        } finally {
            leave(server, buyer, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void payingTheStatementInFullAvoidsInterestAndRaisesTheLimit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, buyer.getUUID(), 50_000, "gametest.setup");
            ItemStack card = giveCreditCard(helper, buyer, "3333");
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.IRON_INGOT, 4), "50");
            check(helper, shop.sellTo(buyer, 4, 5_000, Payment.card("3333")).success(), "buy $200 on credit");

            CreditService.statementNow(server, buyer.getUUID());
            helper.assertValueEqual(account(server, buyer).state().statementBalance(), 20_000L, "the statement shows $200");
            helper.assertValueEqual(account(server, buyer).state().minimumDue(), 2_000L, "minimum 10%");

            ShopActionResult paid = CreditService.payFromBank(buyer, card, "3333", "");
            check(helper, paid.success(), "pay it all from the bank: " + paid.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 30_000L, "$200 came out of the bank");
            helper.assertValueEqual(account(server, buyer).balance(), 0L, "nothing owed");

            CreditService.statementNow(server, buyer.getUUID());
            helper.assertValueEqual(account(server, buyer).balance(), 0L, "no interest");
            helper.assertValueEqual(account(server, buyer).limit(), 60_000L, "the limit grew by $100");
        } finally {
            leave(server, buyer, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void missedPaymentsUseTheBankThenAddFeesAndFreezeTheCard(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ItemStack card = giveCreditCard(helper, buyer, "4444");
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.IRON_INGOT, 4), "50");
            check(helper, shop.sellTo(buyer, 4, 5_000, Payment.card("4444")).success(), "buy $200 on credit");
            CreditService.statementNow(server, buyer.getUUID());

            // Nothing paid, but the bank covers the $20 minimum: interest on the other $180, no late fee.
            ECO.setBalance(server, buyer.getUUID(), 10_000, "gametest.setup");
            CreditService.statementNow(server, buyer.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 8_000L, "the bank paid the $20 minimum");
            helper.assertValueEqual(account(server, buyer).balance(), 18_900L, "$180 left plus $9 interest");
            helper.assertValueEqual(account(server, buyer).limit(), 50_000L, "autopay doesn't raise the limit");

            // Now the bank is empty: three misses in a row.
            ECO.setBalance(server, buyer.getUUID(), 0, "gametest.setup");
            CreditService.statementNow(server, buyer.getUUID());
            helper.assertValueEqual(account(server, buyer).balance(), 18_900L + 945 + 2_500, "interest and a $25 late fee");
            helper.assertValueEqual(account(server, buyer).limit(), 40_000L, "the limit dropped by $100");
            check(helper, !account(server, buyer).frozen(), "one miss doesn't freeze it");
            CreditService.statementNow(server, buyer.getUUID());
            CreditService.statementNow(server, buyer.getUUID());
            check(helper, account(server, buyer).frozen(), "three misses freeze it");
            check(helper, !shop.sellTo(buyer, 1, 5_000, Payment.card("4444")).success(), "a frozen card can't buy");

            long owed = account(server, buyer).balance();
            giveCash(buyer, owed + 1_000);
            ShopActionResult paid = CreditService.payWithAllCash(buyer, card, "4444");
            check(helper, paid.success(), "pay it off in cash: " + paid.message().getString());
            helper.assertValueEqual(account(server, buyer).balance(), 0L, "paid off");
            helper.assertValueEqual(cashIn(buyer), 1_000L, "only what was owed was taken; the rest came back as change");
            check(helper, !account(server, buyer).frozen(), "paying it off unfreezes the card");
            check(helper, shop.sellTo(buyer, 1, 5_000, Payment.card("4444")).success(), "and it works again");
        } finally {
            leave(server, buyer, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aCashAdvanceAddsAFee(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ItemStack card = giveCreditCard(helper, player, "5555");
            ShopActionResult result = CreditService.cashAdvance(player, card, "5555", "100");
            check(helper, result.success(), "advance $100: " + result.message().getString());
            helper.assertValueEqual(cashIn(player), 10_000L, "got $100 in cash");
            helper.assertValueEqual(account(server, player).balance(), 10_500L, "owes $100 plus the $5 fee");

            check(helper, !CreditService.cashAdvance(player, card, "5555", "390").success(), "$390 plus its fee is over what's left");
            helper.assertValueEqual(account(server, player).balance(), 10_500L, "nothing added");
            check(helper, CreditService.cashAdvance(player, card, "5555", "20").success(), "a small advance still fits");
            helper.assertValueEqual(account(server, player).balance(), 10_500L + 2_000 + 500, "the $5 minimum fee");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theBillListsEveryChargeAndPayment(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, buyer.getUUID(), 50_000, "gametest.setup");
            ItemStack card = giveCreditCard(helper, buyer, "6666");
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.IRON_INGOT, 4), "50");
            check(helper, shop.sellTo(buyer, 2, 5_000, Payment.card("6666")).success(), "buy $100 on credit");
            check(helper, CreditService.cashAdvance(buyer, card, "6666", "20").success(), "a $20 cash advance");
            CreditService.statementNow(server, buyer.getUUID());
            check(helper, CreditService.payFromBank(buyer, card, "6666", "").success(), "pay it off");

            List<CreditLedger.HistoryEntry> history = account(server, buyer).history();
            List<String> keys = history.stream().map(entry -> entry.description().getContents() instanceof TranslatableContents contents
                    ? contents.getKey() : "").toList();
            helper.assertValueEqual(keys, List.of(
                    "credit.reikoeconomy.history.bank_payment",
                    "credit.reikoeconomy.history.statement",
                    "credit.reikoeconomy.history.advance_fee",
                    "credit.reikoeconomy.history.advance",
                    "credit.reikoeconomy.history.shop"), "newest first");
            helper.assertValueEqual(history.get(0).amount(), -12_500L, "the payment is shown as money off");
            helper.assertValueEqual(history.get(4).amount(), 10_000L, "the shop purchase");
            helper.assertValueEqual(history.get(3).amount(), 2_000L, "the advance");
            helper.assertValueEqual(history.get(2).amount(), 500L, "its fee");
        } finally {
            leave(server, buyer, owner);
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static ItemStack giveCreditCard(GameTestHelper helper, ServerPlayer player, String pin) {
        ShopActionResult result = CreditService.issue(player, pin);
        helper.assertTrue(result.success(), "issuing a credit card: " + result.message().getString());
        UUID current = CreditLedger.get(player.server).find(player.getUUID()).orElseThrow().cardId();
        for (ItemStack stack : player.getInventory().items) {
            if (Cards.isCredit(stack) && current.equals(Cards.data(stack).cardId())) {
                return stack;
            }
        }
        throw new IllegalStateException("the credit card should be in the inventory");
    }

    private static CreditLedger.Account account(MinecraftServer server, ServerPlayer player) {
        return CreditLedger.get(server).find(player.getUUID()).orElseThrow();
    }

    private static ShopBlockEntity placeShop(GameTestHelper helper, ServerPlayer owner, ItemStack bundle, String price) {
        helper.setBlock(SHOP_POS, ModBlocks.SHOP.get());
        ShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
        shop.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        check(helper, shop.trySetTemplate(bundle).success(), "set item");
        check(helper, shop.trySetPrice(price).success(), "set price");
        for (int i = 0; i < 4; i++) {
            ItemHandlerHelper.insertItem(shop.stock(), bundle.copyWithCount(64), false);
        }
        return shop;
    }

    private static void giveCash(ServerPlayer player, long amount) {
        for (ItemStack stack : CashItem.makeChange(amount)) {
            player.getInventory().add(stack);
        }
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

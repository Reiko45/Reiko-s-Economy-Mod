package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;
import static com.reiko.economy.gametest.GameTestSupport.newAccount;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.AtmService;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.CreditLedger;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxLedger;
import com.reiko.economy.core.TaxRates;
import com.reiko.economy.core.TaxService;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.Cards;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 * In-game tests for taxes and fees. Taxes are off on the game-test server (see {@link GameTestSetup}); each test
 * here turns on {@link #RATES} for itself and back off when it's done.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class TaxGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SHOP_POS = new BlockPos(1, 1, 1);
    /** 5% sales tax, 2% /pay fee, 1% wealth tax over $10,000 weekly, $1 ATM fee. */
    private static final TaxRates RATES = new TaxRates(500, 200, 100, 1_000_000, 7 * 24_000L, 100, 0);

    private TaxGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void salesTaxIsAddedOnTopAtPlayerShops(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        EconomyConfig.overrideTaxes(RATES);
        try {
            ECO.setBalance(server, buyer.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, owner.getUUID(), 0, "gametest.setup");
            UUID debit = Cards.data(giveCard(helper, buyer, "1111")).cardId();
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.DIAMOND, 1), "10");
            long collectedBefore = TaxLedger.get(server).collected(TaxKind.SALES);

            ShopActionResult byCard = shop.sellTo(buyer, 1, 1_000, Payment.card("1111", debit));
            check(helper, byCard.success(), "buy by debit card: " + byCard.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 10_000L - 1_050, "$10 plus 50 cents tax");
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 1_000L, "the owner gets the full $10");

            giveCash(buyer, 2_000);
            check(helper, shop.sellTo(buyer, 1, 1_000, Payment.cash()).success(), "buy with cash");
            helper.assertValueEqual(Purse.value(buyer), 950L, "$20 - $10.50 = $9.50 change");
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 2_000L, "the owner got $10 again");

            ItemStack credit = giveCreditCard(helper, buyer, "2222");
            check(helper, shop.sellTo(buyer, 2, 1_000, Payment.card("2222", Cards.data(credit).cardId())).success(), "buy on credit");
            CreditLedger.Account account = CreditLedger.get(server).find(buyer.getUUID()).orElseThrow();
            helper.assertValueEqual(account.balance(), 2_100L, "$20 plus $1 tax on the bill");
            check(helper, account.history().get(0).description().getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("credit.reikoeconomy.history.sales_tax") && account.history().get(0).amount() == 100,
                    "the tax is its own line on the bill");

            ECO.setBalance(server, buyer.getUUID(), 1_049, "gametest.setup");
            check(helper, !shop.sellTo(buyer, 1, 1_000, Payment.card("1111", debit)).success(), "$10.49 can't cover $10.50");
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 1_049L, "nothing was taken");
            helper.assertValueEqual(TaxLedger.get(server).collected(TaxKind.SALES) - collectedBefore, 50L + 50 + 100, "all the tax is counted");
        } finally {
            EconomyConfig.overrideTaxes(TaxRates.NONE);
            leave(server, buyer, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void salesTaxIsAddedAtTheServerShop(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        EconomyConfig.overrideTaxes(RATES);
        try {
            ECO.setBalance(server, buyer.getUUID(), 1_000, "gametest.setup");
            ItemStack card = giveCard(helper, buyer, "3333");
            helper.setBlock(SHOP_POS, ModBlocks.ADMIN_SHOP.get());
            AdminShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
            check(helper, shop.trySetTemplate(new ItemStack(Items.BREAD, 4)).success(), "set item");
            check(helper, shop.trySetPrices("4", "2").success(), "set prices");

            check(helper, shop.sellTo(buyer, 1, 400, Payment.card("3333", Cards.data(card).cardId())).success(), "buy");
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 1_000L - 420, "$4 plus 20 cents tax");
            check(helper, shop.buyFrom(buyer, 1, 200).success(), "sell back");
            helper.assertValueEqual(Purse.value(buyer), 200L, "selling isn't taxed");
        } finally {
            EconomyConfig.overrideTaxes(TaxRates.NONE);
            leave(server, buyer);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theSenderPaysAFeeOnPay(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer sender = joinMockPlayer(helper);
        ServerPlayer recipient = joinMockPlayer(helper);
        EconomyConfig.overrideTaxes(RATES);
        try {
            ECO.setBalance(server, sender.getUUID(), 20_000, "gametest.setup");
            ECO.setBalance(server, recipient.getUUID(), 0, "gametest.setup");
            // The game-test server has no profile cache to look names up in, so name the recipient with a selector.
            String target = "@a[name=" + recipient.getGameProfile().getName() + "]";
            var source = sender.createCommandSourceStack().withPermission(2);

            int sent = server.getCommands().getDispatcher().execute("pay " + target + " 100", source);
            helper.assertValueEqual(sent, 1, "the payment went through");
            helper.assertValueEqual(ECO.getBalance(server, recipient.getUUID()), 10_000L, "the recipient gets exactly $100");
            helper.assertValueEqual(ECO.getBalance(server, sender.getUUID()), 20_000L - 10_200, "the sender pays $100 plus $2");

            int refused = server.getCommands().getDispatcher().execute("pay " + target + " 98", source);
            helper.assertValueEqual(refused, 0, "$98 needs $99.96 with the fee, and only $98 is left");
            helper.assertValueEqual(ECO.getBalance(server, sender.getUUID()), 9_800L, "nothing moved");
        } finally {
            EconomyConfig.overrideTaxes(TaxRates.NONE);
            leave(server, sender, recipient);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void atmWithdrawalsCostAFee(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        EconomyConfig.overrideTaxes(RATES);
        try {
            ECO.setBalance(server, player.getUUID(), 5_000, "gametest.setup");
            ItemStack card = giveCard(helper, player, "4444");
            ShopActionResult result = AtmService.withdraw(player, card, "4444", "20");
            check(helper, result.success(), "withdraw $20: " + result.message().getString());
            helper.assertValueEqual(Purse.value(player), 2_000L, "got $20 in cash");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 5_000L - 2_100, "$20 plus the $1 fee");

            check(helper, !AtmService.withdraw(player, card, "4444", "29.50").success(), "$29.50 plus $1 is more than $29");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 2_900L, "nothing taken");
            helper.assertValueEqual(Purse.value(player), 2_000L, "no cash given");
        } finally {
            EconomyConfig.overrideTaxes(TaxRates.NONE);
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theWealthTaxTakesFromBigBalancesOnSchedule(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer rich = joinMockPlayer(helper);
        UUID away = newAccount(server, 5_000_000); // $50,000, offline
        UUID poor = newAccount(server, 500_000); // $5,000, under the threshold
        EconomyConfig.overrideTaxes(RATES);
        try {
            ECO.setBalance(server, rich.getUUID(), 2_000_000, "gametest.setup");
            TaxLedger taxes = TaxLedger.get(server);
            TaxService.runWealthTax(server, CreditService.now(server)); // schedules the first one if needed
            long due = taxes.nextWealthTax();

            TaxService.runWealthTax(server, due - 1);
            helper.assertValueEqual(ECO.getBalance(server, rich.getUUID()), 2_000_000L, "not due yet");

            TaxService.runWealthTax(server, due);
            helper.assertValueEqual(ECO.getBalance(server, rich.getUUID()), 2_000_000L - 10_000, "1% of the $10,000 over");
            helper.assertValueEqual(ECO.getBalance(server, away), 5_000_000L - 40_000, "1% of the $40,000 over");
            helper.assertValueEqual(ECO.getBalance(server, poor), 500_000L, "under the threshold pays nothing");
            helper.assertValueEqual(taxes.nextWealthTax(), due + RATES.wealthPeriodTicks(), "the next one is a week later");
            helper.assertValueEqual(taxes.unseenWealthTax(away), 40_000L, "the offline player will hear about it");

            ServerPlayer back = joinMockPlayer(helper, away, "back_" + away.toString().substring(0, 8));
            helper.assertValueEqual(taxes.unseenWealthTax(away), 0L, "and did when they joined");
            leave(server, back);

            helper.assertValueEqual(server.getCommands().getDispatcher().execute("eco taxes", server.createCommandSourceStack()), 1,
                    "/eco taxes runs");
            helper.assertValueEqual(server.getCommands().getDispatcher().execute("taxes", rich.createCommandSourceStack()), 1,
                    "/taxes runs");
        } finally {
            EconomyConfig.overrideTaxes(TaxRates.NONE);
            leave(server, rich);
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static ShopBlockEntity placeShop(GameTestHelper helper, ServerPlayer owner, ItemStack bundle, String price) {
        helper.setBlock(SHOP_POS, ModBlocks.SHOP.get());
        ShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
        shop.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        check(helper, shop.trySetTemplate(bundle).success(), "set item");
        check(helper, shop.trySetPrice(price).success(), "set price");
        ItemHandlerHelper.insertItem(shop.stock(), bundle.copyWithCount(64), false);
        return shop;
    }

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

    private static void giveCash(ServerPlayer player, long amount) {
        for (ItemStack stack : CashItem.makeChange(amount)) {
            player.getInventory().add(stack);
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

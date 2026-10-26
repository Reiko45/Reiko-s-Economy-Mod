package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.AtmService;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.Ledger;
import com.reiko.economy.core.Money;
import com.reiko.economy.core.SharedAccountService;
import com.reiko.economy.core.SharedAccounts;
import com.reiko.economy.core.TransactionLog;
import com.reiko.economy.item.BankCard;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.network.AtmActionPayload;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import java.util.UUID;
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

/**
 * In-game tests for shared accounts. Each test makes accounts with fresh names, since the test world (and so the
 * list of shared accounts) is kept between runs.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class SharedAccountGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SHOP_POS = new BlockPos(1, 1, 1);
    private static final String PIN = "4321";

    private SharedAccountGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void membersShareTheMoneyAndTheOwnerManagesIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinMockPlayer(helper);
        ServerPlayer kim = joinMockPlayer(helper);
        String name = freshName();
        try {
            ECO.setBalance(server, alex.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, sam.getUUID(), 5_000, "gametest.setup");
            ECO.setBalance(server, kim.getUUID(), 0, "gametest.setup");

            check(helper, !SharedAccountService.create(alex, "a b").success(), "names can't have spaces");
            check(helper, !SharedAccountService.create(alex, "ab").success(), "or be shorter than 3");
            check(helper, SharedAccountService.create(alex, name).success(), "create " + name);
            check(helper, !SharedAccountService.create(sam, name.toUpperCase()).success(), "names are unique, ignoring case");
            SharedAccounts.Account account = SharedAccounts.get(server).byName(name).orElseThrow();
            UUID id = account.id();
            helper.assertValueEqual(ECO.getBalance(server, id), 0L, "a new account starts empty");

            check(helper, SharedAccountService.addMember(alex, name, sam.getGameProfile()).success(), "add sam");
            check(helper, !SharedAccountService.addMember(alex, name, sam.getGameProfile()).success(), "sam is already in");
            check(helper, !SharedAccountService.addMember(sam, name, kim.getGameProfile()).success(), "only the owner adds members");
            check(helper, !SharedAccountService.deposit(kim, name, "1").success(), "kim isn't in it");

            check(helper, SharedAccountService.deposit(sam, name, "30").success(), "sam puts in $30");
            check(helper, SharedAccountService.withdraw(alex, name, "10").success(), "alex takes out $10");
            check(helper, !SharedAccountService.withdraw(alex, name, "25").success(), "only $20 is left");
            helper.assertValueEqual(ECO.getBalance(server, sam.getUUID()), 2_000L, "sam paid in $30");
            helper.assertValueEqual(ECO.getBalance(server, alex.getUUID()), 11_000L, "alex got $10");
            helper.assertValueEqual(ECO.getBalance(server, id), 2_000L, "$20 left");

            check(helper, SharedAccountService.pay(sam, name, kim.getGameProfile(), "5").success(), "sam pays kim $5 from it");
            helper.assertValueEqual(ECO.getBalance(server, kim.getUUID()), 500L, "kim got $5");
            TransactionLog.Entry payment = TransactionLog.get(server).forPlayer(id).get(0);
            helper.assertValueEqual(payment.by(), sam.getUUID(), "the history says sam paid");
            helper.assertValueEqual(payment.to(), kim.getUUID(), "to kim");

            check(helper, !SharedAccountService.leave(alex, name).success(), "the owner can't just leave");
            check(helper, SharedAccountService.leave(sam, name).success(), "sam leaves");
            check(helper, !SharedAccountService.withdraw(sam, name, "1").success(), "and can't take money any more");

            check(helper, SharedAccountService.addMember(alex, name, kim.getGameProfile()).success(), "add kim");
            check(helper, SharedAccountService.setOwner(alex, name, kim.getGameProfile().getName()).success(), "kim becomes the owner");
            check(helper, !SharedAccountService.close(alex, name).success(), "alex can't close it any more");
            check(helper, SharedAccountService.removeMember(kim, name, alex.getGameProfile().getName()).success(), "kim removes alex");
            check(helper, SharedAccountService.close(kim, name).success(), "kim closes it");
            helper.assertValueEqual(ECO.getBalance(server, kim.getUUID()), 500L + 1_500, "the last $15 went to kim");
            check(helper, Ledger.get(server).find(id).isEmpty(), "the bank account is gone");
            check(helper, SharedAccounts.get(server).byName(name).isEmpty(), "and so is the shared account");
        } finally {
            leave(server, alex, sam, kim);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void membersCardsPayFromTheSharedAccount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinMockPlayer(helper);
        ServerPlayer kim = joinMockPlayer(helper);
        String name = freshName();
        try {
            ECO.setBalance(server, alex.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, sam.getUUID(), 0, "gametest.setup");
            ECO.setBalance(server, kim.getUUID(), 0, "gametest.setup");
            check(helper, SharedAccountService.create(alex, name).success(), "create");
            check(helper, SharedAccountService.addMember(alex, name, sam.getGameProfile()).success(), "add sam");
            check(helper, SharedAccountService.deposit(alex, name, "100").success(), "alex puts in $100");
            UUID id = SharedAccounts.get(server).byName(name).orElseThrow().id();

            check(helper, !CardService.issueShared(kim, name, PIN).success(), "kim isn't in it, so no card");
            ShopActionResult issued = CardService.issueShared(sam, name, PIN);
            check(helper, issued.success(), "sam gets a card: " + issued.message().getString());
            ItemStack card = DebitCardItem.findCard(sam);
            BankCard data = DebitCardItem.cardOf(card);
            check(helper, data != null && data.shared() && data.account().equals(id), "it's a card for the shared account");

            ShopBlockEntity shop = placeShop(helper, kim, 500);
            ShopActionResult bought = SharedAccountService.actingAs(sam.getUUID(),
                    () -> shop.sellTo(sam, 1, 500, Payment.card(PIN, data.cardId())));
            check(helper, bought.success(), "sam buys with it: " + bought.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, id), 9_500L, "the $5 came from the shared account");
            helper.assertValueEqual(ECO.getBalance(server, sam.getUUID()), 0L, "not from sam");
            helper.assertValueEqual(ECO.getBalance(server, kim.getUUID()), 500L, "kim was paid");
            helper.assertValueEqual(TransactionLog.get(server).forPlayer(id).get(0).by(), sam.getUUID(), "the history says sam spent it");

            check(helper, !AtmService.bank(sam, card, PIN, AtmActionPayload.BORROW, "10").success(), "no loans on a shared card");
            check(helper, AtmService.checkBalance(sam, card, PIN).success(), "but the balance works");

            check(helper, SharedAccountService.removeMember(alex, name, sam.getGameProfile().getName()).success(), "remove sam");
            check(helper, !shop.sellTo(sam, 1, 500, Payment.card(PIN, data.cardId())).success(), "sam's card stopped working");
            helper.assertValueEqual(ECO.getBalance(server, id), 9_500L, "nothing more was spent");
        } finally {
            leave(server, alex, sam, kim);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aTeamShopRunsFromTheSharedAccount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinMockPlayer(helper);
        ServerPlayer kim = joinMockPlayer(helper);
        String name = freshName();
        try {
            ECO.setBalance(server, alex.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, sam.getUUID(), 0, "gametest.setup");
            ECO.setBalance(server, kim.getUUID(), 5_000, "gametest.setup");
            ShopBlockEntity shop = placeShop(helper, sam, 1_000);
            check(helper, !shop.cycleAccount(server).success(), "sam isn't in a shared account yet");

            check(helper, SharedAccountService.create(alex, name).success(), "create");
            check(helper, SharedAccountService.addMember(alex, name, sam.getGameProfile()).success(), "add sam");
            check(helper, SharedAccountService.deposit(alex, name, "20").success(), "alex puts in $20");
            UUID id = SharedAccounts.get(server).byName(name).orElseThrow().id();
            check(helper, shop.cycleAccount(server).success(), "sam runs the shop from the shared account");
            helper.assertValueEqual(shop.accountName(), name, "the shop shows which account");
            helper.assertValueEqual(shop.bankAccount(server), id, "and uses it");

            giveCard(helper, kim, PIN);
            check(helper, shop.sellTo(kim, 1, 1_000, Payment.card(PIN)).success(), "kim buys a bundle");
            helper.assertValueEqual(ECO.getBalance(server, id), 2_000L + 1_000, "the sale went into the shared account");
            helper.assertValueEqual(ECO.getBalance(server, sam.getUUID()), 0L, "not to sam");

            check(helper, shop.trySetPrices("10", "4").success(), "the shop buys too, at $4");
            kim.getInventory().add(new ItemStack(Items.DIAMOND, 2));
            check(helper, shop.buyFrom(kim, 1, 400).success(), "kim sells a bundle back");
            helper.assertValueEqual(ECO.getBalance(server, id), 3_000L - 400, "the shared account paid for it");

            check(helper, SharedAccountService.removeMember(alex, name, sam.getGameProfile().getName()).success(), "remove sam");
            helper.assertValueEqual(shop.bankAccount(server), sam.getUUID(), "the shop goes back to sam's own account");
            helper.assertValueEqual(shop.accountName(), "", "and says so");
        } finally {
            leave(server, alex, sam, kim);
        }
        helper.succeed();
    }

    // ---- helpers ----

    /** A name no earlier run used, since shared accounts are kept with the test world. */
    private static String freshName() {
        return "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** A shop owned by {@code owner} selling 2 diamonds for {@code price}, with 10 in stock. */
    private static ShopBlockEntity placeShop(GameTestHelper helper, ServerPlayer owner, long price) {
        helper.setBlock(SHOP_POS, ModBlocks.SHOP.get());
        ShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
        shop.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        check(helper, shop.trySetTemplate(new ItemStack(Items.DIAMOND, 2)).success(), "set item");
        check(helper, shop.trySetPrices(Money.toPlainString(price), "").success(), "set price");
        ItemHandlerHelper.insertItem(shop.stock(), new ItemStack(Items.DIAMOND, 10), false);
        return shop;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

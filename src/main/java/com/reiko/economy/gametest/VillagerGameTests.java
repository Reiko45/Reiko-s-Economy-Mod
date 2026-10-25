package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.villager.VillagerPricing;
import com.reiko.economy.villager.VillagerTradeService;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for trading with villagers in money instead of emeralds. */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class VillagerGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;

    private VillagerGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void buyingWithCashGivesChangeAndCountsTheTrade(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        Villager villager = farmer(helper, new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.BREAD, 6), 12, 2, 0.05F));
        try {
            long price = 2 * emerald();
            ECO.setBalance(server, player.getUUID(), 1000, "gametest.setup");
            giveCash(player, 3 * price + 75);
            MerchantMenu menu = open(villager, player);

            ShopActionResult result = VillagerTradeService.trade(player, menu, 0, 2, price, Payment.cash());
            check(helper, result.success(), "buy twice with cash: " + result.message().getString());
            helper.assertValueEqual(cashIn(player), price + 75, "paid for two trades, the rest comes back as change");
            helper.assertValueEqual(player.getInventory().countItem(Items.BREAD), 12, "got 2 x 6 bread");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "no emeralds involved");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 1000L, "cash doesn't touch the bank balance");
            helper.assertValueEqual(villager.getOffers().get(0).getUses(), 2, "the villager counts both trades");
            helper.assertValueEqual(villager.getVillagerXp(), 4, "and gains its experience");

            check(helper, !VillagerTradeService.trade(player, menu, 0, 2, price, Payment.cash()).success(),
                    "can't pay for two more with one trade's worth of cash");
            helper.assertValueEqual(cashIn(player), price + 75, "a refused trade takes nothing");
        } finally {
            villager.discard();
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void buyingByCardChargesTheCardsAccount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        Villager villager = farmer(helper, new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 4), 16, 1, 0.05F));
        try {
            long price = emerald();
            ECO.setBalance(server, player.getUUID(), 5 * price, "gametest.setup");
            giveCard(helper, player, "1234");
            MerchantMenu menu = open(villager, player);

            check(helper, !VillagerTradeService.trade(player, menu, 0, 1, price, Payment.card("9999")).success(), "wrong PIN");
            helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 0, "a wrong PIN buys nothing");
            helper.assertValueEqual(villager.getOffers().get(0).getUses(), 0, "and doesn't count as a trade");

            ShopActionResult result = VillagerTradeService.trade(player, menu, 0, 3, price, Payment.card("1234"));
            check(helper, result.success(), "buy 3 by card: " + result.message().getString());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 2 * price, "the card paid for 3 trades");
            helper.assertValueEqual(player.getInventory().countItem(Items.APPLE), 12, "got 3 x 4 apples");

            check(helper, !VillagerTradeService.trade(player, menu, 0, 3, price, Payment.card("1234")).success(),
                    "can't buy more than the account holds");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 2 * price, "nothing taken");
        } finally {
            villager.discard();
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sellingToAVillagerPaysCash(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        Villager villager = farmer(helper, new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
        try {
            player.getInventory().add(new ItemStack(Items.WHEAT, 45));
            MerchantMenu menu = open(villager, player);
            long payout = emerald();

            ShopActionResult result = VillagerTradeService.trade(player, menu, 0, 2, -payout, Payment.cash());
            check(helper, result.success(), "sell 40 wheat: " + result.message().getString());
            helper.assertValueEqual(player.getInventory().countItem(Items.WHEAT), 5, "40 wheat handed over");
            helper.assertValueEqual(cashIn(player), 2 * payout, "paid in cash for two trades");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "no emeralds handed out");

            check(helper, !VillagerTradeService.trade(player, menu, 0, 1, -payout, Payment.cash()).success(),
                    "5 wheat isn't enough for another trade");
            helper.assertValueEqual(cashIn(player), 2 * payout, "and pays nothing");
        } finally {
            villager.discard();
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tradesThatAlsoNeedAnItemTakeIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        Villager villager = farmer(helper, new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.of(new ItemCost(Items.BOOK)),
                new ItemStack(Items.ENCHANTED_BOOK), 12, 5, 0.2F));
        try {
            long price = 5 * emerald();
            giveCash(player, price);
            MerchantMenu menu = open(villager, player);

            check(helper, !VillagerTradeService.trade(player, menu, 0, 1, price, Payment.cash()).success(), "no book, no trade");
            helper.assertValueEqual(cashIn(player), price, "the cash stays put");

            player.getInventory().add(new ItemStack(Items.BOOK, 2));
            ShopActionResult result = VillagerTradeService.trade(player, menu, 0, 1, price, Payment.cash());
            check(helper, result.success(), "with a book: " + result.message().getString());
            helper.assertValueEqual(player.getInventory().countItem(Items.BOOK), 1, "one book used");
            helper.assertValueEqual(player.getInventory().countItem(Items.ENCHANTED_BOOK), 1, "got the enchanted book");
            helper.assertValueEqual(cashIn(player), 0L, "paid exactly");
        } finally {
            villager.discard();
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stockPriceAndRoomAreChecked(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.COOKIE, 8), 2, 1, 0.05F);
        Villager villager = farmer(helper, offer);
        try {
            long value = emerald();
            giveCash(player, 20 * value);
            MerchantMenu menu = open(villager, player);

            check(helper, !VillagerTradeService.trade(player, menu, 0, 3, 3 * value, Payment.cash()).success(), "only 2 in stock");
            check(helper, !VillagerTradeService.trade(player, menu, 0, 1, 2 * value, Payment.cash()).success(), "a price the player didn't see");
            check(helper, !VillagerTradeService.trade(player, menu, 5, 1, 3 * value, Payment.cash()).success(), "no such offer");

            // Vanilla discounts (reputation, Hero of the Village) lower the emerald count, and so the price.
            villager.getOffers().get(0).setSpecialPriceDiff(-1);
            helper.assertValueEqual(VillagerPricing.terms(villager.getOffers().get(0), value).price(), 2 * value, "discounted price");
            check(helper, VillagerTradeService.trade(player, menu, 0, 1, 2 * value, Payment.cash()).success(), "buy at the discount");
            helper.assertValueEqual(cashIn(player), 18 * value, "paid the discounted price");

            // Paying by card frees no slots, so a full inventory has no room for the cookies.
            player.getInventory().clearContent();
            ECO.setBalance(server, player.getUUID(), 10 * value, "gametest.setup");
            giveCard(helper, player, "4321");
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                if (player.getInventory().items.get(i).isEmpty()) {
                    player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
                }
            }
            check(helper, !VillagerTradeService.trade(player, menu, 0, 1, 2 * value, Payment.card("4321")).success(), "no room for the cookies");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 10 * value, "nothing charged when there's no room");

            player.getInventory().clearContent();
            giveCash(player, 10 * value);
            check(helper, VillagerTradeService.trade(player, menu, 0, 1, 2 * value, Payment.cash()).success(), "the last one in stock");
            check(helper, !VillagerTradeService.trade(player, menu, 0, 1, 2 * value, Payment.cash()).success(), "sold out");
        } finally {
            villager.discard();
            leave(server, player);
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static long emerald() {
        return EconomyConfig.emeraldValue();
    }

    /** A villager that stands still and only has the given offers. */
    private static Villager farmer(GameTestHelper helper, MerchantOffer... offers) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setNoAi(true);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        MerchantOffers list = new MerchantOffers();
        for (MerchantOffer offer : offers) {
            list.add(offer);
        }
        villager.setOffers(list);
        return villager;
    }

    /** Opens vanilla's trading menu between the player and the villager, as right-clicking it would. */
    private static MerchantMenu open(Villager villager, ServerPlayer player) {
        villager.setTradingPlayer(player);
        MerchantMenu menu = new MerchantMenu(1, player.getInventory(), villager);
        player.containerMenu = menu;
        return menu;
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

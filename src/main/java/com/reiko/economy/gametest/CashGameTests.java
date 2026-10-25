package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.AtmService;
import com.reiko.economy.core.Denomination;
import com.reiko.economy.core.TransactionLog;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for coins, bills and the ATM. */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class CashGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final String PIN = "1234";

    private CashGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void withdrawalPaysTheFewestPieces(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 10_000, "gametest.setup");
            ItemStack card = giveCard(helper, player, PIN);
            check(helper, AtmService.withdraw(player, card, PIN, "37.41").success(), "withdraw 37.41");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 6_259L, "balance after withdrawal");
            helper.assertValueEqual(cashIn(player.getInventory()), 3_741L, "cash received");
            for (Denomination d : new Denomination[] {Denomination.TWENTY, Denomination.TEN, Denomination.FIVE, Denomination.TWO,
                    Denomination.QUARTER, Denomination.DIME, Denomination.NICKEL, Denomination.PENNY}) {
                helper.assertValueEqual(player.getInventory().countItem(ModItems.cash(d)), 1, "one " + d.name());
            }
            check(helper, TransactionLog.get(server).forPlayer(player.getUUID()).stream()
                    .anyMatch(e -> e.reason().equals("atm.withdraw") && e.amount() == 3_741), "withdrawal is logged");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void badWithdrawalsChangeNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 10_000, "gametest.setup");
            ItemStack card = giveCard(helper, player, PIN);
            for (String amount : new String[] {"abc", "0", "", "-5", "1.005", "100.01", "20000"}) {
                check(helper, !AtmService.withdraw(player, card, PIN, amount).success(), "withdrawing '" + amount + "' should fail");
            }
            check(helper, !AtmService.withdraw(player, ItemStack.EMPTY, PIN, "5").success(), "no card, no cash");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 10_000L, "balance unchanged");
            helper.assertValueEqual(cashIn(player.getInventory()), 0L, "no cash handed out");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void withdrawalNeedsInventoryRoom(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 10_000, "gametest.setup");
            ItemStack card = giveCard(helper, player, PIN);
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                if (inventory.items.get(i) != card) {
                    inventory.items.set(i, new ItemStack(Items.DIRT, 64));
                }
            }
            check(helper, !AtmService.withdraw(player, card, PIN, "5").success(), "a full inventory refuses cash");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 10_000L, "balance unchanged");

            int dirtSlot = inventory.items.get(0) == card ? 1 : 0;
            inventory.items.set(dirtSlot, new ItemStack(ModItems.cash(Denomination.FIVE), 3));
            check(helper, AtmService.withdraw(player, card, PIN, "5").success(), "a partial stack of $5 bills has room for one more");
            helper.assertValueEqual(inventory.countItem(ModItems.cash(Denomination.FIVE)), 4, "topped up the stack");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 9_500L, "paid 5.00");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void depositAllTakesOnlyCash(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ItemStack card = giveCard(helper, player, PIN);
            ECO.setBalance(server, player.getUUID(), 0, "gametest.setup");
            player.getInventory().add(new ItemStack(ModItems.cash(Denomination.TWENTY), 3));
            player.getInventory().add(new ItemStack(ModItems.cash(Denomination.QUARTER), 2));
            player.getInventory().add(new ItemStack(Items.DIRT, 5));

            check(helper, !AtmService.depositAll(player, card, "9999").success(), "a wrong PIN deposits nothing");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 0L, "nothing credited on a wrong PIN");
            check(helper, AtmService.depositAll(player, card, PIN).success(), "deposit all");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 6_050L, "credited 60.50");
            helper.assertValueEqual(cashIn(player.getInventory()), 0L, "cash gone");
            helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 5, "dirt untouched");
            check(helper, !AtmService.depositAll(player, card, PIN).success(), "nothing left to deposit");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 6_050L, "an empty deposit adds nothing");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void depositSlotsAreEmptiedIntoTheBalance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ItemStack card = giveCard(helper, player, PIN);
            ECO.setBalance(server, player.getUUID(), 0, "gametest.setup");
            SimpleContainer slots = new SimpleContainer(9);
            slots.setItem(0, new ItemStack(ModItems.cash(Denomination.HUNDRED), 2));
            slots.setItem(4, new ItemStack(ModItems.cash(Denomination.PENNY), 1));

            check(helper, AtmService.depositFrom(player, card, PIN, slots).success(), "deposit the slots");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 20_001L, "credited 200.01");
            check(helper, slots.isEmpty(), "slots emptied");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cashValuesAndChange(GameTestHelper helper) {
        helper.assertValueEqual(CashItem.valueOf(new ItemStack(ModItems.cash(Denomination.TWENTY), 64)), 128_000L, "64 x $20");
        helper.assertValueEqual(CashItem.valueOf(new ItemStack(Items.DIAMOND, 64)), 0L, "diamonds aren't cash");
        long total = 0;
        int stacks = 0;
        for (ItemStack stack : CashItem.makeChange(2_500_000)) { // $25,000
            total += CashItem.valueOf(stack);
            check(helper, stack.getCount() <= 64, "stacks of at most 64");
            stacks++;
        }
        helper.assertValueEqual(total, 2_500_000L, "change adds up");
        helper.assertValueEqual(stacks, 4, "250 $100 bills in 4 stacks");
        helper.succeed();
    }

    private static long cashIn(Inventory inventory) {
        long total = 0;
        for (ItemStack stack : inventory.items) {
            total += CashItem.valueOf(stack);
        }
        return total;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

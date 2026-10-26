package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.AtmService;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.core.Denomination;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.WalletContents;
import com.reiko.economy.menu.WalletMenu;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.registry.ModItems;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** In-game tests for the wallet: paying from it, change and payouts going into it, and taking cash out. */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class WalletGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SHOP_POS = new BlockPos(1, 1, 1);

    private WalletGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void aWalletPaysAtShopsAndKeepsTheChangeWithAFullInventory(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, owner.getUUID(), 0, "gametest.setup");
            ItemStack wallet = wallet(Map.of(Denomination.TWENTY, 1, Denomination.QUARTER, 3));
            buyer.getInventory().setItem(0, wallet);
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.BREAD, 2), "4.50");
            // One free slot for the bread; the change has to fit in the wallet.
            fillInventory(buyer);
            buyer.getInventory().setItem(35, ItemStack.EMPTY);

            ShopActionResult result = shop.sellTo(buyer, 1, 450, Payment.cash());
            check(helper, result.success(), "pay $4.50 from the wallet: " + result.message().getString());
            helper.assertValueEqual(Purse.contents(buyer.getInventory().getItem(0)).value(), 1_625L,
                    "$20.75 - $4.50 = $16.25 left, with the change back in the wallet");
            helper.assertValueEqual(looseCash(buyer), 0L, "no loose change needed a slot");
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 450L, "the owner was paid");
        } finally {
            leave(server, buyer, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void looseCashIsSpentBeforeTheWallet(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer buyer = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            buyer.getInventory().setItem(0, wallet(Map.of(Denomination.TEN, 1)));
            buyer.getInventory().add(new ItemStack(ModItems.cash(Denomination.FIVE), 1));
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.APPLE, 1), "12");

            check(helper, shop.sellTo(buyer, 1, 1_200, Payment.cash()).success(), "$5 loose and $10 in the wallet pay $12");
            helper.assertValueEqual(looseCash(buyer), 0L, "the loose $5 went first");
            helper.assertValueEqual(Purse.contents(buyer.getInventory().getItem(0)).value(), 300L, "the $3 change is in the wallet");
        } finally {
            leave(server, buyer, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void atmCashGoesIntoTheWalletAndDepositAllEmptiesIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 10_000, "gametest.setup");
            ItemStack card = giveCard(helper, player, "1234");
            player.getInventory().add(wallet(Map.of()));
            fillInventory(player);

            ShopActionResult withdrew = AtmService.withdraw(player, card, "1234", "37.41");
            check(helper, withdrew.success(), "withdraw with a full inventory: " + withdrew.message().getString());
            helper.assertValueEqual(Purse.value(player), 3_741L, "the cash is in the wallet");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 10_000L - 3_741L, "taken from the bank");

            player.getInventory().setItem(20, new ItemStack(ModItems.cash(Denomination.ONE), 2));
            check(helper, AtmService.depositAll(player, card, "1234").success(), "deposit everything");
            helper.assertValueEqual(Purse.value(player), 0L, "loose cash and the wallet are both empty");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 10_200L, "all of it went back in the bank");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theWalletScreenTakesCashOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, wallet(Map.of(Denomination.TWENTY, 3, Denomination.QUARTER, 2)));
            WalletMenu menu = new WalletMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);

            check(helper, menu.take(player, Denomination.TWENTY.ordinal(), 1).success(), "take one $20");
            helper.assertValueEqual(player.getInventory().countItem(ModItems.cash(Denomination.TWENTY)), 1, "one $20 in the inventory");
            helper.assertValueEqual(menu.contents().count(Denomination.TWENTY), 2, "two left in the wallet");

            check(helper, menu.take(player, WalletMenu.ALL, 0).success(), "take everything");
            check(helper, menu.contents().isEmpty(), "the wallet is empty");
            helper.assertValueEqual(looseCash(player), 6_050L, "all $60.50 is in the inventory");

            player.getInventory().setItem(0, wallet(Map.of(Denomination.ONE, 100)));
            for (int i = 1; i < player.getInventory().items.size(); i++) {
                player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
            player.getInventory().items.set(1, ItemStack.EMPTY);
            check(helper, !menu.take(player, WalletMenu.ALL, 0).success(), "only one free slot");
            helper.assertValueEqual(menu.contents().count(Denomination.ONE), 36, "64 came out, 36 stayed in");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cashPickedUpGoesIntoTheWallet(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            player.getInventory().add(wallet(Map.of()));
            ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(),
                    new ItemStack(ModItems.cash(Denomination.FIFTY), 2));
            dropped.setNoPickUpDelay();
            player.level().addFreshEntity(dropped);
            dropped.playerTouch(player);
            helper.assertValueEqual(Purse.value(player), 10_000L, "the two $50s went into the wallet");
            helper.assertValueEqual(looseCash(player), 0L, "not loose in the inventory");
            check(helper, dropped.isRemoved(), "the dropped cash is gone from the ground");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static ItemStack wallet(Map<Denomination, Integer> pieces) {
        ItemStack wallet = new ItemStack(ModItems.WALLET.get());
        Purse.setContents(wallet, new WalletContents(pieces));
        return wallet;
    }

    /** Fills every empty main inventory slot with dirt. */
    private static void fillInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (player.getInventory().items.get(i).isEmpty()) {
                player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
        }
    }

    private static long looseCash(ServerPlayer player) {
        long total = 0;
        for (ItemStack stack : player.getInventory().items) {
            total += CashItem.valueOf(stack);
        }
        return total;
    }

    private static ShopBlockEntity placeShop(GameTestHelper helper, ServerPlayer owner, ItemStack bundle, String price) {
        helper.setBlock(SHOP_POS, ModBlocks.SHOP.get());
        ShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
        shop.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        check(helper, shop.trySetTemplate(bundle).success(), "set item");
        check(helper, shop.trySetPrice(price).success(), "set price");
        ItemHandlerHelper.insertItem(shop.stock(), bundle.copyWithCount(64), false);
        return shop;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

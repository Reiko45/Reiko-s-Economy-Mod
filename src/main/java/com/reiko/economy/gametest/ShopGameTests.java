package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.giveCard;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;
import static com.reiko.economy.gametest.GameTestSupport.newAccount;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.api.TransactionType;
import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.core.TransactionLog;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopAccess;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for Phase 2: player shops, admin shops, protection and the transaction log. */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class ShopGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SHOP_POS = new BlockPos(1, 1, 1);
    private static final String PIN = "1234";
    private static final Payment CARD = Payment.card(PIN);

    private ShopGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void playerShopSellsAndPaysTheOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = joinMockPlayer(helper);
        ServerPlayer buyer = joinMockPlayer(helper);
        try {
            ShopBlockEntity shop = placeShop(helper, owner);
            check(helper, shop.trySetTemplate(new ItemStack(Items.DIAMOND, 2)).success(), "set item");
            check(helper, shop.trySetPrice("10").success(), "set price");
            helper.assertTrue(ItemHandlerHelper.insertItem(shop.stock(), new ItemStack(Items.DIAMOND, 10), false).isEmpty(), "stock fits");
            giveCard(helper, buyer, PIN);
            ECO.setBalance(server, owner.getUUID(), 0, "gametest.setup");
            ECO.setBalance(server, buyer.getUUID(), 5000, "gametest.setup");

            check(helper, shop.sellTo(buyer, 3, 1000, Payment.card(PIN)).success(), "buying 3 bundles by card should work");
            helper.assertValueEqual(ECO.getBalance(server, buyer.getUUID()), 2000L, "buyer paid 30.00");
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 3000L, "owner got 30.00");
            helper.assertValueEqual(buyer.getInventory().countItem(Items.DIAMOND), 6, "buyer got 6 diamonds");
            helper.assertValueEqual(shop.stockCount(), 4, "4 diamonds left in stock");

            check(helper, !shop.sellTo(buyer, 3, 1000, CARD).success(), "can't buy more than the stock");
            check(helper, !shop.sellTo(buyer, 1, 999, CARD).success(), "a stale price is rejected");
            check(helper, !shop.sellTo(owner, 1, 1000, CARD).success(), "owners can't buy from themselves");
            check(helper, !shop.sellTo(buyer, 0, 1000, CARD).success(), "zero bundles is rejected");
            check(helper, !shop.sellTo(buyer, 65, 1000, CARD).success(), "more than 64 bundles is rejected");
            ECO.setBalance(server, buyer.getUUID(), 500, "gametest.setup");
            check(helper, !shop.sellTo(buyer, 1, 1000, CARD).success(), "can't buy without the money");
            check(helper, !shop.sellTo(buyer, 1, 1000, Payment.cash()).success(), "can't pay cash without cash");

            helper.assertValueEqual(shop.stockCount(), 4, "failed purchases leave the stock alone");
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 3000L, "failed purchases pay the owner nothing");
            helper.assertValueEqual(buyer.getInventory().countItem(Items.DIAMOND), 6, "failed purchases give nothing");
        } finally {
            leave(server, owner, buyer);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stockOnlyTakesTheItemForSale(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ShopBlockEntity shop = placeShop(helper, owner);
            shop.trySetTemplate(new ItemStack(Items.DIAMOND));
            ItemStack rejected = ItemHandlerHelper.insertItem(shop.stock(), new ItemStack(Items.DIRT, 5), false);
            helper.assertValueEqual(rejected.getCount(), 5, "dirt can't go into a diamond shop");

            ItemHandlerHelper.insertItem(shop.stock(), new ItemStack(Items.DIAMOND, 3), false);
            check(helper, !shop.trySetTemplate(new ItemStack(Items.GOLD_INGOT)).success(), "item can't change while stocked");
            check(helper, shop.template().is(Items.DIAMOND), "item is still diamonds");
            check(helper, shop.trySetTemplate(new ItemStack(Items.DIAMOND, 4)).success(), "bundle size can change while stocked");

            for (int slot = 0; slot < shop.stock().getSlots(); slot++) {
                shop.stock().extractItem(slot, 64, false);
            }
            check(helper, shop.trySetTemplate(new ItemStack(Items.GOLD_INGOT)).success(), "item can change once empty");
        } finally {
            leave(server, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void shopSurvivesSaveAndLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ShopBlockEntity shop = placeShop(helper, owner);
            shop.trySetTemplate(new ItemStack(Items.EMERALD, 3));
            shop.trySetPrice("7.25");
            ItemHandlerHelper.insertItem(shop.stock(), new ItemStack(Items.EMERALD, 20), false);

            CompoundTag saved = shop.saveWithFullMetadata(server.registryAccess());
            BlockEntity loaded = BlockEntity.loadStatic(shop.getBlockPos(), shop.getBlockState(), saved, server.registryAccess());
            check(helper, loaded instanceof ShopBlockEntity, "reloads as a shop");
            ShopBlockEntity copy = (ShopBlockEntity) loaded;
            helper.assertValueEqual(copy.owner(), owner.getUUID(), "owner");
            helper.assertValueEqual(copy.price(), 725L, "price");
            check(helper, ItemStack.isSameItemSameComponents(copy.template(), shop.template()), "item");
            helper.assertValueEqual(copy.template().getCount(), 3, "bundle size");
            helper.assertValueEqual(copy.stockCount(), 20, "stock");
        } finally {
            leave(server, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void onlyTheOwnerCanBreakAShop(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = joinMockPlayer(helper);
        ServerPlayer stranger = joinMockPlayer(helper);
        try {
            ShopBlockEntity shop = placeShop(helper, owner);
            check(helper, ShopAccess.canManage(owner, shop), "the owner manages their shop");
            check(helper, !ShopAccess.canManage(stranger, shop), "a stranger doesn't");

            BlockPos pos = helper.absolutePos(SHOP_POS);
            BlockState state = helper.getLevel().getBlockState(pos);
            check(helper, NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), pos, state, stranger)).isCanceled(),
                    "a stranger's break is blocked");
            check(helper, !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), pos, state, owner)).isCanceled(),
                    "the owner's break goes through");
        } finally {
            leave(server, owner, stranger);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void adminShopBuysAndSells(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        try {
            helper.setBlock(SHOP_POS, ModBlocks.ADMIN_SHOP.get());
            AdminShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
            shop.trySetTemplate(new ItemStack(Items.WHEAT, 8));
            check(helper, shop.trySetPrices("5", "2").success(), "set prices");
            check(helper, !shop.trySetPrices("abc", "1").success(), "bad price text is rejected");
            giveCard(helper, player, PIN);
            ECO.setBalance(server, player.getUUID(), 1000, "gametest.setup");

            check(helper, shop.sellTo(player, 1, 500, CARD).success(), "buying a bundle by card should work");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 500L, "paid 5.00 from the card's account");
            helper.assertValueEqual(player.getInventory().countItem(Items.WHEAT), 8, "got 8 wheat");

            check(helper, shop.buyFrom(player, Integer.MAX_VALUE, 200).success(), "selling everything should work");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 500L, "selling pays cash, not the balance");
            helper.assertValueEqual(cashIn(player), 200L, "got 2.00 in cash");
            helper.assertValueEqual(player.getInventory().countItem(Items.WHEAT), 0, "wheat gone");
            check(helper, !shop.buyFrom(player, Integer.MAX_VALUE, 200).success(), "nothing left to sell");

            player.getInventory().add(new ItemStack(Items.WHEAT, 20));
            check(helper, shop.buyFrom(player, Integer.MAX_VALUE, 200).success(), "selling whole bundles");
            helper.assertValueEqual(cashIn(player), 600L, "2 more bundles sold for 4.00 cash");
            helper.assertValueEqual(player.getInventory().countItem(Items.WHEAT), 4, "a partial bundle stays");

            check(helper, shop.sellTo(player, 1, 500, Payment.cash()).success(), "buying a bundle with cash");
            helper.assertValueEqual(cashIn(player), 100L, "paid 5.00 of the 6.00 cash");
            helper.assertValueEqual(player.getInventory().countItem(Items.WHEAT), 12, "got 8 more wheat");
            check(helper, !shop.sellTo(player, 1, 500, Payment.cash()).success(), "can't pay cash without enough cash");

            check(helper, !shop.sellTo(player, 1, 499, CARD).success(), "a stale price is rejected");
            shop.trySetPrices("5", "");
            check(helper, !shop.buyFrom(player, 1, 0).success(), "selling is off when the sell price is blank");
            ECO.setBalance(server, player.getUUID(), 100, "gametest.setup");
            check(helper, !shop.sellTo(player, 1, 500, CARD).success(), "can't buy without the money");
            helper.assertValueEqual(player.getInventory().countItem(Items.WHEAT), 12, "failed trades give nothing");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void transactionLogRecordsChanges(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID alice = newAccount(server, 1000);
        UUID bob = newAccount(server, 0);
        ECO.transfer(server, alice, bob, 100, "gametest.log");
        ECO.deposit(server, alice, 50, "gametest.log");

        List<TransactionLog.Entry> aliceLog = TransactionLog.get(server).forPlayer(alice);
        check(helper, aliceLog.size() >= 2, "alice has entries");
        TransactionLog.Entry newest = aliceLog.get(0);
        helper.assertValueEqual(newest.type(), TransactionType.DEPOSIT, "newest entry first");
        helper.assertValueEqual(newest.amount(), 50L, "deposit amount");
        TransactionLog.Entry transfer = aliceLog.get(1);
        helper.assertValueEqual(transfer.type(), TransactionType.TRANSFER, "then the transfer");
        helper.assertValueEqual(transfer.to(), bob, "transfer went to bob");
        helper.assertValueEqual(transfer.reason(), "gametest.log", "reason kept");
        check(helper, TransactionLog.get(server).forPlayer(bob).stream().anyMatch(e -> e.type() == TransactionType.TRANSFER),
                "bob's history shows the transfer too");
        helper.succeed();
    }

    // ---- helpers ----

    private static ShopBlockEntity placeShop(GameTestHelper helper, ServerPlayer owner) {
        helper.setBlock(SHOP_POS, ModBlocks.SHOP.get());
        ShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
        shop.setOwner(owner.getUUID(), owner.getGameProfile().getName());
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

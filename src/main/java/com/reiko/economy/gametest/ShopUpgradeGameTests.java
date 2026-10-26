package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.shop.ShopNotices;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** In-game tests for shops that buy from players, hoppers restocking shops, and notices for shop owners. */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class ShopUpgradeGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SHOP_POS = new BlockPos(1, 1, 1);

    private ShopUpgradeGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void playersSellToAShopAndTheOwnerPays(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, owner.getUUID(), 10_000, "gametest.setup");
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.COBBLESTONE, 16));
            check(helper, shop.trySetPrices("5", "4").success(), "players pay $5, the shop pays $4");
            seller.getInventory().add(new ItemStack(Items.COBBLESTONE, 40));

            ShopActionResult result = shop.buyFrom(seller, Integer.MAX_VALUE, 400);
            check(helper, result.success(), "sell everything: " + result.message().getString());
            helper.assertValueEqual(Purse.value(seller), 800L, "two bundles of 16 for $4 each, in cash");
            helper.assertValueEqual(seller.getInventory().countItem(Items.COBBLESTONE), 8, "the 8 left over aren't a bundle");
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 9_200L, "the $8 came out of the owner's bank");
            helper.assertValueEqual(shop.stockCount(), 32, "the cobblestone went into the stock");

            check(helper, shop.sellTo(seller, 1, 500, Payment.cash()).success(), "and a bundle can be bought back for $5");
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 9_700L, "the owner made $1 on it");

            check(helper, !shop.buyFrom(owner, 1, 400).success(), "owners can't sell to their own shop");
            check(helper, !shop.buyFrom(seller, 1, 300).success(), "a changed price is caught");
        } finally {
            leave(server, seller, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pricesHaveToMakeSense(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = joinMockPlayer(helper);
        ServerPlayer buyer = joinMockPlayer(helper);
        try {
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.WHEAT, 8));
            check(helper, !shop.trySetPrices("", "").success(), "at least one price");
            check(helper, !shop.trySetPrices("2", "3").success(), "the shop can't pay more than it charges");
            check(helper, shop.trySetPrices("", "1.50").success(), "a shop can only buy");
            check(helper, shop.isConfigured(), "and that counts as set up");
            check(helper, !shop.sellsToPlayers() && shop.buysFromPlayers(), "it buys but doesn't sell");
            check(helper, !shop.sellTo(buyer, 1, 0, Payment.cash()).success(), "nobody can buy from it");
            check(helper, !shop.trySetPrice("1").success(), "the price players pay can't drop below what the shop pays");
        } finally {
            leave(server, owner, buyer);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aBuyingShopStopsWhenTheOwnerRunsOutOfMoneyOrRoom(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer seller = joinMockPlayer(helper);
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, owner.getUUID(), 700, "gametest.setup");
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.IRON_INGOT, 1));
            check(helper, shop.trySetPrices("", "3").success(), "the shop pays $3 an ingot");
            seller.getInventory().add(new ItemStack(Items.IRON_INGOT, 10));
            helper.assertValueEqual(shop.bundlesWanted(server), 2, "$7 buys two");

            check(helper, shop.buyFrom(seller, Integer.MAX_VALUE, 300).success(), "sell what the owner can afford");
            helper.assertValueEqual(seller.getInventory().countItem(Items.IRON_INGOT), 8, "only two were bought");
            helper.assertValueEqual(ECO.getBalance(server, owner.getUUID()), 100L, "$1 left");
            ShopActionResult broke = shop.buyFrom(seller, 1, 300);
            check(helper, !broke.success(), "the owner can't pay for another");
            helper.assertValueEqual(seller.getInventory().countItem(Items.IRON_INGOT), 8, "nothing moved");

            ECO.setBalance(server, owner.getUUID(), 1_000_000, "gametest.setup");
            for (int i = 0; i < ShopBlockEntity.STOCK_SLOTS; i++) {
                shop.stock().setStackInSlot(i, new ItemStack(Items.IRON_INGOT, 64));
            }
            shop.stock().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 63));
            helper.assertValueEqual(shop.roomInBundles(), 1, "room for one more");
            check(helper, shop.buyFrom(seller, Integer.MAX_VALUE, 300).success(), "fill the last space");
            helper.assertValueEqual(seller.getInventory().countItem(Items.IRON_INGOT), 7, "one went in");
            check(helper, !shop.buyFrom(seller, 1, 300).success(), "a full shop doesn't buy");
        } finally {
            leave(server, seller, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hoppersRestockAShopButCantEmptyIt(GameTestHelper helper) {
        ShopBlockEntity shop = placeShop(helper, null, new ItemStack(Items.APPLE, 1));
        check(helper, shop.trySetPrices("1", "").success(), "set the price");

        // The capability hoppers and pipes use: the shop's item goes in, other items don't, nothing comes out.
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(SHOP_POS), Direction.UP);
        helper.assertTrue(handler != null, "the shop has an item handler");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "dirt is refused");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.APPLE, 2), false).isEmpty(), "apples go in");
        helper.assertTrue(handler.extractItem(0, 64, false).isEmpty(), "nothing can be taken out");
        helper.assertValueEqual(shop.stockCount(), 2, "the two apples are in stock");

        // A real hopper above fills it; a hopper below can't pull anything out.
        BlockPos above = SHOP_POS.above();
        BlockPos below = SHOP_POS.below();
        helper.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity top = helper.getBlockEntity(above);
        top.setItem(0, new ItemStack(Items.APPLE, 3));
        top.setItem(1, new ItemStack(Items.DIRT, 1));
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(shop.stockCount(), 5, "the hopper moved the three apples in");
            helper.assertValueEqual(top.getItem(1).getCount(), 1, "the dirt stayed in the hopper");
            HopperBlockEntity bottom = helper.getBlockEntity(below);
            helper.assertTrue(bottom.isEmpty(), "the hopper below took nothing");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void ownersHearWhatHappenedWhileTheyWereAway(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = joinMockPlayer(helper);
        UUID ownerId = owner.getUUID();
        String ownerName = owner.getGameProfile().getName();
        ServerPlayer customer = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, ownerId, 1_000, "gametest.setup");
            ECO.setBalance(server, customer.getUUID(), 0, "gametest.setup");
            ShopBlockEntity shop = placeShop(helper, owner, new ItemStack(Items.BREAD, 1));
            check(helper, shop.trySetPrices("2", "1").success(), "set prices");
            ItemHandlerHelper.insertItem(shop.stock(), new ItemStack(Items.BREAD, 2), false);
            leave(server, owner);

            for (ItemStack cash : CashItem.makeChange(400)) {
                customer.getInventory().add(cash);
            }
            check(helper, shop.sellTo(customer, 1, 200, Payment.cash()).success(), "first sale");
            check(helper, shop.sellTo(customer, 1, 200, Payment.cash()).success(), "second sale empties it");
            check(helper, shop.buyFrom(customer, 1, 100).success(), "the shop buys one loaf back");

            ShopNotices.Pending pending = ShopNotices.pending(server, ownerId).orElseThrow();
            helper.assertValueEqual(pending.sales(), 2, "two sales");
            helper.assertValueEqual(pending.earned(), 400L, "worth $4");
            helper.assertValueEqual(pending.purchases(), 1, "one purchase");
            helper.assertValueEqual(pending.spent(), 100L, "worth $1");
            helper.assertValueEqual(pending.alerts().size(), 1, "one alert");
            helper.assertTrue(pending.alerts().get(0).getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("shop.reikoeconomy.alert.sold_out"), "the shop sold out");

            owner = joinMockPlayer(helper, ownerId, ownerName);
            check(helper, ShopNotices.pending(server, ownerId).isEmpty(), "joining delivers the notices");
        } finally {
            leave(server, owner, customer);
        }
        helper.succeed();
    }

    // ---- helpers ----

    /** A shop selling {@code bundle}, owned by {@code owner} (or a made-up player when null), with no prices yet. */
    private static ShopBlockEntity placeShop(GameTestHelper helper, ServerPlayer owner, ItemStack bundle) {
        helper.setBlock(SHOP_POS, ModBlocks.SHOP.get());
        ShopBlockEntity shop = helper.getBlockEntity(SHOP_POS);
        if (owner != null) {
            shop.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        } else {
            shop.setOwner(UUID.randomUUID(), "nobody");
        }
        check(helper, shop.trySetTemplate(bundle).success(), "set item");
        return shop;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

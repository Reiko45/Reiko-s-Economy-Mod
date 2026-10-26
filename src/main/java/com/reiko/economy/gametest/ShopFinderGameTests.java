package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.shop.ShopDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** In-game tests for the list of shops and the {@code /shops} commands. */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class ShopFinderGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos FIRST = new BlockPos(0, 1, 0);
    private static final BlockPos SECOND = new BlockPos(2, 1, 2);

    private ShopFinderGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void shopsAreListedAsTheyChangeAndForgottenWhenBroken(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = joinMockPlayer(helper);
        try {
            ShopBlockEntity shop = placeShop(helper, FIRST, owner, new ItemStack(Items.PRISMARINE_SHARD, 4), "3", "2");
            ShopDirectory.Entry entry = entry(helper, FIRST).orElseThrow();
            helper.assertValueEqual(entry.price(), 300L, "the price is listed");
            helper.assertValueEqual(entry.sellPrice(), 200L, "and what it pays");
            helper.assertValueEqual(entry.stockBundles(), 0, "no stock yet");
            helper.assertValueEqual(entry.owner(), owner.getUUID(), "the owner");

            ItemHandlerHelper.insertItem(shop.stock(), new ItemStack(Items.PRISMARINE_SHARD, 10), false);
            helper.assertValueEqual(entry(helper, FIRST).orElseThrow().stockBundles(), 2, "stock changes are listed");
            check(helper, shop.trySetPrices("5", "").success(), "change the prices");
            helper.assertValueEqual(entry(helper, FIRST).orElseThrow().price(), 500L, "price changes are listed");
            check(helper, !entry(helper, FIRST).orElseThrow().buys(), "and it no longer buys");

            helper.setBlock(FIRST, Blocks.AIR);
            check(helper, entry(helper, FIRST).isEmpty(), "a broken shop is forgotten");

            placeShop(helper, SECOND, owner, new ItemStack(Items.PRISMARINE_SHARD, 4), "3", "");
            helper.getLevel().removeBlockEntity(helper.absolutePos(SECOND)); // gone without the block noticing
            check(helper, ShopDirectory.get(server).all(server).stream()
                    .noneMatch(found -> found.pos().pos().equals(helper.absolutePos(SECOND))), "a shop that vanished is dropped");
        } finally {
            leave(server, owner);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void findListsTheCheapestShopFirst(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = joinMockPlayer(helper);
        ServerPlayer shopper = joinMockPlayer(helper);
        try {
            ECO.setBalance(server, owner.getUUID(), 100_000, "gametest.setup");
            // $12 for 2 is $6 each; the server shop's $5 for 1 is cheaper.
            ShopBlockEntity player = placeShop(helper, FIRST, owner, new ItemStack(Items.NAUTILUS_SHELL, 2), "12", "4");
            ItemHandlerHelper.insertItem(player.stock(), new ItemStack(Items.NAUTILUS_SHELL, 8), false);
            helper.setBlock(SECOND, ModBlocks.ADMIN_SHOP.get());
            AdminShopBlockEntity serverShop = helper.getBlockEntity(SECOND);
            check(helper, serverShop.trySetTemplate(new ItemStack(Items.NAUTILUS_SHELL, 1)).success(), "set the server shop's item");
            check(helper, serverShop.trySetPrices("5", "1").success(), "set its prices");

            List<Component> out = new ArrayList<>();
            int found = run(server, shopper, out, "shops find nautilus");
            helper.assertValueEqual(found, 2, "both shops match \"nautilus\"");
            // Header, "where to buy", two shops, "where to sell", two shops.
            helper.assertValueEqual(out.size(), 7, "lines: " + out.size());
            check(helper, text(out.get(2)).contains("$5.00") && text(out.get(3)).contains("$12.00"), "cheapest per item first");
            check(helper, text(out.get(5)).contains("$4.00") && text(out.get(6)).contains("$1.00"),
                    "the best price for selling first ($2 each beats $1)");

            helper.assertValueEqual(run(server, shopper, new ArrayList<>(), "shops find minecraft:nautilus_shell"), 2, "a full id works");
            helper.assertValueEqual(run(server, shopper, new ArrayList<>(), "shops find nether star"), 0, "nothing sells nether stars");
            helper.assertValueEqual(run(server, owner, new ArrayList<>(), "shops mine"), 1, "the owner has one shop");
            check(helper, run(server, shopper, new ArrayList<>(), "shops near") >= 2, "both are near");

            shopper.getInventory().selected = 0;
            shopper.getInventory().setItem(0, new ItemStack(Items.NAUTILUS_SHELL));
            helper.assertValueEqual(run(server, shopper, new ArrayList<>(), "shops find"), 2, "finding the held item");
        } finally {
            leave(server, owner, shopper);
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static ShopBlockEntity placeShop(GameTestHelper helper, BlockPos pos, ServerPlayer owner, ItemStack bundle,
                                             String price, String sellPrice) {
        helper.setBlock(pos, ModBlocks.SHOP.get());
        ShopBlockEntity shop = helper.getBlockEntity(pos);
        shop.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        check(helper, shop.trySetTemplate(bundle).success(), "set item");
        check(helper, shop.trySetPrices(price, sellPrice).success(), "set prices");
        return shop;
    }

    private static Optional<ShopDirectory.Entry> entry(GameTestHelper helper, BlockPos pos) {
        GlobalPos at = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos));
        return ShopDirectory.get(helper.getLevel().getServer()).entries().stream()
                .filter(entry -> entry.pos().equals(at)).findFirst();
    }

    /** Runs a command as {@code player}, collecting what it says into {@code out}. Returns the command's result. */
    private static int run(MinecraftServer server, ServerPlayer player, List<Component> out, String command)
            throws CommandSyntaxException {
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                out.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = player.createCommandSourceStack().withSource(capture);
        return server.getCommands().getDispatcher().execute(command, source);
    }

    /** All the literal text and plain arguments in a message, without needing the lang file. */
    private static String text(Component component) {
        StringBuilder text = new StringBuilder();
        collect(component, text);
        return text.toString();
    }

    private static void collect(Component component, StringBuilder text) {
        if (component.getContents() instanceof PlainTextContents plain) {
            text.append(plain.text());
        } else if (component.getContents() instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component nested) {
                    collect(nested, text);
                } else {
                    text.append(' ').append(arg).append(' ');
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            collect(sibling, text);
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

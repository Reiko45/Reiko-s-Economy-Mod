package com.reiko.economy.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.shop.ShopDirectory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /shops}: find where to buy or sell an item, list the shops near you, or check on your own shops.
 * Works for shops anywhere in the world, loaded or not (see {@link ShopDirectory}).
 */
final class ShopCommands {
    /** Lines per section of {@code /shops find}, and per page of the other lists. */
    static final int PAGE = 8;

    private ShopCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> shops() {
        return Commands.literal("shops")
                .requires(EconomyPermissions.check(EconomyPermissions.SHOPS, 0))
                .executes(ctx -> near(ctx.getSource(), 1))
                .then(Commands.literal("near")
                        .executes(ctx -> near(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> near(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("find")
                        .executes(ctx -> findHeld(ctx.getSource()))
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        tradedItemNames(ctx.getSource().getServer()), builder))
                                .executes(ctx -> find(ctx.getSource(), StringArgumentType.getString(ctx, "item")))))
                .then(Commands.literal("mine")
                        .executes(ctx -> mine(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> mine(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))));
    }

    // ---- /shops find ----

    private static int findHeld(CommandSourceStack source) throws CommandSyntaxException {
        ItemStack held = source.getPlayerOrException().getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.translatable("commands.reikoeconomy.shops.hold_item"));
            return 0;
        }
        return show(source, held.getHoverName(), entry -> ItemStack.isSameItem(entry.template(), held));
    }

    private static int find(CommandSourceStack source, String query) {
        List<ShopDirectory.Entry> shops = ShopDirectory.get(source.getServer()).all(source.getServer());
        String wanted = query.trim().toLowerCase(Locale.ROOT);
        String asId = wanted.replace(' ', '_');
        // An exact item id ("diamond" or "minecraft:diamond") wins; otherwise any item whose id or name contains the text.
        Predicate<ShopDirectory.Entry> exact = entry -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.template().getItem());
            return id.toString().equals(asId) || id.getPath().equals(asId)
                    || entry.template().getHoverName().getString().toLowerCase(Locale.ROOT).equals(wanted);
        };
        Predicate<ShopDirectory.Entry> partial = entry -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.template().getItem());
            return id.getPath().contains(asId) || entry.template().getHoverName().getString().toLowerCase(Locale.ROOT).contains(wanted);
        };
        Predicate<ShopDirectory.Entry> matches = shops.stream().anyMatch(exact) ? exact : partial;
        List<ShopDirectory.Entry> found = shops.stream().filter(matches).toList();
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.reikoeconomy.shops.no_match", query.trim()));
            return 0;
        }
        // Name the item if every match is the same item, or else repeat what was searched for.
        boolean oneItem = found.stream().allMatch(entry -> entry.template().is(found.get(0).template().getItem()));
        Component title = oneItem ? found.get(0).template().getHoverName() : Component.literal("\"" + query.trim() + "\"");
        return show(source, title, matches);
    }

    private static int show(CommandSourceStack source, Component title, Predicate<ShopDirectory.Entry> matches) {
        MinecraftServer server = source.getServer();
        List<ShopDirectory.Entry> found = ShopDirectory.get(server).all(server).stream().filter(matches).toList();
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.reikoeconomy.shops.no_match", title));
            return 0;
        }
        Viewer viewer = Viewer.of(source);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("commands.reikoeconomy.shops.find_header", title).withStyle(ChatFormatting.GOLD));

        // Where to buy: in stock first, then cheapest per item, then nearest.
        List<ShopDirectory.Entry> selling = found.stream().filter(ShopDirectory.Entry::sells)
                .sorted(Comparator.comparing((ShopDirectory.Entry entry) -> !entry.inStock())
                        .thenComparingDouble(ShopDirectory.Entry::pricePerItem)
                        .thenComparingDouble(viewer::distanceSort))
                .toList();
        lines.add(Component.translatable("commands.reikoeconomy.shops.buy_header").withStyle(ChatFormatting.YELLOW));
        addSection(lines, selling, entry -> line(server, entry, viewer, Mode.BUY), "commands.reikoeconomy.shops.nobody_sells");

        // Where to sell: shops still buying first, then the best price per item, then nearest.
        List<ShopDirectory.Entry> buying = found.stream().filter(ShopDirectory.Entry::buys)
                .sorted(Comparator.comparing((ShopDirectory.Entry entry) -> wantedBundles(server, entry) == 0)
                        .thenComparing(Comparator.comparingDouble(ShopDirectory.Entry::sellPricePerItem).reversed())
                        .thenComparingDouble(viewer::distanceSort))
                .toList();
        lines.add(Component.translatable("commands.reikoeconomy.shops.sell_header").withStyle(ChatFormatting.YELLOW));
        addSection(lines, buying, entry -> line(server, entry, viewer, Mode.SELL), "commands.reikoeconomy.shops.nobody_buys");

        lines.forEach(line -> source.sendSuccess(() -> line, false));
        return found.size();
    }

    private static void addSection(List<Component> lines, List<ShopDirectory.Entry> entries,
                                   Function<ShopDirectory.Entry, Component> format, String emptyKey) {
        if (entries.isEmpty()) {
            lines.add(Component.literal(" ").append(Component.translatable(emptyKey)).withStyle(ChatFormatting.GRAY));
            return;
        }
        entries.stream().limit(PAGE).map(format).forEach(lines::add);
        if (entries.size() > PAGE) {
            lines.add(Component.translatable("commands.reikoeconomy.shops.more", entries.size() - PAGE).withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- /shops near and /shops mine ----

    private static int near(CommandSourceStack source, int page) {
        MinecraftServer server = source.getServer();
        Viewer viewer = Viewer.of(source);
        List<ShopDirectory.Entry> shops = ShopDirectory.get(server).all(server).stream()
                .filter(entry -> entry.pos().dimension().equals(viewer.dimension()))
                .sorted(Comparator.comparingDouble(viewer::distanceSort))
                .toList();
        if (shops.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.shops.near_none"), false);
            return 0;
        }
        return showPage(source, shops, page, "commands.reikoeconomy.shops.near_header", entry -> line(server, entry, viewer, Mode.BOTH));
    }

    private static int mine(CommandSourceStack source, int page) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Viewer viewer = Viewer.of(source);
        List<ShopDirectory.Entry> shops = ShopDirectory.get(server).all(server).stream()
                .filter(entry -> player.getUUID().equals(entry.owner()))
                .sorted(Comparator.comparingDouble(viewer::distanceSort))
                .toList();
        if (shops.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.shops.mine_none"), false);
            return 0;
        }
        return showPage(source, shops, page, "commands.reikoeconomy.shops.mine_header", entry -> line(server, entry, viewer, Mode.BOTH));
    }

    private static int showPage(CommandSourceStack source, List<ShopDirectory.Entry> shops, int page, String headerKey,
                                Function<ShopDirectory.Entry, Component> format) {
        int pages = (shops.size() + PAGE - 1) / PAGE;
        int shown = Math.min(page, pages);
        source.sendSuccess(() -> Component.translatable(headerKey, shops.size(), shown, pages).withStyle(ChatFormatting.GOLD), false);
        shops.stream().skip((long) (shown - 1) * PAGE).limit(PAGE).map(format).forEach(line -> source.sendSuccess(() -> line, false));
        return shops.size();
    }

    // ---- one shop's line ----

    private enum Mode {
        /** Where to buy: the price players pay. */
        BUY,
        /** Where to sell: what the shop pays. */
        SELL,
        /** Both prices. */
        BOTH
    }

    /**
     * "16 × Cobblestone for $5.00 · Steve · 120 blocks NE", with a status at the end when the shop can't trade
     * right now. Hover for the coordinates and stock; click to copy the coordinates.
     */
    private static Component line(MinecraftServer server, ShopDirectory.Entry entry, Viewer viewer, Mode mode) {
        EconomyService eco = EconomyService.INSTANCE;
        Component bundle = Component.translatable("commands.reikoeconomy.shops.bundle", entry.template().getCount(),
                entry.template().getHoverName());
        Component prices = switch (mode) {
            case BUY -> Component.translatable("commands.reikoeconomy.shops.line.buy", bundle, eco.format(entry.price()));
            case SELL -> Component.translatable("commands.reikoeconomy.shops.line.sell", bundle, eco.format(entry.sellPrice()));
            case BOTH -> entry.sells() && entry.buys()
                    ? Component.translatable("commands.reikoeconomy.shops.line.both", bundle, eco.format(entry.price()),
                            eco.format(entry.sellPrice()))
                    : entry.sells()
                            ? Component.translatable("commands.reikoeconomy.shops.line.buy_only", bundle, eco.format(entry.price()))
                            : Component.translatable("commands.reikoeconomy.shops.line.sell_only", bundle, eco.format(entry.sellPrice()));
        };
        Component who = entry.serverShop()
                ? Component.translatable("commands.reikoeconomy.shops.server_shop")
                : viewer.isOwner(entry)
                        ? Component.translatable("commands.reikoeconomy.shops.yours")
                        : Component.literal(entry.ownerName());

        MutableComponent text = Component.literal(" ")
                .append(prices.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(who.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(viewer.whereIs(entry.pos()).copy().withStyle(ChatFormatting.GRAY));
        for (Component status : statuses(server, entry, mode)) {
            text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(status);
        }

        BlockPos pos = entry.pos().pos();
        MutableComponent hover = Component.translatable("commands.reikoeconomy.shops.hover.pos", pos.getX(), pos.getY(), pos.getZ(),
                dimensionName(entry.pos().dimension()));
        if (entry.serverShop()) {
            hover.append("\n").append(Component.translatable("commands.reikoeconomy.shops.hover.unlimited"));
        } else {
            if (entry.sells()) {
                hover.append("\n").append(Component.translatable("commands.reikoeconomy.shops.hover.stock", entry.stockBundles()));
            }
            if (entry.buys()) {
                hover.append("\n").append(Component.translatable("commands.reikoeconomy.shops.hover.wants", wantedBundles(server, entry)));
            }
        }
        hover.append("\n").append(Component.translatable("commands.reikoeconomy.shops.hover.copy").withStyle(ChatFormatting.GRAY));
        return text.withStyle(Style.EMPTY
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pos.getX() + " " + pos.getY() + " " + pos.getZ())));
    }

    /** "sold out", "full", "can't afford to buy": what stops the shop trading right now, for the prices shown. */
    private static List<Component> statuses(MinecraftServer server, ShopDirectory.Entry entry, Mode mode) {
        List<Component> statuses = new ArrayList<>();
        if (entry.serverShop()) {
            return statuses;
        }
        if (mode != Mode.SELL && entry.sells() && entry.stockBundles() == 0) {
            statuses.add(Component.translatable("commands.reikoeconomy.shops.sold_out").withStyle(ChatFormatting.RED));
        }
        if (mode != Mode.BUY && entry.buys()) {
            if (entry.roomBundles() == 0) {
                statuses.add(Component.translatable("commands.reikoeconomy.shops.full").withStyle(ChatFormatting.RED));
            } else if (wantedBundles(server, entry) == 0) {
                statuses.add(Component.translatable("commands.reikoeconomy.shops.cant_afford").withStyle(ChatFormatting.RED));
            }
        }
        return statuses;
    }

    /** Bundles a shop can still buy: its free space, and what the owner can afford. Server shops always buy. */
    static int wantedBundles(MinecraftServer server, ShopDirectory.Entry entry) {
        if (entry.serverShop()) {
            return Integer.MAX_VALUE;
        }
        if (!entry.buys() || entry.owner() == null) {
            return 0;
        }
        long affordable = EconomyService.INSTANCE.getBalance(server, entry.owner()) / entry.sellPrice();
        return (int) Math.min(entry.roomBundles(), affordable);
    }

    static Component dimensionName(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return Component.translatable("commands.reikoeconomy.shops.dimension.overworld");
        }
        if (dimension == Level.NETHER) {
            return Component.translatable("commands.reikoeconomy.shops.dimension.nether");
        }
        if (dimension == Level.END) {
            return Component.translatable("commands.reikoeconomy.shops.dimension.end");
        }
        return Component.literal(dimension.location().toString());
    }

    /** Items that some shop buys or sells, for tab completion: "diamond" for vanilla items, the full id for others. */
    private static Set<String> tradedItemNames(MinecraftServer server) {
        Set<String> names = new LinkedHashSet<>();
        for (ShopDirectory.Entry entry : ShopDirectory.get(server).entries()) {
            if (entry.sells() || entry.buys()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.template().getItem());
                names.add(id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? id.getPath() : id.toString());
            }
        }
        return names;
    }

    /** Who's asking and where they are, for distances, directions and "your shop". */
    record Viewer(ResourceKey<Level> dimension, Vec3 position, @Nullable UUID id) {
        static Viewer of(CommandSourceStack source) {
            ServerPlayer player = source.getPlayer();
            return new Viewer(source.getLevel().dimension(), source.getPosition(), player == null ? null : player.getUUID());
        }

        boolean isOwner(ShopDirectory.Entry entry) {
            return id != null && id.equals(entry.owner());
        }

        /** Distance for sorting: shops in other dimensions go last. */
        double distanceSort(ShopDirectory.Entry entry) {
            if (!entry.pos().dimension().equals(dimension)) {
                return Double.MAX_VALUE;
            }
            return position.distanceToSqr(entry.pos().pos().getCenter());
        }

        /** "120 blocks NE", "right here", or "in the Nether". */
        Component whereIs(GlobalPos pos) {
            if (!pos.dimension().equals(dimension)) {
                return Component.translatable("commands.reikoeconomy.shops.elsewhere", dimensionName(pos.dimension()));
            }
            Vec3 center = pos.pos().getCenter();
            double dx = center.x - position.x;
            double dz = center.z - position.z;
            int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            if (distance < 4) {
                return Component.translatable("commands.reikoeconomy.shops.here");
            }
            // North is -z. Eight directions, 45 degrees each, starting from north and going clockwise.
            double degrees = Math.toDegrees(Mth.atan2(dx, -dz));
            int octant = Math.floorMod((int) Math.round(degrees / 45.0), 8);
            String[] directions = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
            return Component.translatable("commands.reikoeconomy.shops.distance", distance,
                    Component.translatable("commands.reikoeconomy.shops.dir." + directions[octant]));
        }
    }
}

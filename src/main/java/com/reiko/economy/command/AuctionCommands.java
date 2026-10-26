package com.reiko.economy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.auction.AuctionRules;
import com.reiko.economy.auction.AuctionService;
import com.reiko.economy.core.Money;
import com.reiko.economy.shop.ShopActionResult;
import java.util.Arrays;
import java.util.Collection;
import java.util.OptionalLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /ah}: open the auction house; {@code /ah sell <price>} and {@code /ah auction <starting bid> <hours>}:
 * put up what's in your hand; {@code /ah collect}. Admins: {@code /eco auction clear <player>}.
 */
final class AuctionCommands {
    private static final SimpleCommandExceptionType INVALID_AMOUNT = new SimpleCommandExceptionType(
            Component.translatable(TransactionResult.INVALID_AMOUNT.translationKey()));
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(
            Component.translatable("commands.reikoeconomy.one_player"));

    private AuctionCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> ah() {
        return Commands.literal("ah")
                .requires(EconomyPermissions.check(EconomyPermissions.AUCTION, 0))
                .executes(ctx -> {
                    AuctionService.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", StringArgumentType.word())
                                .executes(ctx -> sellHeld(ctx, 0))))
                .then(Commands.literal("auction")
                        .then(Commands.argument("starting_bid", StringArgumentType.word())
                                .then(Commands.argument("hours", IntegerArgumentType.integer(1, 48))
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(AuctionRules.AUCTION_HOURS).mapToObj(String::valueOf), builder))
                                        .executes(ctx -> sellHeld(ctx, IntegerArgumentType.getInteger(ctx, "hours"))))))
                .then(Commands.literal("collect")
                        .executes(ctx -> report(ctx.getSource(), AuctionService.collect(ctx.getSource().getPlayerOrException()))));
    }

    /** Puts the item in the player's hand up for sale (hours 0) or auction. */
    private static int sellHeld(CommandContext<CommandSourceStack> ctx, int hours) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String text = StringArgumentType.getString(ctx, hours == 0 ? "price" : "starting_bid");
        OptionalLong price = Money.parsePositive(text);
        if (price.isEmpty()) {
            throw INVALID_AMOUNT.create();
        }
        ShopActionResult result = AuctionService.list(player, player.getMainHandItem(), price.getAsLong(), hours);
        if (result.success()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        return report(ctx.getSource(), result);
    }

    private static int report(CommandSourceStack source, ShopActionResult result) {
        if (result.success()) {
            source.sendSuccess(result::message, false);
            return 1;
        }
        source.sendFailure(result.message());
        return 0;
    }

    /** {@code /eco auction clear <player>}: takes down a player's listings and puts the items in their Collect tab. */
    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("auction")
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> {
                                    Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
                                    if (profiles.size() != 1) {
                                        throw ONE_PLAYER.create();
                                    }
                                    GameProfile profile = profiles.iterator().next();
                                    int removed = AuctionService.removeAll(ctx.getSource().getServer(), profile.getId());
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.auction.cleared",
                                            removed, profile.getName()), true);
                                    return removed;
                                })));
    }
}

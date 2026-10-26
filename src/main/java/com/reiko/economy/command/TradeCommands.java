package com.reiko.economy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.trade.TradeService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /trade <player>}: ask someone to trade. {@code /trade accept|deny [player]}: answer a request (the
 * latest one if no name is given). {@code /trade cancel}: take back the request you sent.
 */
final class TradeCommands {
    private TradeCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> trade() {
        return Commands.literal("trade")
                .requires(EconomyPermissions.check(EconomyPermissions.TRADE, 0))
                .then(Commands.literal("accept")
                        .executes(ctx -> report(ctx.getSource(), TradeService.accept(ctx.getSource().getPlayerOrException(), null)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(requesters(ctx.getSource()), builder))
                                .executes(ctx -> report(ctx.getSource(), TradeService.accept(ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("deny")
                        .executes(ctx -> report(ctx.getSource(), TradeService.deny(ctx.getSource().getPlayerOrException(), null)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(requesters(ctx.getSource()), builder))
                                .executes(ctx -> report(ctx.getSource(), TradeService.deny(ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("cancel")
                        .executes(ctx -> report(ctx.getSource(), TradeService.withdraw(ctx.getSource().getPlayerOrException()))))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> report(ctx.getSource(), TradeService.request(ctx.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(ctx, "player")))));
    }

    private static java.util.List<String> requesters(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? java.util.List.of() : TradeService.requestersOf(player);
    }

    private static int report(CommandSourceStack source, ShopActionResult result) throws CommandSyntaxException {
        if (!result.success()) {
            source.sendFailure(result.message());
            return 0;
        }
        if (!TradeService.isSilent(result)) {
            source.sendSuccess(result::message, false);
        }
        return 1;
    }
}

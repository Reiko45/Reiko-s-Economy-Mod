package com.reiko.economy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.core.CreditLedger;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** {@code /credit} for players, and {@code /eco credit ...} for admins. */
final class CreditCommands {
    private static final SimpleCommandExceptionType INVALID_AMOUNT = new SimpleCommandExceptionType(
            Component.translatable(TransactionResult.INVALID_AMOUNT.translationKey()));
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(
            Component.translatable("commands.reikoeconomy.one_player"));

    private CreditCommands() {
    }

    /** {@code /credit}: the player's own credit card account. */
    static LiteralArgumentBuilder<CommandSourceStack> credit() {
        return Commands.literal("credit")
                .requires(EconomyPermissions.check(EconomyPermissions.CREDIT, 0))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return show(ctx.getSource(), player.getUUID(), Component.translatable("commands.reikoeconomy.credit.none_self"));
                });
    }

    /** {@code /eco credit <player> [limit <amount> | forgive | statement]}. */
    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("credit")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> {
                            GameProfile profile = onePlayer(ctx);
                            return show(ctx.getSource(), profile.getId(),
                                    Component.translatable("commands.reikoeconomy.credit.none", profile.getName()));
                        })
                        .then(Commands.literal("limit")
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(CreditCommands::setLimit)))
                        .then(Commands.literal("forgive")
                                .executes(CreditCommands::forgive))
                        .then(Commands.literal("statement")
                                .executes(CreditCommands::statement)));
    }

    private static int show(CommandSourceStack source, UUID owner, Component none) {
        MinecraftServer server = source.getServer();
        Optional<CreditLedger.Account> account = CreditLedger.get(server).find(owner);
        if (account.isEmpty()) {
            source.sendSuccess(() -> none, false);
            return 0;
        }
        for (Component line : CreditService.describe(server, account.get())) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int setLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile profile = onePlayer(ctx);
        OptionalLong limit = Money.parse(StringArgumentType.getString(ctx, "amount"));
        if (limit.isEmpty()) {
            throw INVALID_AMOUNT.create();
        }
        if (!CreditService.setLimit(ctx.getSource().getServer(), profile.getId(), limit.getAsLong())) {
            ctx.getSource().sendFailure(Component.translatable("commands.reikoeconomy.credit.none", profile.getName()));
            return 0;
        }
        String formatted = EconomyService.INSTANCE.format(limit.getAsLong());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.credit.limit", profile.getName(), formatted), true);
        return 1;
    }

    private static int forgive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile profile = onePlayer(ctx);
        if (!CreditService.forgive(ctx.getSource().getServer(), profile.getId())) {
            ctx.getSource().sendFailure(Component.translatable("commands.reikoeconomy.credit.none", profile.getName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.credit.forgiven", profile.getName()), true);
        return 1;
    }

    private static int statement(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        GameProfile profile = onePlayer(ctx);
        if (!CreditService.statementNow(ctx.getSource().getServer(), profile.getId())) {
            ctx.getSource().sendFailure(Component.translatable("commands.reikoeconomy.credit.none", profile.getName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.credit.statement", profile.getName()), true);
        return 1;
    }

    private static GameProfile onePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            throw ONE_PLAYER.create();
        }
        return profiles.iterator().next();
    }
}

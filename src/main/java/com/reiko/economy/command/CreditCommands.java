package com.reiko.economy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.ChatFormatting;
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

    private static final int HISTORY_PAGE = 10;

    /** {@code /credit}: the player's own credit card account; {@code /credit history [page]}: the itemized bill. */
    static LiteralArgumentBuilder<CommandSourceStack> credit() {
        return Commands.literal("credit")
                .requires(EconomyPermissions.check(EconomyPermissions.CREDIT, 0))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return show(ctx.getSource(), player.getUUID(), Component.translatable("commands.reikoeconomy.credit.none_self"));
                })
                .then(Commands.literal("history")
                        .executes(ctx -> history(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> history(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))));
    }

    private static int history(CommandSourceStack source, int page) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<CreditLedger.Account> account = CreditLedger.get(source.getServer()).find(player.getUUID());
        if (account.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.credit.none_self"), false);
            return 0;
        }
        List<CreditLedger.HistoryEntry> lines = account.get().history();
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.credit.history.empty"), false);
            return 0;
        }
        int pages = (lines.size() + HISTORY_PAGE - 1) / HISTORY_PAGE;
        int shown = Math.min(page, pages);
        source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.credit.history.header", shown, pages)
                .withStyle(ChatFormatting.GOLD), false);
        EconomyService eco = EconomyService.INSTANCE;
        for (CreditLedger.HistoryEntry line : lines.subList((shown - 1) * HISTORY_PAGE, Math.min(lines.size(), shown * HISTORY_PAGE))) {
            long day = line.time() / 24_000L + 1;
            Component amount = line.amount() > 0 ? Component.literal(eco.format(line.amount())).withStyle(ChatFormatting.WHITE)
                    : line.amount() < 0 ? Component.literal("-" + eco.format(-line.amount())).withStyle(ChatFormatting.GREEN)
                    : Component.empty();
            Component text = Component.translatable("commands.reikoeconomy.credit.history.line", day, line.description(), amount)
                    .withStyle(line.amount() == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY);
            source.sendSuccess(() -> text, false);
        }
        if (shown < pages) {
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.credit.history.more", shown + 1)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return lines.size();
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

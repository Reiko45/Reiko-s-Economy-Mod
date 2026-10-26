package com.reiko.economy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.SharedAccountService;
import com.reiko.economy.core.SharedAccounts;
import com.reiko.economy.shop.ShopActionResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /account}: shared accounts. Everyone: list, create, info, deposit, withdraw, pay, leave, history. The
 * owner: add, remove, owner (hand it over) and close. Admins: {@code /eco account list|info|history|close}.
 */
final class AccountCommands {
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(
            Component.translatable("commands.reikoeconomy.one_player"));

    private AccountCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> account() {
        return Commands.literal("account")
                .requires(EconomyPermissions.check(EconomyPermissions.ACCOUNT, 0))
                .executes(AccountCommands::list)
                .then(Commands.literal("list").executes(AccountCommands::list))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> report(ctx.getSource(), SharedAccountService.create(player(ctx),
                                        StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("info")
                        .then(accountArgument().executes(AccountCommands::info)))
                .then(Commands.literal("deposit")
                        .then(accountArgument()
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(ctx -> report(ctx.getSource(), SharedAccountService.deposit(player(ctx),
                                                account(ctx), StringArgumentType.getString(ctx, "amount")))))))
                .then(Commands.literal("withdraw")
                        .then(accountArgument()
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(ctx -> report(ctx.getSource(), SharedAccountService.withdraw(player(ctx),
                                                account(ctx), StringArgumentType.getString(ctx, "amount")))))))
                .then(Commands.literal("pay")
                        .then(accountArgument()
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(ctx -> report(ctx.getSource(), SharedAccountService.pay(player(ctx),
                                                        account(ctx), profile(ctx), StringArgumentType.getString(ctx, "amount"))))))))
                .then(Commands.literal("add")
                        .then(accountArgument()
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> report(ctx.getSource(), SharedAccountService.addMember(player(ctx),
                                                account(ctx), profile(ctx)))))))
                .then(Commands.literal("remove")
                        .then(accountArgument()
                                .then(memberArgument()
                                        .executes(ctx -> report(ctx.getSource(), SharedAccountService.removeMember(player(ctx),
                                                account(ctx), StringArgumentType.getString(ctx, "member")))))))
                .then(Commands.literal("owner")
                        .then(accountArgument()
                                .then(memberArgument()
                                        .executes(ctx -> report(ctx.getSource(), SharedAccountService.setOwner(player(ctx),
                                                account(ctx), StringArgumentType.getString(ctx, "member")))))))
                .then(Commands.literal("leave")
                        .then(accountArgument()
                                .executes(ctx -> report(ctx.getSource(), SharedAccountService.leave(player(ctx), account(ctx))))))
                .then(Commands.literal("close")
                        .then(accountArgument()
                                .executes(ctx -> report(ctx.getSource(), SharedAccountService.close(player(ctx), account(ctx))))))
                .then(Commands.literal("history")
                        .then(accountArgument()
                                .executes(ctx -> history(ctx, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "page"))))));
    }

    /** The account name, suggesting the accounts the player is in. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> accountArgument() {
        return Commands.argument("account", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    List<String> names = player == null ? List.of()
                            : SharedAccountService.namesOf(ctx.getSource().getServer(), player.getUUID());
                    return SharedSuggestionProvider.suggest(names, builder);
                });
    }

    /** A member's name, suggesting the account's other members. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> memberArgument() {
        return Commands.argument("member", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    List<String> names = player == null ? List.of() : SharedAccountService.otherMembers(
                            ctx.getSource().getServer(), player.getUUID(), StringArgumentType.getString(ctx, "account"));
                    return SharedSuggestionProvider.suggest(names, builder);
                });
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static String account(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "account");
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            throw ONE_PLAYER.create();
        }
        return profiles.iterator().next();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = player(ctx);
        List<Component> lines = SharedAccountService.list(ctx.getSource().getServer(), player.getUUID());
        lines.forEach(line -> ctx.getSource().sendSuccess(() -> line, false));
        return lines.size() - 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = player(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        Optional<SharedAccounts.Account> account = SharedAccountService.memberAccount(server, player.getUUID(), account(ctx));
        if (account.isEmpty()) {
            return report(ctx.getSource(), SharedAccountService.whyNot(server, player.getUUID(), account(ctx)));
        }
        SharedAccountService.describe(server, account.get()).forEach(line -> ctx.getSource().sendSuccess(() -> line, false));
        return 1;
    }

    private static int history(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
        ServerPlayer player = player(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        Optional<SharedAccounts.Account> account = SharedAccountService.memberAccount(server, player.getUUID(), account(ctx));
        if (account.isEmpty()) {
            return report(ctx.getSource(), SharedAccountService.whyNot(server, player.getUUID(), account(ctx)));
        }
        return TransactionHistory.show(ctx.getSource(), account.get().id(), account.get().name(), page);
    }

    private static int report(CommandSourceStack source, ShopActionResult result) {
        if (result.success()) {
            source.sendSuccess(result::message, false);
            return 1;
        }
        source.sendFailure(result.message());
        return 0;
    }

    // ---- admins ----

    /** {@code /eco account list|info|history|close}: look into or close any shared account. */
    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("account")
                .then(Commands.literal("list").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    List<SharedAccounts.Account> all = SharedAccounts.get(server).all();
                    if (all.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.account.none"), false);
                        return 0;
                    }
                    EconomyService eco = EconomyService.INSTANCE;
                    for (SharedAccounts.Account account : all) {
                        String owner = SharedAccountService.nameOf(server, account.owner(), "?");
                        Component line = Component.translatable("commands.reikoeconomy.account.line", account.name(),
                                eco.format(eco.getBalance(server, account.id())), owner, account.members().size());
                        ctx.getSource().sendSuccess(() -> line, false);
                    }
                    return all.size();
                }))
                .then(Commands.literal("info")
                        .then(anyAccountArgument().executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            SharedAccounts.Account account = anyAccount(ctx);
                            SharedAccountService.describe(server, account).forEach(line -> ctx.getSource().sendSuccess(() -> line, false));
                            return 1;
                        })))
                .then(Commands.literal("history")
                        .then(anyAccountArgument()
                                .executes(ctx -> TransactionHistory.show(ctx.getSource(), anyAccount(ctx).id(), anyAccount(ctx).name(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> TransactionHistory.show(ctx.getSource(), anyAccount(ctx).id(),
                                                anyAccount(ctx).name(), IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("close")
                        .then(anyAccountArgument().executes(ctx -> {
                            ShopActionResult result = SharedAccountService.close(ctx.getSource().getServer(), anyAccount(ctx),
                                    ctx.getSource().getTextName());
                            if (result.success()) {
                                ctx.getSource().sendSuccess(result::message, true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(result.message());
                            return 0;
                        })));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> anyAccountArgument() {
        return Commands.argument("account", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        SharedAccounts.get(ctx.getSource().getServer()).all().stream().map(SharedAccounts.Account::name), builder));
    }

    private static SharedAccounts.Account anyAccount(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = account(ctx);
        return SharedAccounts.get(ctx.getSource().getServer()).byName(name).orElseThrow(() ->
                new SimpleCommandExceptionType(Component.translatable("commands.reikoeconomy.account.unknown", name)).create());
    }
}

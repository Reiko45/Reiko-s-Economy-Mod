package com.reiko.economy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Ledger;
import com.reiko.economy.core.Money;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers {@code /balance} ({@code /bal}), {@code /pay}, {@code /baltop} and the admin {@code /eco} command.
 *
 * <p>Commands only parse input and print results; every balance change goes through {@link EconomyService}.
 */
public final class EconomyCommands {
    private static final SimpleCommandExceptionType INVALID_AMOUNT = new SimpleCommandExceptionType(
            Component.translatable(TransactionResult.INVALID_AMOUNT.translationKey()));
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(
            Component.translatable("commands.reikoeconomy.one_player"));

    private EconomyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(balance("balance"));
        dispatcher.register(balance("bal"));
        dispatcher.register(pay());
        dispatcher.register(baltop());
        dispatcher.register(transactions());
        dispatcher.register(eco());
        dispatcher.register(CreditCommands.credit());
    }

    // ---- /transactions (own history) ----

    private static LiteralArgumentBuilder<CommandSourceStack> transactions() {
        return Commands.literal("transactions")
                .requires(EconomyPermissions.check(EconomyPermissions.HISTORY, 0))
                .executes(ctx -> ownHistory(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> ownHistory(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"))));
    }

    private static int ownHistory(CommandSourceStack source, int page) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return TransactionHistory.show(source, player.getUUID(), player.getGameProfile().getName(), page);
    }

    // ---- /balance and /bal ----

    private static LiteralArgumentBuilder<CommandSourceStack> balance(String name) {
        return Commands.literal(name)
                .requires(EconomyPermissions.check(EconomyPermissions.BALANCE, 0))
                .executes(ctx -> showOwnBalance(ctx.getSource()))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .requires(EconomyPermissions.check(EconomyPermissions.BALANCE_OTHERS, 0))
                        .executes(ctx -> showBalances(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player"))));
    }

    private static int showOwnBalance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EconomyService eco = EconomyService.INSTANCE;
        Ledger.Account account = eco.ensureAccount(source.getServer(), player.getUUID(), player.getGameProfile().getName());
        source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.balance.self",
                eco.format(account.balance())), false);
        return 1;
    }

    private static int showBalances(CommandSourceStack source, Collection<GameProfile> profiles) {
        EconomyService eco = EconomyService.INSTANCE;
        int shown = 0;
        for (GameProfile profile : profiles) {
            var account = eco.findAccount(source.getServer(), profile.getId());
            if (account.isEmpty()) {
                source.sendFailure(Component.translatable("commands.reikoeconomy.balance.none", profile.getName()));
                continue;
            }
            long balance = account.get().balance();
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.balance.other",
                    profile.getName(), eco.format(balance)), false);
            shown++;
        }
        return shown;
    }

    // ---- /pay ----

    private static LiteralArgumentBuilder<CommandSourceStack> pay() {
        return Commands.literal("pay")
                .requires(EconomyPermissions.check(EconomyPermissions.PAY, 0))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> pay(ctx.getSource(),
                                        GameProfileArgument.getGameProfiles(ctx, "player"),
                                        StringArgumentType.getString(ctx, "amount")))));
    }

    private static int pay(CommandSourceStack source, Collection<GameProfile> profiles, String amountText)
            throws CommandSyntaxException {
        ServerPlayer payer = source.getPlayerOrException();
        if (profiles.size() != 1) {
            throw ONE_PLAYER.create();
        }
        GameProfile target = profiles.iterator().next();
        long amount = parsePositive(amountText);

        MinecraftServer server = source.getServer();
        EconomyService eco = EconomyService.INSTANCE;
        eco.ensureAccount(server, payer.getUUID(), payer.getGameProfile().getName());

        TransactionResult result = eco.transfer(server, payer.getUUID(), target.getId(), amount, "command.pay");
        if (!result.isSuccess()) {
            source.sendFailure(Component.translatable(result.translationKey()));
            return 0;
        }

        String formatted = eco.format(amount);
        long newBalance = eco.getBalance(server, payer.getUUID());
        source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.pay.sent",
                formatted, target.getName(), eco.format(newBalance)), false);

        ServerPlayer recipient = server.getPlayerList().getPlayer(target.getId());
        if (recipient != null) {
            recipient.sendSystemMessage(Component.translatable("commands.reikoeconomy.pay.received",
                    formatted, payer.getGameProfile().getName()));
        }
        return 1;
    }

    // ---- /baltop ----

    private static LiteralArgumentBuilder<CommandSourceStack> baltop() {
        return Commands.literal("baltop")
                .requires(EconomyPermissions.check(EconomyPermissions.BALTOP, 0))
                .executes(ctx -> baltop(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> baltop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"))));
    }

    private static int baltop(CommandSourceStack source, int page) {
        EconomyService eco = EconomyService.INSTANCE;
        Ledger ledger = Ledger.get(source.getServer());
        List<Ledger.Account> ranked = ledger.ranked();
        if (ranked.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.baltop.empty"), false);
            return 0;
        }

        int pageSize = EconomyConfig.baltopPageSize();
        int pages = (ranked.size() + pageSize - 1) / pageSize;
        int shownPage = Math.min(page, pages);
        int start = (shownPage - 1) * pageSize;
        int end = Math.min(start + pageSize, ranked.size());

        source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.baltop.header", shownPage, pages), false);
        for (int i = start; i < end; i++) {
            Ledger.Account account = ranked.get(i);
            int rank = i + 1;
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.baltop.entry",
                    rank, account.lastKnownName(), eco.format(account.balance())), false);
        }
        long total = ledger.total();
        source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.baltop.total", eco.format(total)), false);
        return end - start;
    }

    // ---- /eco (admin) ----

    private enum AdminAction {
        GIVE, TAKE, SET
    }

    private static LiteralArgumentBuilder<CommandSourceStack> eco() {
        return Commands.literal("eco")
                .requires(EconomyPermissions.check(EconomyPermissions.ADMIN, EconomyPermissions.ADMIN_OP_LEVEL))
                .then(adminAction("give", AdminAction.GIVE))
                .then(adminAction("take", AdminAction.TAKE))
                .then(adminAction("set", AdminAction.SET))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(ctx -> reset(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "targets")))))
                .then(Commands.literal("history")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> adminHistory(ctx, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> adminHistory(ctx, IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(CreditCommands.admin());
    }

    private static int adminHistory(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            throw ONE_PLAYER.create();
        }
        GameProfile profile = profiles.iterator().next();
        return TransactionHistory.show(ctx.getSource(), profile.getId(), profile.getName(), page);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminAction(String name, AdminAction action) {
        return Commands.literal(name)
                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> admin(ctx, action))));
    }

    private static int admin(CommandContext<CommandSourceStack> ctx, AdminAction action) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
        String amountText = StringArgumentType.getString(ctx, "amount");
        long amount = action == AdminAction.SET ? parseNonNegative(amountText) : parsePositive(amountText);

        MinecraftServer server = source.getServer();
        EconomyService eco = EconomyService.INSTANCE;
        int changed = 0;
        for (GameProfile target : targets) {
            // Admins may set up accounts for players who have never joined (as long as the server knows their profile).
            eco.ensureAccount(server, target.getId(), target.getName());
            TransactionResult result = switch (action) {
                case GIVE -> eco.deposit(server, target.getId(), amount, "admin.give");
                case TAKE -> eco.withdraw(server, target.getId(), amount, "admin.take");
                case SET -> eco.setBalance(server, target.getId(), amount, "admin.set");
            };
            if (!result.isSuccess()) {
                source.sendFailure(Component.translatable("commands.reikoeconomy.eco.failed",
                        target.getName(), Component.translatable(result.translationKey())));
                continue;
            }
            String formattedAmount = eco.format(amount);
            String newBalance = eco.format(eco.getBalance(server, target.getId()));
            String key = "commands.reikoeconomy.eco." + action.name().toLowerCase(java.util.Locale.ROOT);
            source.sendSuccess(() -> Component.translatable(key, formattedAmount, target.getName(), newBalance), true);
            notifyBalanceChanged(server, target, newBalance);
            changed++;
        }
        return changed;
    }

    private static int reset(CommandSourceStack source, Collection<GameProfile> targets) {
        MinecraftServer server = source.getServer();
        EconomyService eco = EconomyService.INSTANCE;
        long startingBalance = EconomyConfig.startingBalance();
        int changed = 0;
        for (GameProfile target : targets) {
            eco.ensureAccount(server, target.getId(), target.getName());
            TransactionResult result = eco.setBalance(server, target.getId(), startingBalance, "admin.reset");
            if (!result.isSuccess()) {
                source.sendFailure(Component.translatable("commands.reikoeconomy.eco.failed",
                        target.getName(), Component.translatable(result.translationKey())));
                continue;
            }
            String newBalance = eco.format(startingBalance);
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.eco.reset",
                    target.getName(), newBalance), true);
            notifyBalanceChanged(server, target, newBalance);
            changed++;
        }
        return changed;
    }

    private static void notifyBalanceChanged(MinecraftServer server, GameProfile target, String newBalance) {
        ServerPlayer player = server.getPlayerList().getPlayer(target.getId());
        if (player != null) {
            player.sendSystemMessage(Component.translatable("commands.reikoeconomy.eco.notify", newBalance));
        }
    }

    // ---- helpers ----

    private static long parsePositive(String text) throws CommandSyntaxException {
        OptionalLong amount = Money.parsePositive(text);
        if (amount.isEmpty()) {
            throw INVALID_AMOUNT.create();
        }
        return amount.getAsLong();
    }

    private static long parseNonNegative(String text) throws CommandSyntaxException {
        OptionalLong amount = Money.parse(text);
        if (amount.isEmpty()) {
            throw INVALID_AMOUNT.create();
        }
        return amount.getAsLong();
    }
}

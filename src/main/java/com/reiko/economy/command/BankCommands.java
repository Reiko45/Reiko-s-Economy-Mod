package com.reiko.economy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.LoanLedger;
import com.reiko.economy.core.LoanService;
import com.reiko.economy.core.LoanTerms;
import com.reiko.economy.core.Money;
import com.reiko.economy.core.SavingsLedger;
import com.reiko.economy.core.SavingsService;
import com.reiko.economy.core.TaxRates;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** {@code /bank} for players, and {@code /eco bank <player>} and {@code /eco loan <player> ...} for admins. */
public final class BankCommands {
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(
            Component.translatable("commands.reikoeconomy.one_player"));
    private static final SimpleCommandExceptionType INVALID_AMOUNT = new SimpleCommandExceptionType(
            Component.translatable(TransactionResult.INVALID_AMOUNT.translationKey()));

    private BankCommands() {
    }

    /** {@code /bank}: the player's balance, savings and loan. */
    static LiteralArgumentBuilder<CommandSourceStack> bank() {
        return Commands.literal("bank")
                .requires(EconomyPermissions.check(EconomyPermissions.BANK, 0))
                .executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    EconomyService.INSTANCE.ensureAccount(ctx.getSource().getServer(), player.getUUID(), player.getGameProfile().getName());
                    show(ctx.getSource(), player.getUUID(), Component.translatable("commands.reikoeconomy.bank.header_self"));
                    return 1;
                });
    }

    /** {@code /eco bank <player>} and {@code /eco loan <player> forgive|limit <amount>|due}. */
    static List<LiteralArgumentBuilder<CommandSourceStack>> admin() {
        LiteralArgumentBuilder<CommandSourceStack> bank = Commands.literal("bank")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> {
                            GameProfile profile = one(ctx);
                            show(ctx.getSource(), profile.getId(), Component.translatable("commands.reikoeconomy.bank.header_other", profile.getName()));
                            return 1;
                        }));
        LiteralArgumentBuilder<CommandSourceStack> loan = Commands.literal("loan")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.literal("forgive").executes(ctx -> {
                            GameProfile profile = one(ctx);
                            boolean had = LoanService.forgive(ctx.getSource().getServer(), profile.getId());
                            if (!had) {
                                ctx.getSource().sendFailure(Component.translatable("commands.reikoeconomy.loan.none_other", profile.getName()));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.loan.forgiven", profile.getName()), true);
                            return 1;
                        }))
                        .then(Commands.literal("limit")
                                .then(Commands.argument("amount", StringArgumentType.word()).executes(ctx -> {
                                    GameProfile profile = one(ctx);
                                    OptionalLong amount = Money.parse(StringArgumentType.getString(ctx, "amount"));
                                    if (amount.isEmpty()) {
                                        throw INVALID_AMOUNT.create();
                                    }
                                    LoanService.setLimit(ctx.getSource().getServer(), profile.getId(), amount.getAsLong());
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.loan.limit_set",
                                            profile.getName(), EconomyService.INSTANCE.format(amount.getAsLong())), true);
                                    return 1;
                                })))
                        .then(Commands.literal("due").executes(ctx -> {
                            GameProfile profile = one(ctx);
                            LoanService.dueNow(ctx.getSource().getServer(), profile.getId());
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.loan.due_now", profile.getName()), true);
                            return 1;
                        })));
        return List.of(bank, loan);
    }

    private static GameProfile one(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            throw ONE_PLAYER.create();
        }
        return profiles.iterator().next();
    }

    private static void show(CommandSourceStack source, UUID owner, Component header) {
        MinecraftServer server = source.getServer();
        EconomyService eco = EconomyService.INSTANCE;
        List<Component> lines = new ArrayList<>();
        lines.add(header.copy().withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("commands.reikoeconomy.bank.balance", eco.format(eco.getBalance(server, owner))));

        long savings = SavingsService.balance(server, owner);
        if (EconomyConfig.savingsEnabled()) {
            lines.add(Component.translatable("commands.reikoeconomy.bank.savings", eco.format(savings),
                    TaxRates.percentText(EconomyConfig.savingsInterestBasisPoints()), days(server, SavingsLedger.get(server).nextInterest())));
        } else if (savings > 0) {
            lines.add(Component.translatable("commands.reikoeconomy.bank.savings_off", eco.format(savings)));
        }

        LoanLedger.Borrower loan = LoanLedger.get(server).find(owner).filter(LoanLedger.Borrower::hasLoan).orElse(null);
        LoanTerms terms = EconomyConfig.loanTerms();
        if (loan != null) {
            long due = LoanTerms.stillDue(loan.state());
            lines.add(due > 0
                    ? Component.translatable("commands.reikoeconomy.bank.loan", eco.format(loan.owed()), eco.format(due),
                            days(server, loan.nextDue()))
                    : Component.translatable("commands.reikoeconomy.bank.loan_covered", eco.format(loan.owed()),
                            days(server, loan.nextDue())));
            if (loan.missedAPayment()) {
                lines.add(Component.translatable("commands.reikoeconomy.bank.loan_missed").withStyle(ChatFormatting.RED));
            }
        } else if (EconomyConfig.loansEnabled()) {
            lines.add(Component.translatable("commands.reikoeconomy.bank.no_loan", eco.format(LoanService.limit(server, owner)),
                    TaxRates.percentText(terms.feeBasisPoints()), terms.payments(), terms.periodTicks() / 24_000));
        }
        if (loan != null) {
            lines.add(Component.translatable("commands.reikoeconomy.bank.limit", eco.format(loan.limit())).withStyle(ChatFormatting.GRAY));
        }
        lines.forEach(line -> source.sendSuccess(() -> line, false));
    }

    /** Minecraft days from now until {@code time}, to one decimal place: "3.2". */
    public static String days(MinecraftServer server, long time) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0, time - CreditService.now(server)) / 24_000.0);
    }
}

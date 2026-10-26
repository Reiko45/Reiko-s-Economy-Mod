package com.reiko.economy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Ledger;
import com.reiko.economy.core.SavingsLedger;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxLedger;
import com.reiko.economy.core.TaxRates;
import com.reiko.economy.core.TaxService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** {@code /taxes} for players, and {@code /eco taxes [collect]} for admins. */
final class TaxCommands {
    private TaxCommands() {
    }

    /** {@code /taxes}: the server's tax rates, and the player's next wealth tax. */
    static LiteralArgumentBuilder<CommandSourceStack> taxes() {
        return Commands.literal("taxes")
                .requires(EconomyPermissions.check(EconomyPermissions.TAXES, 0))
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    List<Component> lines = rateLines(source.getServer());
                    ServerPlayer player = source.getPlayer();
                    TaxRates rates = EconomyConfig.taxRates();
                    if (player != null && rates.hasWealthTax()) {
                        long due = rates.wealthTax(EconomyService.INSTANCE.getBalance(source.getServer(), player.getUUID()));
                        lines.add(Component.translatable("commands.reikoeconomy.taxes.your_wealth_tax",
                                EconomyService.INSTANCE.format(due)).withStyle(ChatFormatting.GRAY));
                    }
                    lines.forEach(line -> source.sendSuccess(() -> line, false));
                    return 1;
                });
    }

    /** {@code /eco taxes}: rates and what each tax has taken; {@code /eco taxes collect}: wealth tax now. */
    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("taxes")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    MinecraftServer server = source.getServer();
                    EconomyService eco = EconomyService.INSTANCE;
                    TaxLedger taxes = TaxLedger.get(server);
                    List<Component> lines = rateLines(server);
                    long total = 0;
                    lines.add(Component.translatable("commands.reikoeconomy.taxes.collected_header").withStyle(ChatFormatting.GOLD));
                    for (TaxKind kind : TaxKind.values()) {
                        long collected = taxes.collected(kind);
                        total += collected;
                        lines.add(Component.translatable("commands.reikoeconomy.taxes.collected_line",
                                Component.translatable("commands.reikoeconomy.taxes.kind." + kind.id()), eco.format(collected)));
                    }
                    lines.add(Component.translatable("commands.reikoeconomy.taxes.collected_total", eco.format(total)));
                    lines.add(Component.translatable("commands.reikoeconomy.taxes.money_supply",
                            eco.format(Ledger.get(server).total() + SavingsLedger.get(server).total())).withStyle(ChatFormatting.GRAY));
                    lines.forEach(line -> source.sendSuccess(() -> line, false));
                    return 1;
                })
                .then(Commands.literal("collect")
                        .executes(ctx -> {
                            long taken = TaxService.collectWealthTax(ctx.getSource().getServer(), EconomyConfig.taxRates());
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikoeconomy.taxes.collected_now",
                                    EconomyService.INSTANCE.format(taken)), true);
                            return 1;
                        }));
    }

    private static List<Component> rateLines(MinecraftServer server) {
        TaxRates rates = EconomyConfig.taxRates();
        EconomyService eco = EconomyService.INSTANCE;
        List<Component> lines = new ArrayList<>();
        if (rates.salesBasisPoints() == 0 && rates.payFeeBasisPoints() == 0 && !rates.hasWealthTax()
                && rates.atmFlatFee() == 0 && rates.atmFeeBasisPoints() == 0) {
            lines.add(Component.translatable("commands.reikoeconomy.taxes.none"));
            return lines;
        }
        lines.add(Component.translatable("commands.reikoeconomy.taxes.header").withStyle(ChatFormatting.GOLD));
        if (rates.salesBasisPoints() > 0) {
            lines.add(Component.translatable("commands.reikoeconomy.taxes.sales", TaxRates.percentText(rates.salesBasisPoints())));
        }
        if (rates.payFeeBasisPoints() > 0) {
            lines.add(Component.translatable("commands.reikoeconomy.taxes.pay", TaxRates.percentText(rates.payFeeBasisPoints())));
        }
        if (rates.atmFlatFee() > 0 && rates.atmFeeBasisPoints() > 0) {
            lines.add(Component.translatable("commands.reikoeconomy.taxes.atm_both", eco.format(rates.atmFlatFee()),
                    TaxRates.percentText(rates.atmFeeBasisPoints())));
        } else if (rates.atmFlatFee() > 0) {
            lines.add(Component.translatable("commands.reikoeconomy.taxes.atm_flat", eco.format(rates.atmFlatFee())));
        } else if (rates.atmFeeBasisPoints() > 0) {
            lines.add(Component.translatable("commands.reikoeconomy.taxes.atm_percent", TaxRates.percentText(rates.atmFeeBasisPoints())));
        }
        if (rates.hasWealthTax()) {
            long next = TaxLedger.get(server).nextWealthTax();
            // Nothing is scheduled until the world's first tick; then it's a full period away.
            long ticksLeft = next == 0 ? rates.wealthPeriodTicks() : Math.max(0, next - CreditService.now(server));
            double days = ticksLeft / 24_000.0;
            lines.add(Component.translatable("commands.reikoeconomy.taxes.wealth", TaxRates.percentText(rates.wealthBasisPoints()),
                    eco.format(rates.wealthThreshold()), rates.wealthPeriodTicks() / 24_000,
                    String.format(Locale.ROOT, "%.1f", days)));
        }
        return lines;
    }
}

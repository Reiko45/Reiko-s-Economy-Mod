package com.reiko.economy.core;

import com.reiko.economy.config.EconomyConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Collects taxes. Sales tax, the {@code /pay} fee and the ATM fee are taken where the payment happens (see
 * {@link com.reiko.economy.shop.ShopPayments}, the {@code /pay} command and {@link com.reiko.economy.bank.AtmService})
 * and reported here; the wealth tax runs on a schedule from here. Taxes leave the economy: nobody receives them.
 */
public final class TaxService {
    private TaxService() {
    }

    /** Notes tax that was taken somewhere, for {@code /eco taxes}. */
    public static void collected(MinecraftServer server, TaxKind kind, long amount) {
        TaxLedger.get(server).addCollected(kind, amount);
    }

    /**
     * Takes a bank tax: withdraws {@code amount} from the player's account under the tax's reason and notes it.
     * Returns false (taking nothing) if the account can't cover it.
     */
    public static boolean take(MinecraftServer server, UUID player, TaxKind kind, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (!EconomyService.INSTANCE.withdraw(server, player, amount, kind.reason()).isSuccess()) {
            return false;
        }
        collected(server, kind, amount);
        return true;
    }

    // ---- wealth tax ----

    /**
     * Takes the wealth tax from every account if it's due. The first call only schedules the first collection.
     * If the server was off for longer than a period, it's taken once, not once for every missed period.
     */
    public static void runWealthTax(MinecraftServer server, long now) {
        TaxRates rates = EconomyConfig.taxRates();
        TaxLedger taxes = TaxLedger.get(server);
        if (taxes.nextWealthTax() == 0) {
            taxes.setNextWealthTax(now + rates.wealthPeriodTicks());
            return;
        }
        if (now < taxes.nextWealthTax()) {
            return;
        }
        taxes.setNextWealthTax(now + rates.wealthPeriodTicks());
        if (rates.hasWealthTax()) {
            collectWealthTax(server, rates);
        }
    }

    /** Takes the wealth tax from every account now, whatever the schedule. Returns the total taken. */
    public static long collectWealthTax(MinecraftServer server, TaxRates rates) {
        TaxLedger taxes = TaxLedger.get(server);
        long total = 0;
        for (Ledger.Account account : List.copyOf(Ledger.get(server).accounts())) {
            long tax = rates.wealthTax(account.balance());
            if (tax <= 0 || !take(server, account.owner(), TaxKind.WEALTH, tax)) {
                continue;
            }
            total += tax;
            ServerPlayer player = server.getPlayerList().getPlayer(account.owner());
            if (player != null) {
                player.sendSystemMessage(wealthTaxMessage(tax, rates).withStyle(ChatFormatting.GOLD));
            } else {
                taxes.addUnseenWealthTax(account.owner(), tax);
            }
        }
        return total;
    }

    private static MutableComponent wealthTaxMessage(long tax, TaxRates rates) {
        EconomyService eco = EconomyService.INSTANCE;
        return Component.translatable("tax.reikoeconomy.wealth.taken", eco.format(tax),
                TaxRates.percentText(rates.wealthBasisPoints()), eco.format(rates.wealthThreshold()));
    }

    /** Tells a player who just joined about wealth tax taken while they were away. */
    public static void deliver(ServerPlayer player) {
        long unseen = TaxLedger.get(player.server).takeUnseenWealthTax(player.getUUID());
        if (unseen > 0) {
            player.sendSystemMessage(Component.translatable("tax.reikoeconomy.wealth.while_away",
                    EconomyService.INSTANCE.format(unseen)).withStyle(ChatFormatting.GOLD));
        }
    }
}

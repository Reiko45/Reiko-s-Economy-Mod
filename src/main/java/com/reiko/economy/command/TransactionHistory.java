package com.reiko.economy.command;

import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Ledger;
import com.reiko.economy.core.TransactionLog;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** Prints one player's recent transactions, newest first, from their point of view. */
final class TransactionHistory {
    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private TransactionHistory() {
    }

    static int show(CommandSourceStack source, UUID player, String name, int page) {
        MinecraftServer server = source.getServer();
        List<TransactionLog.Entry> entries = TransactionLog.get(server).forPlayer(player);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.history.empty", name), false);
            return 0;
        }
        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int shownPage = Math.min(page, pages);
        int start = (shownPage - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());

        source.sendSuccess(() -> Component.translatable("commands.reikoeconomy.history.header", name, shownPage, pages), false);
        Ledger ledger = Ledger.get(server);
        for (TransactionLog.Entry entry : entries.subList(start, end)) {
            Component line = describe(entry, player, ledger);
            source.sendSuccess(() -> line, false);
        }
        return end - start;
    }

    private static Component describe(TransactionLog.Entry entry, UUID viewer, Ledger ledger) {
        EconomyService eco = EconomyService.INSTANCE;
        String amount = eco.format(entry.amount());
        MutableComponent change = switch (entry.type()) {
            case TRANSFER -> viewer.equals(entry.to())
                    ? Component.translatable("commands.reikoeconomy.history.received_from", amount, nameOf(ledger, entry.from()))
                            .withStyle(ChatFormatting.GREEN)
                    : Component.translatable("commands.reikoeconomy.history.sent_to", amount, nameOf(ledger, entry.to()))
                            .withStyle(ChatFormatting.RED);
            case DEPOSIT -> Component.translatable("commands.reikoeconomy.history.deposit", amount).withStyle(ChatFormatting.GREEN);
            case WITHDRAW -> Component.translatable("commands.reikoeconomy.history.withdraw", amount).withStyle(ChatFormatting.RED);
            case SET -> Component.translatable("commands.reikoeconomy.history.set", amount).withStyle(ChatFormatting.YELLOW);
        };
        Component reason = Component.translatableWithFallback("reikoeconomy.reason." + entry.reason(), entry.reason());
        return Component.literal(TIME.format(Instant.ofEpochMilli(entry.time())) + "  ").withStyle(ChatFormatting.GRAY)
                .append(change)
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(reason.copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
    }

    private static String nameOf(Ledger ledger, @Nullable UUID player) {
        if (player == null) {
            return "?";
        }
        return ledger.find(player).map(Ledger.Account::lastKnownName).filter(n -> !n.isEmpty())
                .orElse(player.toString().substring(0, 8));
    }
}

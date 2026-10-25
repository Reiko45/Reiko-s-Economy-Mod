package com.reiko.economy.core;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.bank.CashWallet;
import com.reiko.economy.bank.InventorySpace;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.item.BankCard;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.CreditCardItem;
import com.reiko.economy.registry.ModDataComponents;
import com.reiko.economy.registry.ModItems;
import com.reiko.economy.shop.InventorySync;
import com.reiko.economy.shop.ShopActionResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Credit cards: issuing them, spending on credit, paying the bill, cash advances and the statement cycle.
 *
 * <p>Spending on credit doesn't touch the cardholder's bank account. When a player shop is paid by credit card,
 * its owner is paid straight away and the cardholder owes that amount on their card. Every few days (the
 * statement period) a statement is made; {@link CreditStatement} decides interest, fees and limit changes.
 *
 * <p>Like debit cards, each account has one working credit card, a new one cancels the old one, anyone with
 * the card and its PIN can use it, and {@value CardService#MAX_PIN_FAILURES} wrong PINs lock it.
 */
public final class CreditService {
    /** Most overdue statements processed at once, if the server was off for a long time. */
    private static final int MAX_CATCH_UP = 64;
    private static final long TICKS_PER_DAY = 24_000L;

    private CreditService() {
    }

    /** The game time statements are scheduled in. It keeps counting through sleep and /time set. */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---- cards ----

    /** Issues the player a credit card with the chosen PIN. The first is free; a replacement's fee goes on the bill. */
    public static ShopActionResult issue(ServerPlayer player, String pin) {
        if (!EconomyConfig.creditEnabled()) {
            return ShopActionResult.fail("credit.reikoeconomy.disabled");
        }
        if (!CardService.isValidPin(pin)) {
            return ShopActionResult.fail("card.reikoeconomy.pin_format");
        }
        if (player.getInventory().getFreeSlot() < 0) {
            return ShopActionResult.fail("card.reikoeconomy.no_room");
        }
        MinecraftServer server = player.server;
        CreditLedger ledger = CreditLedger.get(server);
        CreditTerms terms = EconomyConfig.creditTerms();
        String name = player.getGameProfile().getName();
        CreditLedger.Account account = ledger.find(player.getUUID())
                .orElseGet(() -> ledger.create(player.getUUID(), name, terms.startingLimit(), now(server) + terms.statementTicks()));

        long fee = account.cardsIssued() > 0 ? EconomyConfig.cardReplacementFee() : 0;
        if (fee > 0) {
            ledger.charge(account, fee);
        }
        boolean replacing = account.cardId() != null;
        UUID cardId = UUID.randomUUID();
        ledger.issueCard(account, cardId, CardService.hash(cardId, pin), name);

        ItemStack card = new ItemStack(ModItems.CREDIT_CARD.get());
        BankCard data = new BankCard(player.getUUID(), cardId, name);
        card.set(ModDataComponents.CREDIT_CARD.get(), data);
        List<ItemStack> before = InventorySync.snapshot(player);
        player.getInventory().add(card);
        InventorySync.sendChanges(player, before);

        EconomyService eco = EconomyService.INSTANCE;
        Component message;
        if (fee > 0) {
            message = Component.translatable("credit.reikoeconomy.replaced_with_fee", data.lastFour(), eco.format(fee));
        } else if (replacing) {
            message = Component.translatable("credit.reikoeconomy.replaced", data.lastFour());
        } else {
            message = Component.translatable("credit.reikoeconomy.issued", data.lastFour(), eco.format(account.limit()),
                    terms.statementTicks() / TICKS_PER_DAY);
        }
        return ShopActionResult.ok(message);
    }

    /** Checks a credit card and PIN, counting wrong PINs toward the lock like a debit card. */
    public static CardService.Authorization authorize(MinecraftServer server, ItemStack cardStack, String pin) {
        BankCard card = CreditCardItem.cardOf(cardStack);
        if (card == null) {
            return new CardService.Authorization(null, Component.translatable("card.reikoeconomy.no_card"));
        }
        CreditLedger ledger = CreditLedger.get(server);
        Optional<CreditLedger.Account> found = ledger.find(card.account());
        if (found.isEmpty() || !card.cardId().equals(found.get().cardId())) {
            return new CardService.Authorization(null, Component.translatable("card.reikoeconomy.cancelled"));
        }
        CreditLedger.Account account = found.get();
        if (account.pinFailures() >= CardService.MAX_PIN_FAILURES) {
            return new CardService.Authorization(null, Component.translatable("card.reikoeconomy.locked"));
        }
        if (!CardService.isValidPin(pin) || !MessageDigest.isEqual(
                CardService.hash(card.cardId(), pin).getBytes(StandardCharsets.US_ASCII),
                account.pinHash().getBytes(StandardCharsets.US_ASCII))) {
            int failures = account.pinFailures() + 1;
            ledger.setPinFailures(account, failures);
            return failures >= CardService.MAX_PIN_FAILURES
                    ? new CardService.Authorization(null, Component.translatable("card.reikoeconomy.locked_now"))
                    : new CardService.Authorization(null, Component.translatable("card.reikoeconomy.wrong_pin",
                            CardService.MAX_PIN_FAILURES - failures));
        }
        if (account.pinFailures() > 0) {
            ledger.setPinFailures(account, 0);
        }
        return new CardService.Authorization(card.account(), Component.empty());
    }

    // ---- spending ----

    /**
     * Puts a purchase on a credit card. A player shop owner ({@code payee}) is paid now; with no payee (the
     * server shop, villagers) the money simply leaves the economy, and the cardholder owes it either way.
     */
    public static ShopActionResult charge(MinecraftServer server, ItemStack card, String pin, long total,
                                          @Nullable UUID payee, String reason) {
        if (!EconomyConfig.creditEnabled()) {
            return ShopActionResult.fail("credit.reikoeconomy.disabled");
        }
        CardService.Authorization auth = authorize(server, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }
        CreditLedger ledger = CreditLedger.get(server);
        CreditLedger.Account account = ledger.find(auth.account()).orElseThrow();
        ShopActionResult usable = checkUsable(account, total);
        if (!usable.success()) {
            return usable;
        }
        if (payee != null) {
            TransactionResult paid = EconomyService.INSTANCE.deposit(server, payee, total, reason + "_credit");
            if (!paid.isSuccess()) {
                return ShopActionResult.fail(paid.translationKey());
            }
        }
        ledger.charge(account, total);
        return ShopActionResult.ok(Component.empty());
    }

    private static ShopActionResult checkUsable(CreditLedger.Account account, long amount) {
        if (account.frozen()) {
            return ShopActionResult.fail("credit.reikoeconomy.frozen");
        }
        long available = account.state().available();
        if (amount > available) {
            return ShopActionResult.fail("credit.reikoeconomy.over_limit", EconomyService.INSTANCE.format(available));
        }
        return ShopActionResult.ok(Component.empty());
    }

    /** Takes cash out of an ATM on credit. The advance and its fee go on the bill. */
    public static ShopActionResult cashAdvance(ServerPlayer player, ItemStack card, String pin, String amountText) {
        OptionalLong parsed = Money.parsePositive(amountText);
        if (parsed.isEmpty()) {
            return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
        }
        long amount = parsed.getAsLong();
        EconomyService eco = EconomyService.INSTANCE;
        long atmLimit = EconomyConfig.atmMaxWithdrawal();
        if (amount > atmLimit) {
            return ShopActionResult.fail("atm.reikoeconomy.over_limit", eco.format(atmLimit));
        }
        List<ItemStack> cash = CashItem.makeChange(amount);
        if (!InventorySpace.addAll(InventorySpace.copyOf(player.getInventory().items), cash)) {
            return ShopActionResult.fail("atm.reikoeconomy.no_room");
        }
        if (!EconomyConfig.creditEnabled()) {
            return ShopActionResult.fail("credit.reikoeconomy.disabled");
        }
        MinecraftServer server = player.server;
        CardService.Authorization auth = authorize(server, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }
        CreditLedger ledger = CreditLedger.get(server);
        CreditLedger.Account account = ledger.find(auth.account()).orElseThrow();
        long fee = EconomyConfig.creditTerms().cashAdvanceFee(amount);
        if (account.frozen()) {
            return ShopActionResult.fail("credit.reikoeconomy.frozen");
        }
        if (amount + fee > account.state().available()) {
            return ShopActionResult.fail("credit.reikoeconomy.advance_over_limit",
                    eco.format(account.state().available()), eco.format(fee));
        }
        ledger.charge(account, amount + fee);
        List<ItemStack> before = InventorySync.snapshot(player);
        for (ItemStack stack : cash) {
            player.getInventory().add(stack); // fits: checked above
        }
        player.getInventory().setChanged();
        InventorySync.sendChanges(player, before);
        return ShopActionResult.ok(Component.translatable("credit.reikoeconomy.advanced", eco.format(amount), eco.format(fee),
                eco.format(account.state().available())));
    }

    // ---- paying the bill ----

    /** Pays the bill from the cardholder's bank account: the typed amount, or everything owed if left blank. */
    public static ShopActionResult payFromBank(ServerPlayer player, ItemStack card, String pin, String amountText) {
        OptionalLong typed = OptionalLong.empty();
        if (amountText != null && !amountText.isBlank()) {
            typed = Money.parsePositive(amountText);
            if (typed.isEmpty()) {
                return ShopActionResult.fail(TransactionResult.INVALID_AMOUNT.translationKey());
            }
        }
        MinecraftServer server = player.server;
        CardService.Authorization auth = authorize(server, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }
        CreditLedger ledger = CreditLedger.get(server);
        CreditLedger.Account account = ledger.find(auth.account()).orElseThrow();
        if (account.balance() <= 0) {
            return ShopActionResult.fail("credit.reikoeconomy.nothing_owed");
        }
        long amount = Math.min(typed.orElse(account.balance()), account.balance());
        EconomyService eco = EconomyService.INSTANCE;
        TransactionResult result = eco.withdraw(server, account.owner(), amount, "credit.payment");
        if (!result.isSuccess()) {
            return result == TransactionResult.INSUFFICIENT_FUNDS
                    ? ShopActionResult.fail("credit.reikoeconomy.bank_short", eco.format(eco.getBalance(server, account.owner())))
                    : ShopActionResult.fail(result.translationKey());
        }
        return ShopActionResult.ok(paid(account, amount, ledger.pay(account, amount), 0));
    }

    /** Pays the bill with the cash in the ATM's slots, taking only what's owed and giving change. */
    public static ShopActionResult payWithCash(ServerPlayer player, ItemStack card, String pin, Container slots) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < slots.getContainerSize(); i++) {
            stacks.add(slots.getItem(i));
        }
        ShopActionResult result = payCash(player, card, pin, stacks, false);
        slots.setChanged();
        return result;
    }

    /** Pays the bill with the cash in the player's inventory, taking only what's owed and giving change. */
    public static ShopActionResult payWithAllCash(ServerPlayer player, ItemStack card, String pin) {
        return payCash(player, card, pin, player.getInventory().items, true);
    }

    private static ShopActionResult payCash(ServerPlayer player, ItemStack card, String pin, List<ItemStack> source,
                                            boolean sourceIsInventory) {
        long offered = CashWallet.value(source);
        if (offered <= 0) {
            return ShopActionResult.fail("credit.reikoeconomy.no_cash");
        }
        MinecraftServer server = player.server;
        CardService.Authorization auth = authorize(server, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }
        CreditLedger ledger = CreditLedger.get(server);
        CreditLedger.Account account = ledger.find(auth.account()).orElseThrow();
        long owed = account.balance();
        if (owed <= 0) {
            return ShopActionResult.fail("credit.reikoeconomy.nothing_owed");
        }

        List<ItemStack> before = InventorySync.snapshot(player);
        long amount;
        long change = 0;
        if (offered <= owed) {
            amount = offered;
            for (ItemStack stack : source) {
                if (CashItem.isCash(stack)) {
                    stack.setCount(0);
                }
            }
        } else {
            // Only take what's owed; the rest comes back as change, like paying at a shop.
            amount = owed;
            CashPlan.Plan plan = CashPlan.plan(CashWallet.count(source), owed).orElseThrow();
            List<ItemStack> changeStacks = CashItem.makeChange(plan.change());
            List<ItemStack> inventoryAfter = InventorySpace.copyOf(player.getInventory().items);
            if (sourceIsInventory) {
                CashWallet.remove(inventoryAfter, plan.handOver());
            }
            if (!InventorySpace.addAll(inventoryAfter, changeStacks)) {
                return ShopActionResult.fail("payment.reikoeconomy.no_room_for_change");
            }
            CashWallet.remove(source, plan.handOver());
            for (ItemStack stack : changeStacks) {
                player.getInventory().add(stack);
            }
            change = plan.change();
        }
        player.getInventory().setChanged();
        InventorySync.sendChanges(player, before);
        return ShopActionResult.ok(paid(account, amount, ledger.pay(account, amount), change));
    }

    private static Component paid(CreditLedger.Account account, long amount, boolean unfroze, long change) {
        EconomyService eco = EconomyService.INSTANCE;
        MutableComponent message = Component.translatable("credit.reikoeconomy.paid", eco.format(amount),
                eco.format(Math.max(0, account.balance())));
        if (change > 0) {
            message.append(" ").append(Component.translatable("credit.reikoeconomy.change", eco.format(change)));
        }
        if (unfroze) {
            message.append(" ").append(Component.translatable("credit.reikoeconomy.notice.unfrozen"));
        }
        return message;
    }

    // ---- what the player sees ----

    /** A short summary for the ATM screen: what's owed, the limit, and what to pay by when. */
    public static ShopActionResult status(ServerPlayer player, ItemStack card, String pin) {
        MinecraftServer server = player.server;
        CardService.Authorization auth = authorize(server, card, pin);
        if (!auth.ok()) {
            return ShopActionResult.fail(auth.message());
        }
        CreditLedger.Account account = CreditLedger.get(server).find(auth.account()).orElseThrow();
        return ShopActionResult.ok(summary(server, account));
    }

    private static Component summary(MinecraftServer server, CreditLedger.Account account) {
        EconomyService eco = EconomyService.INSTANCE;
        CreditStatement.State state = account.state();
        MutableComponent text = Component.translatable("credit.reikoeconomy.status", eco.format(Math.max(0, state.balance())),
                eco.format(state.limit()), eco.format(state.available()));
        text.append(" ");
        if (state.frozen()) {
            text.append(Component.translatable("credit.reikoeconomy.status.frozen"));
        } else if (state.minimumLeft() > 0) {
            text.append(Component.translatable("credit.reikoeconomy.status.due", eco.format(state.minimumLeft()), dueIn(server, account)));
        } else {
            text.append(Component.translatable("credit.reikoeconomy.status.nothing_due"));
        }
        return text;
    }

    /** "within 3 days" or "within a day", counting to the next statement. */
    public static Component dueIn(MinecraftServer server, CreditLedger.Account account) {
        long days = Math.max(0, (account.nextStatement() - now(server) + TICKS_PER_DAY - 1) / TICKS_PER_DAY);
        return days <= 1 ? Component.translatable("credit.reikoeconomy.within_a_day")
                : Component.translatable("credit.reikoeconomy.within_days", days);
    }

    /** The full picture, for /credit. */
    public static List<Component> describe(MinecraftServer server, CreditLedger.Account account) {
        EconomyService eco = EconomyService.INSTANCE;
        CreditTerms terms = EconomyConfig.creditTerms();
        CreditStatement.State state = account.state();
        List<Component> lines = new ArrayList<>();
        lines.add(account.cardId() == null
                ? Component.translatable("credit.reikoeconomy.info.no_card", account.name()).withStyle(ChatFormatting.GOLD)
                : Component.translatable("credit.reikoeconomy.info.card", account.name(),
                        new BankCard(account.owner(), account.cardId(), account.name()).lastFour()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("credit.reikoeconomy.info.owed", eco.format(state.balance()), eco.format(state.limit()),
                eco.format(state.available())));
        lines.add(Component.translatable("credit.reikoeconomy.info.statement", eco.format(state.statementBalance()),
                eco.format(state.minimumDue()), eco.format(state.paidSinceStatement())));
        lines.add(Component.translatable("credit.reikoeconomy.info.next", dueIn(server, account)).withStyle(ChatFormatting.GRAY));
        if (state.minimumLeft() > 0) {
            lines.add(Component.translatable("credit.reikoeconomy.info.pay_minimum", eco.format(state.minimumLeft()),
                    eco.format(terms.lateFee())));
        }
        long statementLeft = Math.max(0, state.statementBalance() - state.paidSinceStatement());
        if (statementLeft > 0 && terms.interestBasisPoints() > 0) {
            lines.add(Component.translatable("credit.reikoeconomy.info.pay_full", eco.format(statementLeft),
                    Money.toPlainString(terms.interestBasisPoints()) + "%").withStyle(ChatFormatting.GRAY));
        }
        if (state.frozen()) {
            lines.add(Component.translatable("credit.reikoeconomy.status.frozen").withStyle(ChatFormatting.RED));
        } else if (state.missed() > 0) {
            lines.add(Component.translatable("credit.reikoeconomy.info.missed", state.missed(), terms.freezeAfter()).withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /** Reminds a player who just joined about a payment that's due or a frozen card. */
    public static void remind(ServerPlayer player) {
        CreditLedger.get(player.server).find(player.getUUID()).ifPresent(account -> {
            CreditStatement.State state = account.state();
            if (state.frozen()) {
                player.sendSystemMessage(Component.translatable("credit.reikoeconomy.status.frozen").withStyle(ChatFormatting.GOLD));
            } else if (state.minimumLeft() > 0) {
                player.sendSystemMessage(Component.translatable("credit.reikoeconomy.notice.reminder",
                        EconomyService.INSTANCE.format(state.minimumLeft()), dueIn(player.server, account)).withStyle(ChatFormatting.GOLD));
            }
        });
    }

    // ---- statements ----

    /** Makes every statement that has come due by {@code now}. Called every second; cheap when nothing is due. */
    public static void runStatements(MinecraftServer server, long now) {
        CreditLedger ledger = CreditLedger.get(server);
        CreditTerms terms = null;
        for (CreditLedger.Account account : ledger.accounts()) {
            if (account.nextStatement() > now) {
                continue;
            }
            if (terms == null) {
                terms = EconomyConfig.creditTerms();
            }
            int made = 0;
            while (account.nextStatement() <= now && made++ < MAX_CATCH_UP) {
                closeStatement(server, ledger, account, terms);
            }
            if (account.nextStatement() <= now) {
                ledger.apply(account, account.state(), now + terms.statementTicks());
            }
        }
    }

    private static void closeStatement(MinecraftServer server, CreditLedger ledger, CreditLedger.Account account, CreditTerms terms) {
        CreditStatement.State state = account.state();
        long autopaid = 0;
        long shortfall = state.minimumLeft();
        if (shortfall > 0 && EconomyService.INSTANCE.withdraw(server, account.owner(), shortfall, "credit.autopay").isSuccess()) {
            autopaid = shortfall;
        }
        CreditStatement.Result result = CreditStatement.close(state, terms, autopaid);
        ledger.apply(account, result.state(), account.nextStatement() + terms.statementTicks());
        ServerPlayer player = server.getPlayerList().getPlayer(account.owner());
        if (player != null) {
            for (Component line : notices(result, terms)) {
                player.sendSystemMessage(line);
            }
        }
    }

    private static List<Component> notices(CreditStatement.Result result, CreditTerms terms) {
        EconomyService eco = EconomyService.INSTANCE;
        List<Component> lines = new ArrayList<>();
        if (result.autopaid() > 0) {
            lines.add(Component.translatable("credit.reikoeconomy.notice.autopaid", eco.format(result.autopaid())));
        }
        if (result.lateFee() > 0) {
            lines.add(Component.translatable("credit.reikoeconomy.notice.late", eco.format(result.lateFee())));
        }
        if (result.interest() > 0) {
            lines.add(Component.translatable("credit.reikoeconomy.notice.interest", eco.format(result.interest())));
        }
        if (result.limitChange() > 0) {
            lines.add(Component.translatable("credit.reikoeconomy.notice.limit_up", eco.format(result.state().limit())));
        } else if (result.limitChange() < 0) {
            lines.add(Component.translatable("credit.reikoeconomy.notice.limit_down", eco.format(result.state().limit())));
        }
        if (result.frozeNow()) {
            lines.add(Component.translatable("credit.reikoeconomy.status.frozen"));
        }
        if (result.unfrozeNow()) {
            lines.add(Component.translatable("credit.reikoeconomy.notice.unfrozen"));
        }
        if (result.state().statementBalance() > 0) {
            lines.add(Component.translatable("credit.reikoeconomy.notice.statement", eco.format(result.state().statementBalance()),
                    eco.format(result.state().minimumDue()), terms.statementTicks() / TICKS_PER_DAY));
        }
        List<Component> styled = new ArrayList<>();
        for (Component line : lines) {
            styled.add(line.copy().withStyle(ChatFormatting.GOLD));
        }
        return styled;
    }

    // ---- admin ----

    /** Ends the player's statement period now, as if its due date had come, and restarts the clock. */
    public static boolean statementNow(MinecraftServer server, UUID owner) {
        CreditLedger ledger = CreditLedger.get(server);
        Optional<CreditLedger.Account> account = ledger.find(owner);
        if (account.isEmpty()) {
            return false;
        }
        CreditTerms terms = EconomyConfig.creditTerms();
        closeStatement(server, ledger, account.get(), terms);
        ledger.apply(account.get(), account.get().state(), now(server) + terms.statementTicks());
        return true;
    }

    public static boolean setLimit(MinecraftServer server, UUID owner, long limit) {
        CreditLedger ledger = CreditLedger.get(server);
        Optional<CreditLedger.Account> account = ledger.find(owner);
        account.ifPresent(found -> ledger.setLimit(found, limit));
        return account.isPresent();
    }

    public static boolean forgive(MinecraftServer server, UUID owner) {
        CreditLedger ledger = CreditLedger.get(server);
        Optional<CreditLedger.Account> account = ledger.find(owner);
        account.ifPresent(ledger::forgive);
        return account.isPresent();
    }
}

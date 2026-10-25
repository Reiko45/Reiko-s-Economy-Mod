package com.reiko.economy.core;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.item.BankCard;
import com.reiko.economy.item.DebitCardItem;
import com.reiko.economy.registry.ModDataComponents;
import com.reiko.economy.registry.ModItems;
import com.reiko.economy.shop.InventorySync;
import com.reiko.economy.shop.ShopActionResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Debit cards: issuing them at the ATM and checking them when someone pays or uses an ATM.
 *
 * <p>Each account has at most one working card. Getting a new one cancels the old one, which is how a lost or
 * stolen card is stopped. Anyone holding a card can use it with its PIN; after {@value #MAX_PIN_FAILURES} wrong
 * PINs in a row the card locks, and its owner gets a new one at an ATM.
 */
public final class CardService {
    public static final int MAX_PIN_FAILURES = 3;
    private static final Pattern PIN_FORMAT = Pattern.compile("\\d{4}");

    private CardService() {
    }

    /** The result of checking a card: the account to charge, or why it can't be used. */
    public record Authorization(@Nullable UUID account, Component message) {
        public boolean ok() {
            return account != null;
        }

        static Authorization denied(String key, Object... args) {
            return new Authorization(null, Component.translatable(key, args));
        }
    }

    public static boolean isValidPin(String pin) {
        return pin != null && PIN_FORMAT.matcher(pin).matches();
    }

    /**
     * Issues the player a new card for their own account with the chosen PIN, and cancels their previous card.
     * The first card is free; later ones cost the configured fee, taken from the balance.
     */
    public static ShopActionResult issue(ServerPlayer player, String pin) {
        if (!isValidPin(pin)) {
            return ShopActionResult.fail("card.reikoeconomy.pin_format");
        }
        if (player.getInventory().getFreeSlot() < 0) {
            return ShopActionResult.fail("card.reikoeconomy.no_room");
        }
        MinecraftServer server = player.server;
        EconomyService eco = EconomyService.INSTANCE;
        Ledger ledger = Ledger.get(server);
        Ledger.Account account = eco.ensureAccount(server, player.getUUID(), player.getGameProfile().getName());

        long fee = account.cardsIssued() > 0 ? EconomyConfig.cardReplacementFee() : 0;
        if (fee > 0) {
            TransactionResult paid = eco.withdraw(server, player.getUUID(), fee, "card.fee");
            if (!paid.isSuccess()) {
                return ShopActionResult.fail("card.reikoeconomy.cant_afford_fee", eco.format(fee));
            }
        }
        boolean replacing = account.cardId() != null;
        UUID cardId = UUID.randomUUID();
        ledger.issueCard(account, cardId, hash(cardId, pin));

        ItemStack card = new ItemStack(ModItems.DEBIT_CARD.get());
        BankCard data = new BankCard(player.getUUID(), cardId, player.getGameProfile().getName());
        card.set(ModDataComponents.DEBIT_CARD.get(), data);
        List<ItemStack> before = InventorySync.snapshot(player);
        player.getInventory().add(card);
        InventorySync.sendChanges(player, before);

        Component message = fee > 0
                ? Component.translatable("card.reikoeconomy.issued_with_fee", data.lastFour(), eco.format(fee))
                : Component.translatable("card.reikoeconomy.issued", data.lastFour());
        if (replacing) {
            message = message.copy().append(" ").append(Component.translatable("card.reikoeconomy.old_cancelled"));
        }
        return ShopActionResult.ok(message);
    }

    /**
     * Checks a card and PIN. Every wrong PIN counts towards the lock, so guessing is limited to
     * {@value #MAX_PIN_FAILURES} tries; a right PIN resets the count.
     */
    public static Authorization authorize(MinecraftServer server, ItemStack cardStack, String pin) {
        BankCard card = DebitCardItem.cardOf(cardStack);
        if (card == null) {
            return Authorization.denied("card.reikoeconomy.no_card");
        }
        Ledger ledger = Ledger.get(server);
        Optional<Ledger.Account> found = ledger.find(card.account());
        if (found.isEmpty() || !card.cardId().equals(found.get().cardId())) {
            return Authorization.denied("card.reikoeconomy.cancelled");
        }
        Ledger.Account account = found.get();
        if (account.pinFailures() >= MAX_PIN_FAILURES) {
            return Authorization.denied("card.reikoeconomy.locked");
        }
        if (!isValidPin(pin) || !MessageDigest.isEqual(
                hash(card.cardId(), pin).getBytes(StandardCharsets.US_ASCII),
                account.pinHash().getBytes(StandardCharsets.US_ASCII))) {
            int failures = account.pinFailures() + 1;
            ledger.setPinFailures(account, failures);
            return failures >= MAX_PIN_FAILURES
                    ? Authorization.denied("card.reikoeconomy.locked_now")
                    : Authorization.denied("card.reikoeconomy.wrong_pin", MAX_PIN_FAILURES - failures);
        }
        if (account.pinFailures() > 0) {
            ledger.setPinFailures(account, 0);
        }
        return new Authorization(card.account(), Component.empty());
    }

    /** SHA-256 of the PIN salted with the card id, so the same PIN gives a different hash on every card. */
    static String hash(UUID cardId, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((cardId + ":" + pin).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}

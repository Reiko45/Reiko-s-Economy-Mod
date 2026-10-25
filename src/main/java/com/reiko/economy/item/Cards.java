package com.reiko.economy.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Debit and credit cards together: which cards a player carries and which one pays. */
public final class Cards {
    private Cards() {
    }

    /** The card details on a debit or credit card, or null for anything else. */
    @Nullable
    public static BankCard data(ItemStack stack) {
        BankCard debit = DebitCardItem.cardOf(stack);
        return debit != null ? debit : CreditCardItem.cardOf(stack);
    }

    public static boolean isCard(ItemStack stack) {
        return data(stack) != null;
    }

    public static boolean isCredit(ItemStack stack) {
        return CreditCardItem.cardOf(stack) != null;
    }

    /** Every card the player carries: main hand, off hand, then the inventory in slot order. */
    public static List<ItemStack> carried(Player player) {
        List<ItemStack> cards = new ArrayList<>();
        addIfCard(cards, player.getMainHandItem());
        addIfCard(cards, player.getOffhandItem());
        for (ItemStack stack : player.getInventory().items) {
            addIfCard(cards, stack);
        }
        return cards;
    }

    private static void addIfCard(List<ItemStack> cards, ItemStack stack) {
        // The main hand item is also in the inventory list, so skip the same stack twice.
        if (isCard(stack) && cards.stream().noneMatch(card -> card == stack)) {
            cards.add(stack);
        }
    }

    /**
     * The card that pays: the carried card with {@code cardId}, or the first carried card when no id is given.
     * Returns {@link ItemStack#EMPTY} when that card isn't with the player.
     */
    public static ItemStack find(Player player, @Nullable UUID cardId) {
        for (ItemStack card : carried(player)) {
            if (cardId == null || cardId.equals(data(card).cardId())) {
                return card;
            }
        }
        return ItemStack.EMPTY;
    }
}

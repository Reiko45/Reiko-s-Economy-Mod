package com.reiko.economy.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Turns a vanilla villager offer into money terms. Emeralds the villager asks for become a price, emeralds it
 * hands out become a cash payout, and every other item is still traded as an item.
 *
 * <p>Prices follow the offer's current emerald count, so vanilla's discounts (curing a zombie villager, Hero
 * of the Village, good reputation) and price rises from demand still apply. The client and the server both
 * use this, so the price on the button is the price charged.
 */
public final class VillagerPricing {
    private VillagerPricing() {
    }

    /** An item the villager wants besides money, and how many of it per trade. */
    public record Need(ItemCost cost, int count) {
        /** The item to show for this need, with the count set. */
        public ItemStack display() {
            return cost.itemStack().copyWithCount(count);
        }
    }

    /**
     * One trade in money terms. At most one of {@code price} and {@code payout} is above zero.
     *
     * @param price  what the player pays, in stored units
     * @param payout what the villager pays the player in cash, in stored units
     * @param needs  items the player hands over
     * @param goods  the item the player gets, or empty when the villager pays money instead
     */
    public record Terms(long price, long payout, List<Need> needs, ItemStack goods) {
        /** The player pays for this trade. */
        public boolean isPurchase() {
            return price > 0;
        }

        /** The villager pays the player for this trade. */
        public boolean isSale() {
            return payout > 0;
        }

        /** Price minus payout: above zero the player pays, below zero they are paid. */
        public long net() {
            return price - payout;
        }
    }

    public static Terms terms(MerchantOffer offer, long emeraldValue) {
        long emeraldsIn = 0;
        List<Need> needs = new ArrayList<>(2);

        ItemCost costA = offer.getItemCostA();
        int countA = offer.getCostA().getCount(); // after discounts and demand, like vanilla charges
        if (isEmerald(costA)) {
            emeraldsIn += countA;
        } else {
            needs.add(new Need(costA, countA));
        }
        Optional<ItemCost> costB = offer.getItemCostB();
        if (costB.isPresent()) {
            if (isEmerald(costB.get())) {
                emeraldsIn += costB.get().count();
            } else {
                needs.add(new Need(costB.get(), costB.get().count()));
            }
        }

        ItemStack result = offer.getResult();
        long emeraldsOut = 0;
        ItemStack goods = ItemStack.EMPTY;
        if (result.is(Items.EMERALD)) {
            emeraldsOut = result.getCount();
        } else {
            goods = result;
        }

        // A trade with emeralds on both sides (none in vanilla) only moves the difference.
        long both = Math.min(emeraldsIn, emeraldsOut);
        return new Terms((emeraldsIn - both) * emeraldValue, (emeraldsOut - both) * emeraldValue, List.copyOf(needs), goods);
    }

    /** The price before vanilla's discounts and demand, for showing what a discount saves. */
    public static long basePrice(MerchantOffer offer, long emeraldValue) {
        long emeralds = 0;
        if (isEmerald(offer.getItemCostA())) {
            emeralds += offer.getItemCostA().count();
        }
        if (offer.getItemCostB().isPresent() && isEmerald(offer.getItemCostB().get())) {
            emeralds += offer.getItemCostB().get().count();
        }
        return emeralds * emeraldValue;
    }

    private static boolean isEmerald(ItemCost cost) {
        return cost.item().value() == Items.EMERALD;
    }
}

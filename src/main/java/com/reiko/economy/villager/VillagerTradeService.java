package com.reiko.economy.villager;

import com.reiko.economy.api.TransactionResult;
import com.reiko.economy.bank.InventorySpace;
import com.reiko.economy.bank.Purse;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.CashPlan;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.shop.Payment;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.shop.ShopPayments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Carries out a villager trade paid in money, on the server.
 *
 * <p>The trade goes through vanilla's own trading screen on the server, so the villager behaves as usual: it
 * counts the trade toward restocking, gains experience and levels up, drops experience orbs, remembers the
 * player for reputation and triggers advancements. Only the payment changes: emeralds the villager asked for
 * are paid by card or cash, and emeralds it gave are paid out in cash.
 *
 * <p>Everything is checked before anything moves: stock, the items the villager wants, room for what the
 * player gets back, then the card and PIN.
 */
public final class VillagerTradeService {
    /** Most trades of one offer at once. Vanilla offers allow at most 16 before restocking. */
    public static final int MAX_TRADES_AT_ONCE = 64;
    private static final String REASON = "villager.buy";
    /** Cash that could never fit in an inventory, even as $100 bills: 36 slots of 64. */
    private static final long MAX_PAYOUT = 36L * 64 * 10_000;

    private VillagerTradeService() {
    }

    /**
     * Trades {@code times} times with offer {@code offerIndex} of the trader behind {@code menu}.
     *
     * @param expectedNet the price the player saw per trade, as {@link VillagerPricing.Terms#net()}; the trade
     *                    is refused if it has changed
     */
    public static ShopActionResult trade(ServerPlayer player, MerchantMenu menu, int offerIndex, int times,
                                         long expectedNet, Payment payment) {
        if (!EconomyConfig.villagersUseMoney()) {
            return ShopActionResult.fail("villager.reikoeconomy.disabled");
        }
        Merchant trader = menu.trader;
        if (trader.getTradingPlayer() != player) {
            return ShopActionResult.fail("villager.reikoeconomy.no_trade");
        }
        MerchantOffers offers = trader.getOffers();
        if (offerIndex < 0 || offerIndex >= offers.size()) {
            return ShopActionResult.fail("villager.reikoeconomy.no_trade");
        }
        MerchantOffer offer = offers.get(offerIndex);
        VillagerPricing.Terms terms = VillagerPricing.terms(offer, EconomyConfig.emeraldValue());
        if (terms.net() != expectedNet) {
            return ShopActionResult.fail("shop.reikoeconomy.price_changed");
        }
        int left = offer.getMaxUses() - offer.getUses();
        if (left <= 0) {
            return ShopActionResult.fail("villager.reikoeconomy.out_of_stock");
        }
        if (times < 1 || times > Math.min(left, MAX_TRADES_AT_ONCE)) {
            return ShopActionResult.fail("villager.reikoeconomy.only_left", Math.min(left, MAX_TRADES_AT_ONCE));
        }
        long total;
        long payout;
        try {
            total = Math.multiplyExact(terms.price(), (long) times);
            payout = Math.multiplyExact(terms.payout(), (long) times);
        } catch (ArithmeticException e) {
            return ShopActionResult.fail(TransactionResult.BALANCE_LIMIT.translationKey());
        }
        if (total > Money.MAX_BALANCE) {
            return ShopActionResult.fail(TransactionResult.BALANCE_LIMIT.translationKey());
        }
        if (payout > MAX_PAYOUT) {
            return ShopActionResult.fail("villager.reikoeconomy.no_room");
        }

        // Work out the whole trade on a copy of the inventory first.
        List<ItemStack> slots = player.getInventory().items;
        List<ItemStack> after = InventorySpace.copyOf(slots);
        for (VillagerPricing.Need need : terms.needs()) {
            int count = need.count() * times;
            if (!removeMatching(after, need.cost()::test, count)) {
                return ShopActionResult.fail("villager.reikoeconomy.missing_items", count, need.cost().itemStack().getHoverName());
            }
        }
        List<ItemStack> goods = stacksOf(terms.goods(), times);

        CashPlan.Plan cash = null;
        if (total > 0) {
            if (payment.method() == Payment.CASH) {
                // Loose cash and wallets both count; change goes back into a wallet if there is one.
                Optional<CashPlan.Plan> plan = CashPlan.plan(Purse.count(after, player), total);
                if (plan.isEmpty()) {
                    return ShopActionResult.fail("payment.reikoeconomy.not_enough_cash",
                            EconomyService.INSTANCE.format(Purse.value(player)));
                }
                cash = plan.get();
                Purse.removeLoose(after, cash.handOver());
            } else if (payment.method() != Payment.CARD) {
                return ShopActionResult.fail("payment.reikoeconomy.unknown_method");
            }
        }
        long change = cash == null ? 0 : cash.change();
        if (!InventorySpace.addAll(after, goods) || !Purse.fits(player, after, payout, change)) {
            return ShopActionResult.fail("villager.reikoeconomy.no_room");
        }

        // The card goes last: a wrong PIN is only counted when the trade would otherwise go through.
        boolean onCredit = false;
        if (total > 0 && payment.method() == Payment.CARD) {
            ShopPayments.Charge charge = ShopPayments.chargeCard(player, payment, total, null, REASON);
            if (!charge.success()) {
                return ShopActionResult.fail(charge.message());
            }
            onCredit = charge.onCredit();
        }

        // Nothing can fail from here on.
        if (cash != null) {
            Purse.remove(player, cash.handOver());
        }
        for (VillagerPricing.Need need : terms.needs()) {
            removeMatching(slots, need.cost()::test, need.count() * times);
        }
        for (ItemStack stack : goods) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        Purse.give(player, payout);
        Purse.give(player, change);
        player.getInventory().setChanged();

        for (int i = 0; i < times; i++) {
            trader.notifyTrade(offer); // uses, villager experience, reputation, advancements, NeoForge's trade event
            player.awardStat(Stats.TRADED_WITH_VILLAGER);
        }
        if (trader instanceof Entity entity) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), trader.getNotifyTradeSound(),
                    SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        // The client only learns about used-up offers and new experience from the server.
        player.sendMerchantOffers(menu.containerId, offers, traderLevel(trader), trader.getVillagerXp(),
                trader.showProgressBar(), trader.canRestock());

        return ShopActionResult.ok(receipt(offer, terms, times, total, payout, payment, cash, onCredit));
    }

    private static Component receipt(MerchantOffer offer, VillagerPricing.Terms terms, int times, long total, long payout,
                                     Payment payment, CashPlan.Plan cash, boolean onCredit) {
        EconomyService eco = EconomyService.INSTANCE;
        if (terms.isPurchase()) {
            ItemStack bought = offer.getResult();
            Component line = Component.translatable("villager.reikoeconomy.bought",
                    bought.getCount() * times, bought.getHoverName(), eco.format(total));
            return ShopPayments.receipt(line, payment, new ShopPayments.Charge(true, Component.empty(),
                    cash == null ? 0 : cash.change(), onCredit));
        }
        if (terms.isSale() && !terms.needs().isEmpty()) {
            VillagerPricing.Need sold = terms.needs().get(0);
            return Component.translatable("villager.reikoeconomy.sold",
                    sold.count() * times, sold.cost().itemStack().getHoverName(), eco.format(payout));
        }
        ItemStack got = offer.getResult();
        return Component.translatable("villager.reikoeconomy.traded", got.getCount() * times, got.getHoverName());
    }

    /** The level vanilla shows in the screen title: the villager's level, or 1 for a wandering trader. */
    public static int traderLevel(Merchant trader) {
        return trader instanceof VillagerDataHolder holder ? holder.getVillagerData().getLevel() : 1;
    }

    /** {@code times} copies of {@code goods}, split into full stacks. */
    private static List<ItemStack> stacksOf(ItemStack goods, int times) {
        List<ItemStack> stacks = new ArrayList<>();
        if (goods.isEmpty()) {
            return stacks;
        }
        long left = (long) goods.getCount() * times;
        int max = goods.getMaxStackSize();
        while (left > 0) {
            int count = (int) Math.min(max, left);
            stacks.add(goods.copyWithCount(count));
            left -= count;
        }
        return stacks;
    }

    /** How many items in {@code slots} match. */
    public static int countMatching(List<ItemStack> slots, Predicate<ItemStack> matches) {
        int count = 0;
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && matches.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes {@code count} matching items out of {@code slots}, or nothing if there aren't enough. */
    private static boolean removeMatching(List<ItemStack> slots, Predicate<ItemStack> matches, int count) {
        if (countMatching(slots, matches) < count) {
            return false;
        }
        int left = count;
        for (ItemStack stack : slots) {
            if (left <= 0) {
                break;
            }
            if (!stack.isEmpty() && matches.test(stack)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        return true;
    }
}

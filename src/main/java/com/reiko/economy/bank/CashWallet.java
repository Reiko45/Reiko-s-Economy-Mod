package com.reiko.economy.bank;

import com.reiko.economy.core.CashPlan;
import com.reiko.economy.core.Denomination;
import com.reiko.economy.item.CashItem;
import com.reiko.economy.shop.InventorySync;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The coins and bills in a player's main inventory and hotbar, and paying with them. */
public final class CashWallet {
    private CashWallet() {
    }

    public static Map<Denomination, Integer> count(List<ItemStack> slots) {
        Map<Denomination, Integer> wallet = new EnumMap<>(Denomination.class);
        for (ItemStack stack : slots) {
            if (stack.getItem() instanceof CashItem cash) {
                wallet.merge(cash.denomination(), stack.getCount(), Integer::sum);
            }
        }
        return wallet;
    }

    public static long value(List<ItemStack> slots) {
        long total = 0;
        for (ItemStack stack : slots) {
            total += CashItem.valueOf(stack);
        }
        return total;
    }

    /** Removes the given pieces from {@code slots}. Mutates the stacks. */
    public static void remove(List<ItemStack> slots, Map<Denomination, Integer> pieces) {
        for (Map.Entry<Denomination, Integer> piece : pieces.entrySet()) {
            int left = piece.getValue();
            for (ItemStack stack : slots) {
                if (left <= 0) {
                    break;
                }
                if (stack.getItem() instanceof CashItem cash && cash.denomination() == piece.getKey()) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
    }

    /**
     * Works out how the player would pay {@code amount} in cash, and checks their change would fit once the
     * pieces they hand over are gone. Changes nothing.
     */
    public static Optional<CashPlan.Plan> planPayment(ServerPlayer player, long amount) {
        List<ItemStack> slots = player.getInventory().items;
        Optional<CashPlan.Plan> plan = CashPlan.plan(count(slots), amount);
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> after = InventorySpace.copyOf(slots);
        remove(after, plan.get().handOver());
        return InventorySpace.addAll(after, CashItem.makeChange(plan.get().change())) ? plan : Optional.empty();
    }

    /** Carries out a plan from {@link #planPayment}: takes the pieces and hands back the change. */
    public static void pay(ServerPlayer player, CashPlan.Plan plan) {
        List<ItemStack> before = InventorySync.snapshot(player);
        remove(player.getInventory().items, plan.handOver());
        for (ItemStack change : CashItem.makeChange(plan.change())) {
            player.getInventory().add(change);
        }
        player.getInventory().setChanged();
        InventorySync.sendChanges(player, before);
    }
}

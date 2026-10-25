package com.reiko.economy.bank;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Simulates adding items to an inventory, so a trade can be refused before anything moves. */
public final class InventorySpace {
    private InventorySpace() {
    }

    public static List<ItemStack> copyOf(List<ItemStack> slots) {
        List<ItemStack> copy = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) {
            copy.add(stack.copy());
        }
        return copy;
    }

    /**
     * Adds {@code incoming} to the simulated {@code slots} the way items are normally added: topping up matching
     * stacks first, then filling empty slots. Mutates {@code slots}.
     *
     * @return true if everything fit
     */
    public static boolean addAll(List<ItemStack> slots, List<ItemStack> incoming) {
        for (ItemStack stack : incoming) {
            int left = stack.getCount();
            int max = stack.getMaxStackSize();
            for (ItemStack slot : slots) {
                if (left > 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < max) {
                    int added = Math.min(max - slot.getCount(), left);
                    slot.grow(added);
                    left -= added;
                }
            }
            for (int i = 0; i < slots.size() && left > 0; i++) {
                if (slots.get(i).isEmpty()) {
                    int added = Math.min(max, left);
                    slots.set(i, stack.copyWithCount(added));
                    left -= added;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }
}

package com.reiko.economy.shop;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A shop's stock as hoppers and pipes see it: they can put the shop's item in, but never take anything out,
 * so nobody can drain someone else's shop with a hopper underneath it.
 */
public record InsertOnlyStock(IItemHandler stock) implements IItemHandler {
    @Override
    public int getSlots() {
        return stock.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return stock.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stock.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return stock.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return stock.isItemValid(slot, stack);
    }
}

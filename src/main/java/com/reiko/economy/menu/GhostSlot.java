package com.reiko.economy.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

/**
 * A display-only slot that shows which item a shop sells. Clicking it with an item copies that item into the
 * shop's settings without taking it from the player; the menu's {@code clicked} handles that.
 */
public final class GhostSlot extends Slot {
    public GhostSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}

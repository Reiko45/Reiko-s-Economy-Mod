package com.reiko.economy.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/** Shared helpers for the shop menus. */
public abstract class ShopMenuBase extends AbstractContainerMenu {
    protected ShopMenuBase(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /** Adds the 27 inventory slots and 9 hotbar slots, with the top-left item at (x, y). */
    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, x + col * 18, y + 58));
        }
    }

    /** Never gather items out of a display-only slot with a double-click. */
    @Override
    public boolean canTakeItemForPickAll(net.minecraft.world.item.ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && super.canTakeItemForPickAll(stack, slot);
    }
}

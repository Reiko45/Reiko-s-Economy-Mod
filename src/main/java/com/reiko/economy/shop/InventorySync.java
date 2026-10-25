package com.reiko.economy.shop;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the client's view of the player's inventory up to date while a shop screen is open.
 *
 * <p>Vanilla only syncs the inventory while no other screen is open, so after a trade the client would keep
 * showing the old items until the shop closed. Container id -2 is vanilla's "set this inventory slot
 * directly" message, which the client applies no matter which screen is open.
 */
public final class InventorySync {
    private static final int SET_INVENTORY_SLOT = -2;

    private InventorySync() {
    }

    /** Copies the main inventory and hotbar, to compare against after a change. */
    public static List<ItemStack> snapshot(ServerPlayer player) {
        List<ItemStack> copy = new ArrayList<>(player.getInventory().items.size());
        for (ItemStack stack : player.getInventory().items) {
            copy.add(stack.copy());
        }
        return copy;
    }

    /** Sends the client every main-inventory slot that differs from {@code before}. */
    public static void sendChanges(ServerPlayer player, List<ItemStack> before) {
        List<ItemStack> now = player.getInventory().items;
        for (int slot = 0; slot < now.size() && slot < before.size(); slot++) {
            if (!ItemStack.matches(before.get(slot), now.get(slot))) {
                player.connection.send(new ClientboundContainerSetSlotPacket(SET_INVENTORY_SLOT, 0, slot, now.get(slot).copy()));
            }
        }
    }
}

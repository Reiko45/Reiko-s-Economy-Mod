package com.reiko.economy.menu;

import com.reiko.economy.item.CashItem;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The ATM screen's menu: a row of deposit slots that only take cash, plus the player's inventory.
 * Withdrawals and deposits go through {@code AtmActionPayload}.
 */
public final class AtmMenu extends ShopMenuBase {
    public static final int DEPOSIT_SLOTS = 9;
    public static final int DEPOSIT_Y = 110;
    public static final int INVENTORY_Y = 166;
    public static final int WIDTH = 176;
    public static final int HEIGHT = 248;

    private static final int INVENTORY_START = DEPOSIT_SLOTS;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final ContainerLevelAccess access;
    private final SimpleContainer depositSlots = new SimpleContainer(DEPOSIT_SLOTS);

    /** Client constructor. */
    public AtmMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public AtmMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.ATM.get(), containerId);
        this.access = access;
        for (int i = 0; i < DEPOSIT_SLOTS; i++) {
            addSlot(new Slot(depositSlots, i, 8 + i * 18, DEPOSIT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return CashItem.isCash(stack);
                }
            });
        }
        addPlayerInventory(inventory, 8, INVENTORY_Y);
    }

    public Container depositSlots() {
        return depositSlots;
    }

    /** Value of the cash sitting in the deposit slots. Works on both sides. */
    public long depositValue() {
        long total = 0;
        for (int i = 0; i < depositSlots.getContainerSize(); i++) {
            total += CashItem.valueOf(depositSlots.getItem(i));
        }
        return total;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < DEPOSIT_SLOTS) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!CashItem.isCash(stack) || !moveItemStackTo(stack, 0, DEPOSIT_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** Cash left in the deposit slots goes back to the player, like a crafting table's grid. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, depositSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ATM.get());
    }
}

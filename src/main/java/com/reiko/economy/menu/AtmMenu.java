package com.reiko.economy.menu;

import com.reiko.economy.item.CashItem;
import com.reiko.economy.item.Cards;
import com.reiko.economy.registry.ModBlocks;
import com.reiko.economy.registry.ModMenus;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The ATM screen's menu: a card slot (debit or credit), a row of deposit slots that only take cash, and the player's inventory.
 * The card and any cash left in the slots go back to the player when the screen closes. Actions go through
 * {@code AtmActionPayload}.
 */
public final class AtmMenu extends ShopMenuBase {
    public static final int CARD_SLOT = 0;
    public static final int CARD_X = 8;
    public static final int CARD_Y = 18;
    public static final int DEPOSIT_SLOTS = 9;
    /** The right-hand column: deposit slots at the top, the player's inventory below, like vanilla's layout. */
    public static final int RIGHT_X = 180;
    public static final int DEPOSIT_Y = 17;
    public static final int INVENTORY_Y = 84;
    /** Two columns keep the ATM as short as vanilla's villager screen, so it fits at any GUI scale. */
    public static final int WIDTH = 350;
    public static final int HEIGHT = 166;

    private static final int DEPOSIT_START = 1;
    private static final int DEPOSIT_END = DEPOSIT_START + DEPOSIT_SLOTS;
    private static final int INVENTORY_START = DEPOSIT_END;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final ContainerLevelAccess access;
    private final SimpleContainer cardSlot = new SimpleContainer(1);
    private final SimpleContainer depositSlots = new SimpleContainer(DEPOSIT_SLOTS);

    /** Client: the shared accounts the player is in, for picking whose a new card is. */
    private List<String> sharedAccounts = List.of();

    /** Client constructor. */
    public AtmMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
        this.sharedAccounts = buf.readList(FriendlyByteBuf::readUtf);
    }

    /** What the client needs when the ATM opens: the names of the shared accounts the player is in. */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, List<String> sharedAccounts) {
        buf.writeCollection(sharedAccounts, FriendlyByteBuf::writeUtf);
    }

    /** Client: names of the shared accounts the player can get a card for. */
    public List<String> sharedAccounts() {
        return sharedAccounts;
    }

    public AtmMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.ATM.get(), containerId);
        this.access = access;
        addSlot(new Slot(cardSlot, 0, CARD_X, CARD_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Cards.isCard(stack);
            }
        });
        for (int i = 0; i < DEPOSIT_SLOTS; i++) {
            addSlot(new Slot(depositSlots, i, RIGHT_X + i * 18, DEPOSIT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return CashItem.isCash(stack);
                }
            });
        }
        addPlayerInventory(inventory, RIGHT_X, INVENTORY_Y);
    }

    /** The inserted card, or empty. */
    public ItemStack card() {
        return cardSlot.getItem(0);
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
        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (Cards.isCard(stack)) {
            if (!moveItemStackTo(stack, CARD_SLOT, CARD_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!CashItem.isCash(stack) || !moveItemStackTo(stack, DEPOSIT_START, DEPOSIT_END, false)) {
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

    /** The card and any leftover cash go back to the player, like a crafting table's grid. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> {
            clearContainer(player, cardSlot);
            clearContainer(player, depositSlots);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ATM.get());
    }
}

package com.reiko.economy.menu;

import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.network.ShopFeedback;
import com.reiko.economy.registry.ModMenus;
import com.reiko.economy.shop.ShopAccess;
import com.reiko.economy.shop.ShopActionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The owner's view of a player shop: pick the item, set what players pay and what the shop pays, and fill
 * the 27 stock slots.
 *
 * <p>Two columns: the item and prices on the left, the stock and the player's inventory on the right. Slot 0
 * is the item-for-sale display, slots 1-27 are stock, then the player's inventory.
 */
public final class ShopOwnerMenu extends ShopMenuBase {
    public static final int TEMPLATE_SLOT = 0;
    public static final int TEMPLATE_X = 8;
    public static final int TEMPLATE_Y = 18;
    public static final int RIGHT_X = 180;
    public static final int STOCK_X = RIGHT_X;
    public static final int STOCK_Y = 17;
    public static final int INVENTORY_X = RIGHT_X;
    public static final int INVENTORY_Y = 88;
    public static final int WIDTH = 350;
    public static final int HEIGHT = 170;

    private static final int STOCK_START = 1;
    private static final int STOCK_END = STOCK_START + ShopBlockEntity.STOCK_SLOTS;
    private static final int INVENTORY_START = STOCK_END;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    @Nullable
    private final ShopBlockEntity shop; // null on the client
    private final BlockPos pos;
    private final long initialPrice;
    private final long initialSellPrice;
    private final Container templateView;

    /** Client constructor, from the data the server sent when opening. */
    public ShopOwnerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, null, buf.readBlockPos(), buf.readVarLong(), buf.readVarLong(), new SimpleContainer(1),
                new ItemStackHandler(ShopBlockEntity.STOCK_SLOTS));
    }

    private ShopOwnerMenu(int containerId, Inventory inventory, @Nullable ShopBlockEntity shop, BlockPos pos, long price,
            long sellPrice, Container templateView, IItemHandler stock) {
        super(ModMenus.SHOP_OWNER.get(), containerId);
        this.shop = shop;
        this.pos = pos;
        this.initialPrice = price;
        this.initialSellPrice = sellPrice;
        this.templateView = templateView;

        addSlot(new GhostSlot(templateView, 0, TEMPLATE_X, TEMPLATE_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(stock, col + row * 9, STOCK_X + col * 18, STOCK_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return matchesTemplate(stack);
                    }
                });
            }
        }
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y);
    }

    public static void open(ServerPlayer player, ShopBlockEntity shop) {
        Component title = Component.translatable("container.reikoeconomy.shop_owner");
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShopOwnerMenu(id, inventory, shop, shop.getBlockPos(),
                shop.price(), shop.sellPrice(), new SimpleContainer(shop.template().copy()), shop.stock()), title), buf -> {
            buf.writeBlockPos(shop.getBlockPos());
            buf.writeVarLong(shop.price());
            buf.writeVarLong(shop.sellPrice());
        });
    }

    @Nullable
    public ShopBlockEntity shop() {
        return shop;
    }

    public BlockPos pos() {
        return pos;
    }

    public long initialPrice() {
        return initialPrice;
    }

    public long initialSellPrice() {
        return initialSellPrice;
    }

    public ItemStack template() {
        return templateView.getItem(0);
    }

    private boolean matchesTemplate(ItemStack stack) {
        ItemStack template = template();
        return !template.isEmpty() && ItemStack.isSameItemSameComponents(stack, template);
    }

    /** Clicking the display slot sets the item for sale: left-click = the whole held stack, right-click = 1. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == TEMPLATE_SLOT) {
            if (clickType == ClickType.PICKUP && shop != null && player instanceof ServerPlayer serverPlayer) {
                ShopActionResult result;
                if (!ShopAccess.canManage(serverPlayer, shop)) {
                    result = ShopActionResult.fail("shop.reikoeconomy.not_yours");
                } else {
                    ItemStack carried = getCarried();
                    result = shop.trySetTemplate(carried.isEmpty() ? ItemStack.EMPTY
                            : button == 1 ? carried.copyWithCount(1) : carried.copy());
                }
                templateView.setItem(0, shop.template().copy());
                ShopFeedback.send(serverPlayer, result);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == TEMPLATE_SLOT) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < STOCK_END) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, STOCK_START, STOCK_END, false)) {
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

    @Override
    public boolean stillValid(Player player) {
        return shop == null || (Container.stillValidBlockEntity(shop, player)
                && player instanceof ServerPlayer serverPlayer && ShopAccess.canManage(serverPlayer, shop));
    }
}

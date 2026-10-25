package com.reiko.economy.menu;

import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.network.ShopFeedback;
import com.reiko.economy.registry.ModMenus;
import com.reiko.economy.shop.ShopAccess;
import com.reiko.economy.shop.ShopActionResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The admin's setup screen for an admin shop: item, buy price and sell price. */
public final class AdminShopSetupMenu extends ShopMenuBase {
    public static final int TEMPLATE_SLOT = 0;
    public static final int TEMPLATE_X = 8;
    public static final int TEMPLATE_Y = 24;
    public static final int INVENTORY_Y = 116;
    public static final int WIDTH = 176;
    public static final int HEIGHT = 198;

    @Nullable
    private final AdminShopBlockEntity shop; // null on the client
    private final long initialBuyPrice;
    private final long initialSellPrice;
    private final Container templateView;

    public AdminShopSetupMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, null, buf.readVarLong(), buf.readVarLong(), new SimpleContainer(1));
    }

    private AdminShopSetupMenu(int containerId, Inventory inventory, @Nullable AdminShopBlockEntity shop, long buyPrice,
            long sellPrice, Container templateView) {
        super(ModMenus.ADMIN_SHOP_SETUP.get(), containerId);
        this.shop = shop;
        this.initialBuyPrice = buyPrice;
        this.initialSellPrice = sellPrice;
        this.templateView = templateView;
        addSlot(new GhostSlot(templateView, 0, TEMPLATE_X, TEMPLATE_Y));
        addPlayerInventory(inventory, 8, INVENTORY_Y);
    }

    public static void open(ServerPlayer player, AdminShopBlockEntity shop) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AdminShopSetupMenu(id, inventory, shop, shop.price(),
                shop.sellPrice(), new SimpleContainer(shop.template().copy())),
                Component.translatable("container.reikoeconomy.admin_shop_setup")), buf -> {
            buf.writeVarLong(shop.price());
            buf.writeVarLong(shop.sellPrice());
        });
    }

    @Nullable
    public AdminShopBlockEntity shop() {
        return shop;
    }

    public long initialBuyPrice() {
        return initialBuyPrice;
    }

    public long initialSellPrice() {
        return initialSellPrice;
    }

    public ItemStack template() {
        return templateView.getItem(0);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == TEMPLATE_SLOT) {
            if (clickType == ClickType.PICKUP && shop != null && player instanceof ServerPlayer serverPlayer) {
                ShopActionResult result;
                if (!ShopAccess.isShopAdmin(serverPlayer)) {
                    result = ShopActionResult.fail("shop.reikoeconomy.admin.protected");
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
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return shop == null || (Container.stillValidBlockEntity(shop, player)
                && player instanceof ServerPlayer serverPlayer && ShopAccess.isShopAdmin(serverPlayer));
    }
}

package com.reiko.economy.menu;

import com.reiko.economy.block.entity.AdminShopBlockEntity;
import com.reiko.economy.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** A player's view of an admin shop: buy from it and/or sell to it. Trades go through {@code ShopTradePayload}. */
public final class AdminShopTradeMenu extends ShopMenuBase {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 170;

    @Nullable
    private final AdminShopBlockEntity shop; // null on the client
    private final ItemStack template;
    private final long buyPrice;
    private final long sellPrice;

    public AdminShopTradeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, null, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong());
    }

    private AdminShopTradeMenu(int containerId, @Nullable AdminShopBlockEntity shop, ItemStack template, long buyPrice, long sellPrice) {
        super(ModMenus.ADMIN_SHOP_TRADE.get(), containerId);
        this.shop = shop;
        this.template = template;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public static void open(ServerPlayer player, AdminShopBlockEntity shop) {
        ItemStack template = shop.template().copy();
        long buy = shop.sellsToPlayers() ? shop.price() : 0;
        long sell = shop.buysFromPlayers() ? shop.sellPrice() : 0;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AdminShopTradeMenu(id, shop, template, buy, sell),
                Component.translatable("container.reikoeconomy.admin_shop_trade")), buf -> {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, template);
            buf.writeVarLong(buy);
            buf.writeVarLong(sell);
        });
    }

    @Nullable
    public AdminShopBlockEntity shop() {
        return shop;
    }

    public ItemStack template() {
        return template;
    }

    /** Price per bundle when buying from the shop; 0 if it doesn't sell. */
    public long buyPrice() {
        return buyPrice;
    }

    /** Price per bundle when selling to the shop; 0 if it doesn't buy. */
    public long sellPrice() {
        return sellPrice;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return shop == null || Container.stillValidBlockEntity(shop, player);
    }
}

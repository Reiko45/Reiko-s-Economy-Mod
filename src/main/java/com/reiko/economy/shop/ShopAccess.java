package com.reiko.economy.shop;

import com.reiko.economy.block.entity.ShopBlockEntity;
import com.reiko.economy.command.EconomyPermissions;
import net.minecraft.server.level.ServerPlayer;

/** Who may manage which shop. Checked again on every action, not just when a screen opens. */
public final class ShopAccess {
    private ShopAccess() {
    }

    /** The owner, or anyone with the admin shop permission, may stock, price and break a player shop. */
    public static boolean canManage(ServerPlayer player, ShopBlockEntity shop) {
        return shop.isOwner(player) || isShopAdmin(player);
    }

    public static boolean isShopAdmin(ServerPlayer player) {
        return EconomyPermissions.has(player, EconomyPermissions.SHOP_ADMIN);
    }

    public static boolean canCreateShops(ServerPlayer player) {
        return EconomyPermissions.has(player, EconomyPermissions.SHOP_CREATE);
    }
}

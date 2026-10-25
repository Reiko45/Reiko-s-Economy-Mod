package com.reiko.economy.network;

import com.reiko.economy.bank.AtmService;
import com.reiko.economy.core.CardService;
import com.reiko.economy.menu.AdminShopSetupMenu;
import com.reiko.economy.menu.AtmMenu;
import com.reiko.economy.menu.AdminShopTradeMenu;
import com.reiko.economy.menu.ShopBuyMenu;
import com.reiko.economy.menu.ShopOwnerMenu;
import com.reiko.economy.shop.ShopAccess;
import com.reiko.economy.shop.ShopActionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles client requests. Every request is checked against the menu the player really has open on the
 * server: a client can't trade with a shop it hasn't opened, is too far from, or isn't allowed to manage.
 */
final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {
    }

    static void handleSetPrices(SetShopPricesPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        AbstractContainerMenu menu = openMenu(player, payload.containerId());
        ShopActionResult result;
        if (menu instanceof ShopOwnerMenu owner && owner.shop() != null) {
            result = ShopAccess.canManage(player, owner.shop())
                    ? owner.shop().trySetPrice(payload.price())
                    : ShopActionResult.fail("shop.reikoeconomy.not_yours");
        } else if (menu instanceof AdminShopSetupMenu setup && setup.shop() != null) {
            result = ShopAccess.isShopAdmin(player)
                    ? setup.shop().trySetPrices(payload.price(), payload.sellPrice())
                    : ShopActionResult.fail("shop.reikoeconomy.admin.protected");
        } else {
            return;
        }
        ShopFeedback.send(player, result);
    }

    static void handleTrade(ShopTradePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        AbstractContainerMenu menu = openMenu(player, payload.containerId());
        ShopActionResult result;
        if (menu instanceof ShopBuyMenu buy && buy.shop() != null && payload.action() == ShopTradePayload.BUY) {
            result = buy.shop().sellTo(player, payload.bundles(), payload.expectedPrice(), payload.payment());
        } else if (menu instanceof AdminShopTradeMenu trade && trade.shop() != null) {
            result = switch (payload.action()) {
                case ShopTradePayload.BUY -> trade.shop().sellTo(player, payload.bundles(), payload.expectedPrice(), payload.payment());
                case ShopTradePayload.SELL -> trade.shop().buyFrom(player, payload.bundles(), payload.expectedPrice());
                default -> null;
            };
            if (result == null) {
                return;
            }
        } else {
            return;
        }
        ShopFeedback.send(player, result);
    }

    static void handleAtm(AtmActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(openMenu(player, payload.containerId()) instanceof AtmMenu atm)) {
            return;
        }
        ShopActionResult result = switch (payload.action()) {
            case AtmActionPayload.WITHDRAW -> AtmService.withdraw(player, atm.card(), payload.pin(), payload.amount());
            case AtmActionPayload.DEPOSIT_SLOTS -> AtmService.depositFrom(player, atm.card(), payload.pin(), atm.depositSlots());
            case AtmActionPayload.DEPOSIT_ALL -> AtmService.depositAll(player, atm.card(), payload.pin());
            case AtmActionPayload.CHECK_BALANCE -> AtmService.checkBalance(player, atm.card(), payload.pin());
            case AtmActionPayload.NEW_CARD -> CardService.issue(player, payload.pin());
            default -> null;
        };
        if (result != null) {
            ShopFeedback.send(player, result);
        }
    }

    /** The player's open menu if it matches the id and is still valid (block present, player in range). */
    private static AbstractContainerMenu openMenu(ServerPlayer player, int containerId) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId || !menu.stillValid(player)) {
            return null;
        }
        return menu;
    }
}

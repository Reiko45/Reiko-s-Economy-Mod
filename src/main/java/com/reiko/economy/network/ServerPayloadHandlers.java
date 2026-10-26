package com.reiko.economy.network;

import com.reiko.economy.bank.AtmService;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.CardService;
import com.reiko.economy.core.CreditService;
import com.reiko.economy.auction.AuctionService;
import com.reiko.economy.jobs.ContractService;
import com.reiko.economy.menu.AuctionMenu;
import com.reiko.economy.jobs.JobService;
import com.reiko.economy.menu.AdminShopSetupMenu;
import com.reiko.economy.menu.AdminShopTradeMenu;
import com.reiko.economy.menu.AtmMenu;
import com.reiko.economy.menu.JobBoardMenu;
import com.reiko.economy.menu.ShopBuyMenu;
import com.reiko.economy.menu.ShopOwnerMenu;
import com.reiko.economy.menu.TradeMenu;
import com.reiko.economy.menu.WalletMenu;
import com.reiko.economy.shop.ShopAccess;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.trade.TradeService;
import com.reiko.economy.trade.TradeSession;
import com.reiko.economy.villager.VillagerTradeService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.neoforge.network.PacketDistributor;
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
                    ? owner.shop().trySetPrices(payload.price(), payload.sellPrice())
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
        if (menu instanceof ShopBuyMenu buy && buy.shop() != null) {
            result = switch (payload.action()) {
                case ShopTradePayload.BUY -> buy.shop().sellTo(player, payload.bundles(), payload.expectedPrice(), payload.payment());
                case ShopTradePayload.SELL -> buy.shop().buyFrom(player, payload.bundles(), payload.expectedPrice());
                default -> null;
            };
            if (result == null) {
                return;
            }
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
            case AtmActionPayload.NEW_CREDIT_CARD -> CreditService.issue(player, payload.pin());
            case AtmActionPayload.PAY_BILL -> AtmService.payBill(player, atm.card(), payload.pin(), payload.amount());
            case AtmActionPayload.TO_SAVINGS, AtmActionPayload.FROM_SAVINGS, AtmActionPayload.BORROW, AtmActionPayload.REPAY ->
                    AtmService.bank(player, atm.card(), payload.pin(), payload.action(), payload.amount());
            default -> null;
        };
        if (result != null) {
            ShopFeedback.send(player, result);
        }
    }

    static void handleJob(JobActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(openMenu(player, payload.containerId()) instanceof JobBoardMenu)) {
            return;
        }
        ShopFeedback.send(player, payload.join() ? JobService.join(player, payload.job()) : JobService.quit(player, payload.job()));
        PacketDistributor.sendToPlayer(player, new JobBoardSyncPayload(payload.containerId(), ContractService.boardData(player)));
    }

    static void handleAuctionQuery(AuctionQueryPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && openMenu(player, payload.containerId()) instanceof AuctionMenu menu) {
            menu.setQuery(payload.query());
            sendAuctionPage(player, menu);
        }
    }

    static void handleAuctionAction(AuctionActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(openMenu(player, payload.containerId()) instanceof AuctionMenu menu)) {
            return;
        }
        ShopActionResult result;
        switch (payload.action()) {
            case AuctionActionPayload.BUY -> result = payload.listing().map(id -> AuctionService.buy(player, id, payload.amount()))
                    .orElse(ShopActionResult.fail("auction.reikoeconomy.gone"));
            case AuctionActionPayload.BID -> result = payload.listing().map(id -> AuctionService.bid(player, id, payload.amount()))
                    .orElse(ShopActionResult.fail("auction.reikoeconomy.gone"));
            case AuctionActionPayload.CANCEL -> result = payload.listing().map(id -> AuctionService.cancel(player, id))
                    .orElse(ShopActionResult.fail("auction.reikoeconomy.gone"));
            case AuctionActionPayload.LIST -> {
                result = AuctionService.list(player, menu.sellItem(), payload.amount(), payload.hours());
                if (result.success()) {
                    menu.clearSellItem();
                }
            }
            case AuctionActionPayload.COLLECT -> result = AuctionService.collect(player);
            default -> {
                return;
            }
        }
        ShopFeedback.send(player, result);
        sendAuctionPage(player, menu);
    }

    private static void sendAuctionPage(ServerPlayer player, AuctionMenu menu) {
        PacketDistributor.sendToPlayer(player, new AuctionPagePayload(menu.containerId, menu.query().run(player.server, player.getUUID())));
    }

    static void handleTradeAction(TradeActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(openMenu(player, payload.containerId()) instanceof TradeMenu menu)
                || menu.session() == null) {
            return;
        }
        TradeSession session = menu.session();
        ShopActionResult result = switch (payload.action()) {
            case TradeActionPayload.MONEY -> TradeService.offerMoney(player, session, payload.amount());
            case TradeActionPayload.ACCEPT -> TradeService.accept(player, session, payload.revision());
            case TradeActionPayload.UNACCEPT -> TradeService.unaccept(player, session);
            default -> null;
        };
        if (result != null && !TradeService.isSilent(result)) {
            ShopFeedback.send(player, result);
        }
        if (player.containerMenu == menu) {
            menu.broadcastChanges(); // send the new state now rather than next tick
        }
    }

    static void handleContract(JobContractPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(openMenu(player, payload.containerId()) instanceof JobBoardMenu)) {
            return;
        }
        ShopFeedback.send(player, ContractService.deliver(player, payload.contract()));
        PacketDistributor.sendToPlayer(player, new JobBoardSyncPayload(payload.containerId(), ContractService.boardData(player)));
    }

    static void handleWallet(WalletActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(openMenu(player, payload.containerId()) instanceof WalletMenu wallet)) {
            return;
        }
        ShopActionResult result = wallet.take(player, payload.denomination(), payload.count());
        if (!result.success() && !result.message().getString().isEmpty()) {
            ShopFeedback.send(player, result);
        }
    }

    static void handleVillagerTrade(VillagerTradePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(openMenu(player, payload.containerId()) instanceof MerchantMenu merchant)) {
            return;
        }
        ShopFeedback.send(player, VillagerTradeService.trade(player, merchant, payload.offerIndex(), payload.times(),
                payload.expectedNet(), payload.payment()));
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

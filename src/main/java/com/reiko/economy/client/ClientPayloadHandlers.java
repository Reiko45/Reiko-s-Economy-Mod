package com.reiko.economy.client;

import com.reiko.economy.client.screen.ShopScreenBase;
import com.reiko.economy.menu.AuctionMenu;
import com.reiko.economy.menu.JobBoardMenu;
import com.reiko.economy.menu.TradeMenu;
import com.reiko.economy.network.AuctionPagePayload;
import com.reiko.economy.network.JobBoardSyncPayload;
import com.reiko.economy.network.ShopFeedbackPayload;
import com.reiko.economy.network.TradeStatePayload;
import net.minecraft.client.Minecraft;

/** Client-only payload handling. Never load this class on a dedicated server. */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleJobBoard(JobBoardSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof JobBoardMenu menu
                && menu.containerId == payload.containerId()) {
            menu.update(payload.data());
        }
    }

    public static void handleTradeState(TradeStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof TradeMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setState(payload);
        }
    }

    public static void handleAuctionPage(AuctionPagePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof AuctionMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setPage(payload.page());
        }
    }

    public static void handleFeedback(ShopFeedbackPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ShopScreenBase<?> screen) {
            screen.showFeedback(payload.message(), payload.success());
        } else if (minecraft.player != null) {
            minecraft.player.displayClientMessage(payload.message(), false);
        }
    }
}

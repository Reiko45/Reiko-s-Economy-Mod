package com.reiko.economy.client;

import com.reiko.economy.client.screen.ShopScreenBase;
import com.reiko.economy.menu.JobBoardMenu;
import com.reiko.economy.network.JobBoardSyncPayload;
import com.reiko.economy.network.ShopFeedbackPayload;
import net.minecraft.client.Minecraft;

/** Client-only payload handling. Never load this class on a dedicated server. */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleJobBoard(JobBoardSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof JobBoardMenu menu
                && menu.containerId == payload.containerId()) {
            menu.update(payload.views(), payload.maxJobs());
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

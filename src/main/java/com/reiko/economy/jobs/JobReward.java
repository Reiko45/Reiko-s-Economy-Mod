package com.reiko.economy.jobs;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/**
 * One line of a job file: what counts (a block, mob or item, or a tag of them), what it pays and how much
 * job XP it gives.
 *
 * @param label      what to call the target on the job board, worked out when shown (tags aren't ready while
 *                   job files load)
 * @param payCents   pay before level bonuses and the server multiplier, in cents
 * @param matureOnly for crops: only pays when fully grown, and then even if the player planted it
 */
public record JobReward<T>(Predicate<T> matches, Supplier<Component> label, double payCents, int xp, boolean matureOnly) {
}

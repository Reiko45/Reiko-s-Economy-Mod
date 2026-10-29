package com.reiko.economy.api;

import com.reiko.economy.jobs.JobService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Jobs for other mods' work. A mod reports what a player did as an <em>action</em>, a {@link ResourceLocation} in
 * its own namespace like {@code reikocars:taxi_ride}, and job files pay for it with {@code work} lines and give
 * contracts for it with {@code work} contract lines:
 *
 * <pre>{@code
 * "work": [ { "action": "reikocars:taxi_ride", "label": "Taxi ride", "pay": 2.00, "xp": 10 } ],
 * "contracts": [ { "work": "reikocars:taxi_ride", "label": "Taxi Rides", "icon": "reikocars:taxi", "count": [3, 6], "pay": 3.00, "xp": 8 } ]
 * }</pre>
 *
 * <p>Pay follows the same rules as every job: only jobs the player has joined, level bonuses and the server's
 * multiplier apply, creative and spectator players earn nothing, and pay is deposited every few seconds. The
 * reporting mod decides what counts as real work (no paying for things players can repeat for free).
 * An action is named on the job board by the lang entry {@code job_action.<namespace>.<path>}, or a line's
 * {@code label}.
 */
public final class JobsApi {
    private JobsApi() {
    }

    /** {@code player} did {@code amount} of {@code action}: pays their jobs that pay for it, and counts toward a contract. */
    public static void work(ServerPlayer player, ResourceLocation action, int amount) {
        JobService.work(player, action, amount);
    }

    /** {@code player} did one {@code action}. */
    public static void work(ServerPlayer player, ResourceLocation action) {
        JobService.work(player, action, 1);
    }

    /** Whether any loaded job pays for, or has a contract for, {@code action}: to skip tracking work nobody pays for. */
    public static boolean paysFor(ResourceLocation action) {
        return JobService.paysForWork(action);
    }
}

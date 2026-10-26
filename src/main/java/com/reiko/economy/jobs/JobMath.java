package com.reiko.economy.jobs;

/**
 * Job levels and pay, as plain arithmetic so it can be tested without a game.
 *
 * <p>Level {@code n} needs {@code 100 * n} job XP to reach level {@code n + 1}, so early levels come quickly
 * and later ones take longer. Each level above 1 adds a percentage to everything the job pays.
 */
public final class JobMath {
    private JobMath() {
    }

    /** XP needed to go from {@code level} to the next one. */
    public static long xpToNext(int level) {
        return 100L * Math.max(1, level);
    }

    /** A level and the XP gathered toward the next one. */
    public record Progress(int level, long xp) {
    }

    /** The progress after gaining {@code gained} XP. XP stops counting at {@code maxLevel}. */
    public static Progress addXp(Progress progress, long gained, int maxLevel) {
        int level = progress.level();
        long xp = progress.xp() + Math.max(0, gained);
        while (level < maxLevel && xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
        }
        if (level >= maxLevel) {
            return new Progress(maxLevel, 0);
        }
        return new Progress(level, xp);
    }

    /** The pay bonus at {@code level}, in basis points (hundredths of a percent). */
    public static long bonusBasisPoints(int level, long bonusPerLevelBasisPoints) {
        return (long) (Math.max(1, level) - 1) * bonusPerLevelBasisPoints;
    }

    /**
     * What an action pays at {@code level}, in cents (fractions of a cent are kept and added up before paying).
     *
     * @param baseCents   the job file's pay for the action
     * @param multiplier  the server-wide pay multiplier
     */
    public static double pay(double baseCents, int level, long bonusPerLevelBasisPoints, double multiplier) {
        return baseCents * multiplier * (1 + bonusBasisPoints(level, bonusPerLevelBasisPoints) / 10_000.0);
    }
}

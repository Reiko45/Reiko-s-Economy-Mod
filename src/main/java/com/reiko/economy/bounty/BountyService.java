package com.reiko.economy.bounty;

import com.mojang.authlib.GameProfile;
import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxService;
import com.reiko.economy.shop.ShopActionResult;
import com.reiko.economy.shop.ShopNotices;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Bounties: a player puts money on another player, and whoever kills them in PvP collects it. The money is taken
 * from the poster when they post (plus a fee that leaves the economy) and held until someone collects it, or until
 * it runs out and goes back to the poster. Posters stay anonymous: the board shows targets and amounts, not who
 * paid.
 *
 * <p>Posters can't collect their own bounties, and can't take them back early, so a bounty can't be pulled
 * mid-hunt.
 */
public final class BountyService {
    private static final EconomyService ECO = EconomyService.INSTANCE;
    public static final int PAGE_SIZE = 8;

    private static LongSupplier clock = System::currentTimeMillis;

    private BountyService() {
    }

    /** Replaces the clock for tests, or restores the real one with {@code null}. */
    public static void setClockForTests(@Nullable LongSupplier testClock) {
        clock = testClock == null ? System::currentTimeMillis : testClock;
    }

    public static long now() {
        return clock.getAsLong();
    }

    // ---- posting ----

    /** {@code poster} puts {@code amount} on {@code target}. The amount and the fee come out of their bank account. */
    public static ShopActionResult post(ServerPlayer poster, GameProfile target, long amount) {
        MinecraftServer server = poster.server;
        BountyRules rules = EconomyConfig.bountyRules();
        if (!rules.enabled()) {
            return ShopActionResult.fail("bounty.reikoeconomy.disabled");
        }
        if (!server.isPvpAllowed()) {
            return ShopActionResult.fail("bounty.reikoeconomy.no_pvp");
        }
        if (target.getId().equals(poster.getUUID())) {
            return ShopActionResult.fail("bounty.reikoeconomy.self");
        }
        if (!ECO.hasAccount(server, target.getId())) {
            return ShopActionResult.fail("bounty.reikoeconomy.unknown_player", target.getName());
        }
        if (amount < rules.minAmount()) {
            return ShopActionResult.fail("bounty.reikoeconomy.too_small", ECO.format(rules.minAmount()));
        }
        BountyData data = BountyData.get(server);
        if (data.postedBy(poster.getUUID()).size() >= rules.maxActive()) {
            return ShopActionResult.fail("bounty.reikoeconomy.too_many", rules.maxActive());
        }
        long fee = rules.fee(amount);
        long balance = ECO.getBalance(server, poster.getUUID());
        if (balance < amount + fee) {
            return ShopActionResult.fail("bounty.reikoeconomy.cant_afford", ECO.format(amount + fee), ECO.format(amount),
                    ECO.format(fee), ECO.format(balance));
        }
        if (!ECO.withdraw(server, poster.getUUID(), amount, "bounty.post").isSuccess()) {
            return ShopActionResult.fail("bounty.reikoeconomy.cant_afford", ECO.format(amount + fee), ECO.format(amount),
                    ECO.format(fee), ECO.format(balance));
        }
        if (fee > 0) {
            TaxService.take(server, poster.getUUID(), TaxKind.BOUNTY, fee); // checked above
        }
        long now = now();
        data.add(new BountyData.Bounty(UUID.randomUUID(), poster.getUUID(), poster.getGameProfile().getName(), target.getId(),
                target.getName(), amount, now, now + rules.durationMillis()));
        long days = rules.durationMillis() / 86_400_000L;
        ShopNotices.alert(server, target.getId(), Component.translatable("bounty.reikoeconomy.warning", ECO.format(amount),
                ECO.format(data.totalOn(target.getId()))).withStyle(ChatFormatting.RED));
        return ShopActionResult.ok(fee > 0
                ? Component.translatable("bounty.reikoeconomy.posted_fee", ECO.format(amount), target.getName(), ECO.format(fee), days)
                : Component.translatable("bounty.reikoeconomy.posted", ECO.format(amount), target.getName(), days));
    }

    // ---- collecting ----

    /**
     * {@code target} was killed by {@code killer}: the killer collects every bounty on them except their own.
     * Returns what they collected.
     */
    public static long claim(ServerPlayer target, ServerPlayer killer) {
        MinecraftServer server = target.server;
        if (killer.getUUID().equals(target.getUUID()) || !EconomyConfig.bountyRules().enabled()) {
            return 0;
        }
        BountyData data = BountyData.get(server);
        List<BountyData.Bounty> collected = new ArrayList<>();
        long total = 0;
        for (BountyData.Bounty bounty : data.on(target.getUUID())) {
            if (!bounty.poster().equals(killer.getUUID())) {
                collected.add(bounty);
                total += bounty.amount();
            }
        }
        if (total <= 0) {
            return 0;
        }
        if (!ECO.deposit(server, killer.getUUID(), total, "bounty.claim").isSuccess()) {
            return 0; // the killer's balance is at the limit; the bounties stay up
        }
        collected.forEach(data::remove);
        String amount = ECO.format(total);
        String targetName = target.getGameProfile().getName();
        String killerName = killer.getGameProfile().getName();
        killer.sendSystemMessage(Component.translatable("bounty.reikoeconomy.collected", amount, targetName)
                .withStyle(ChatFormatting.GOLD));
        if (EconomyConfig.bountyRules().announceClaims()) {
            Component announcement = Component.translatable("bounty.reikoeconomy.announce", killerName, amount, targetName)
                    .withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != killer) {
                    player.sendSystemMessage(announcement);
                }
            }
        } else {
            target.sendSystemMessage(Component.translatable("bounty.reikoeconomy.collected_on_you", killerName, amount)
                    .withStyle(ChatFormatting.RED));
        }
        for (BountyData.Bounty bounty : collected) {
            ShopNotices.alert(server, bounty.poster(), Component.translatable("bounty.reikoeconomy.yours_collected",
                    ECO.format(bounty.amount()), targetName, killerName).withStyle(ChatFormatting.GOLD));
        }
        return total;
    }

    // ---- running out ----

    /** Ends bounties whose time is up and gives the money back to their posters. */
    public static void tick(MinecraftServer server) {
        BountyData data = BountyData.get(server);
        long now = now();
        for (BountyData.Bounty bounty : data.all()) {
            if (bounty.expiresAt() <= now) {
                refund(server, data, bounty, "bounty.reikoeconomy.expired");
            }
        }
    }

    /** Takes down every bounty on {@code target}, refunding the posters. For admins. Returns how many. */
    public static int clear(MinecraftServer server, UUID target) {
        BountyData data = BountyData.get(server);
        List<BountyData.Bounty> bounties = data.on(target);
        for (BountyData.Bounty bounty : bounties) {
            refund(server, data, bounty, "bounty.reikoeconomy.cleared");
        }
        return bounties.size();
    }

    private static void refund(MinecraftServer server, BountyData data, BountyData.Bounty bounty, String messageKey) {
        if (!ECO.deposit(server, bounty.poster(), bounty.amount(), "bounty.refund").isSuccess()) {
            return; // their balance is at the limit; try again later
        }
        data.remove(bounty);
        ShopNotices.alert(server, bounty.poster(), Component.translatable(messageKey, ECO.format(bounty.amount()),
                bounty.targetName()).withStyle(ChatFormatting.GOLD));
    }

    // ---- looking ----

    /** One target on the board: how much is on them, in how many bounties, and when the first one runs out. */
    public record Target(UUID player, String name, long total, int count, long firstExpiry) {
    }

    /** Everyone with a bounty on them, the biggest total first. */
    public static List<Target> board(MinecraftServer server) {
        Map<UUID, Target> targets = new LinkedHashMap<>();
        for (BountyData.Bounty bounty : BountyData.get(server).all()) {
            Target old = targets.get(bounty.target());
            targets.put(bounty.target(), old == null
                    ? new Target(bounty.target(), bounty.targetName(), bounty.amount(), 1, bounty.expiresAt())
                    : new Target(bounty.target(), old.name(), old.total() + bounty.amount(), old.count() + 1,
                            Math.min(old.firstExpiry(), bounty.expiresAt())));
        }
        List<Target> list = new ArrayList<>(targets.values());
        list.sort(Comparator.comparingLong(Target::total).reversed().thenComparing(Target::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** How long until {@code time}, like "6d 23h", "5h 10m" or "12m". */
    public static String timeLeft(long time) {
        long minutes = Math.max(0, time - now()) / 60_000;
        long days = minutes / (24 * 60);
        long hours = minutes / 60 % 24;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        return hours > 0 ? hours + "h " + minutes % 60 + "m" : Math.max(1, minutes) + "m";
    }
}

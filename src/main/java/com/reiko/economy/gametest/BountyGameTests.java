package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.bounty.BountyData;
import com.reiko.economy.bounty.BountyService;
import com.reiko.economy.core.TaxKind;
import com.reiko.economy.core.TaxLedger;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for bounties. They assume the default rules: at least $10, a 5% posting fee, 5 per player and 7
 * days. PvP is off on the game-test server, so each test turns it on and back off.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class BountyGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final long DAY = 86_400_000L;

    private BountyGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void postingHoldsTheMoneyAndChargesAFee(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinMockPlayer(helper);
        boolean pvp = server.isPvpAllowed();
        try {
            ECO.setBalance(server, alex.getUUID(), 10_000, "gametest.setup");
            server.setPvpAllowed(false);
            check(helper, !BountyService.post(alex, sam.getGameProfile(), 5_000).success(), "no bounties without PvP");
            server.setPvpAllowed(true);

            long feesBefore = TaxLedger.get(server).collected(TaxKind.BOUNTY);
            check(helper, !BountyService.post(alex, alex.getGameProfile(), 5_000).success(), "not on yourself");
            check(helper, !BountyService.post(alex, sam.getGameProfile(), 500).success(), "$5 is under the $10 minimum");
            check(helper, BountyService.post(alex, sam.getGameProfile(), 5_000).success(), "post $50 on sam");
            helper.assertValueEqual(ECO.getBalance(server, alex.getUUID()), 10_000L - 5_000 - 250, "$50 held plus the $2.50 fee");
            helper.assertValueEqual(TaxLedger.get(server).collected(TaxKind.BOUNTY) - feesBefore, 250L, "the fee left the economy");
            helper.assertValueEqual(BountyData.get(server).totalOn(sam.getUUID()), 5_000L, "$50 is on sam");
            check(helper, BountyService.board(server).stream().anyMatch(target -> target.player().equals(sam.getUUID())
                    && target.total() == 5_000), "sam is on the board");
            check(helper, !BountyService.post(alex, sam.getGameProfile(), 5_000).success(), "$52.50 is more than the $47.50 left");
        } finally {
            server.setPvpAllowed(pvp);
            BountyService.clear(server, sam.getUUID());
            leave(server, alex, sam);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theKillerCollectsButNotTheirOwnBounty(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinMockPlayer(helper);
        ServerPlayer kim = joinMockPlayer(helper);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            ECO.setBalance(server, alex.getUUID(), 10_000, "gametest.setup");
            ECO.setBalance(server, kim.getUUID(), 5_000, "gametest.setup");
            check(helper, BountyService.post(alex, sam.getGameProfile(), 2_000).success(), "alex posts $20 on sam");
            check(helper, BountyService.post(kim, sam.getGameProfile(), 1_000).success(), "kim posts $10 on sam");

            sam.setGameMode(GameType.SURVIVAL);
            // A kill that ignores the few seconds of protection new players get (fake players never tick it down),
            // still credited to kim.
            DamageSource byKim = new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DamageTypes.GENERIC_KILL), kim);
            sam.hurt(byKim, Float.MAX_VALUE);
            check(helper, sam.isDeadOrDying(), "kim killed sam");
            helper.assertValueEqual(ECO.getBalance(server, kim.getUUID()), 5_000L - 1_050 + 2_000,
                    "kim collected alex's $20, not her own $10");
            helper.assertValueEqual(BountyData.get(server).totalOn(sam.getUUID()), 1_000L, "kim's own bounty is still up");

            helper.assertValueEqual(BountyService.claim(sam, alex), 1_000L, "alex can collect kim's");
            helper.assertValueEqual(BountyData.get(server).totalOn(sam.getUUID()), 0L, "nothing left on sam");
            helper.assertValueEqual(BountyService.claim(sam, kim), 0L, "and nothing more to collect");
        } finally {
            server.setPvpAllowed(pvp);
            BountyService.clear(server, sam.getUUID());
            leave(server, alex, sam, kim);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void bountiesRunOutAndAreRefunded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer alex = joinMockPlayer(helper);
        ServerPlayer sam = joinMockPlayer(helper);
        boolean pvp = server.isPvpAllowed();
        long[] now = {System.currentTimeMillis()};
        BountyService.setClockForTests(() -> now[0]);
        try {
            server.setPvpAllowed(true);
            ECO.setBalance(server, alex.getUUID(), 10_000, "gametest.setup");
            check(helper, BountyService.post(alex, sam.getGameProfile(), 4_000).success(), "post $40");
            now[0] += 7 * DAY - 1;
            BountyService.tick(server);
            helper.assertValueEqual(BountyData.get(server).totalOn(sam.getUUID()), 4_000L, "still up just before 7 days");
            now[0] += 1;
            BountyService.tick(server);
            helper.assertValueEqual(BountyData.get(server).totalOn(sam.getUUID()), 0L, "gone after 7 days");
            helper.assertValueEqual(ECO.getBalance(server, alex.getUUID()), 10_000L - 200, "the $40 came back, not the $2 fee");

            check(helper, BountyService.post(alex, sam.getGameProfile(), 4_000).success(), "post again");
            helper.assertValueEqual(BountyService.clear(server, sam.getUUID()), 1, "an admin clears it");
            helper.assertValueEqual(ECO.getBalance(server, alex.getUUID()), 10_000L - 400, "refunded again, less both fees");
        } finally {
            BountyService.setClockForTests(null);
            server.setPvpAllowed(pvp);
            leave(server, alex, sam);
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

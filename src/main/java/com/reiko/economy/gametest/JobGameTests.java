package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.jobs.JobService;
import com.reiko.economy.jobs.JobView;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for jobs, using the built-in job files at their default pay. */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class JobGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SPOT = new BlockPos(1, 1, 1);
    private static final ResourceLocation MINER = job("miner");
    private static final ResourceLocation LUMBERJACK = job("lumberjack");
    private static final ResourceLocation DIGGER = job("digger");
    private static final ResourceLocation FARMER = job("farmer");
    private static final ResourceLocation HUNTER = job("hunter");

    private JobGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void joiningIsLimitedAndQuittingKeepsTheLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = worker(helper);
        try {
            check(helper, JobService.join(player, MINER).success(), "join Miner");
            check(helper, !JobService.join(player, MINER).success(), "can't join the same job twice");
            check(helper, JobService.join(player, LUMBERJACK).success(), "join Lumberjack");
            check(helper, JobService.join(player, DIGGER).success(), "join Digger");
            check(helper, !JobService.join(player, FARMER).success(), "a fourth job is one too many");
            check(helper, !JobService.join(player, job("astronaut")).success(), "no such job");

            for (int i = 0; i < 4; i++) {
                breakBlock(helper, player, Blocks.DIAMOND_ORE.defaultBlockState()); // 30 XP each
            }
            helper.assertValueEqual(view(player, MINER).level(), 2, "120 XP is level 2");
            check(helper, JobService.quit(player, MINER).success(), "quit Miner");
            check(helper, JobService.join(player, FARMER).success(), "room for Farmer now");
            check(helper, JobService.quit(player, FARMER).success(), "quit Farmer");
            check(helper, JobService.join(player, MINER).success(), "rejoin Miner");
            helper.assertValueEqual(view(player, MINER).level(), 2, "the level was kept");
        } finally {
            JobService.deposit(server, player.getUUID());
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void minersArePaidForOresButNotForBlocksTheyPlaced(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = worker(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 0, "gametest.setup");
            breakBlock(helper, player, Blocks.DIAMOND_ORE.defaultBlockState());
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 0L, "no pay without the job");

            check(helper, JobService.join(player, MINER).success(), "join Miner");
            breakBlock(helper, player, Blocks.DIAMOND_ORE.defaultBlockState());
            breakBlock(helper, player, Blocks.STONE.defaultBlockState());
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 502L, "$5.00 for diamond ore and 2 cents for stone");

            // Placing the ore yourself and breaking it again pays nothing.
            helper.setBlock(SPOT, Blocks.DIAMOND_ORE);
            BlockPos pos = helper.absolutePos(SPOT);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), pos),
                    Blocks.STONE.defaultBlockState(), player));
            player.gameMode.destroyBlock(pos);
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 502L, "a placed ore doesn't pay");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void farmersAreOnlyPaidForGrownCrops(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = worker(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 0, "gametest.setup");
            check(helper, JobService.join(player, FARMER).success(), "join Farmer");
            helper.setBlock(SPOT.below(), Blocks.FARMLAND);
            breakBlock(helper, player, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 0L, "half-grown wheat pays nothing");

            breakBlock(helper, player, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 10L, "grown wheat pays 10 cents");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void huntersArePaidButNotForSpawnerMobs(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = worker(helper);
        try {
            ECO.setBalance(server, player.getUUID(), 0, "gametest.setup");
            check(helper, JobService.join(player, HUNTER).success(), "join Hunter");
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, SPOT);
            zombie.hurt(player.damageSources().playerAttack(player), 1_000);
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 100L, "$1.00 for a zombie");

            Zombie farmed = helper.spawn(EntityType.ZOMBIE, SPOT);
            farmed.getPersistentData().putBoolean(ReikoEconomy.MODID + ":from_spawner", true);
            farmed.hurt(player.damageSources().playerAttack(player), 1_000);
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 100L, "a spawner zombie pays nothing");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void higherLevelsPayMore(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = worker(helper);
        try {
            check(helper, JobService.join(player, MINER).success(), "join Miner");
            for (int i = 0; i < 4; i++) {
                breakBlock(helper, player, Blocks.DIAMOND_ORE.defaultBlockState());
            }
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(view(player, MINER).level(), 2, "level 2");
            long before = ECO.getBalance(server, player.getUUID());
            breakBlock(helper, player, Blocks.DIAMOND_ORE.defaultBlockState());
            JobService.deposit(server, player.getUUID());
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()) - before, 510L, "level 2 pays 2% more: $5.10");
        } finally {
            leave(server, player);
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static ResourceLocation job(String name) {
        return ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, name);
    }

    /** A player in survival mode (creative players don't get job pay). */
    private static ServerPlayer worker(GameTestHelper helper) {
        ServerPlayer player = joinMockPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Puts {@code state} at the test spot and has the player break it, firing the normal break events. */
    private static void breakBlock(GameTestHelper helper, ServerPlayer player, BlockState state) {
        helper.setBlock(SPOT, state);
        player.gameMode.destroyBlock(helper.absolutePos(SPOT));
    }

    private static JobView view(ServerPlayer player, ResourceLocation job) {
        return JobService.views(player).stream().filter(view -> view.id().equals(job)).findFirst().orElseThrow();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

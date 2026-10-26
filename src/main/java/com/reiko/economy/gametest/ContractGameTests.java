package com.reiko.economy.gametest;

import static com.reiko.economy.gametest.GameTestSupport.ECO;
import static com.reiko.economy.gametest.GameTestSupport.joinMockPlayer;
import static com.reiko.economy.gametest.GameTestSupport.leave;

import com.google.gson.JsonParser;
import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.jobs.ContractKind;
import com.reiko.economy.jobs.ContractService;
import com.reiko.economy.jobs.ContractsData;
import com.reiko.economy.jobs.JobDefinition;
import com.reiko.economy.jobs.JobDefinitions;
import com.reiko.economy.jobs.JobService;
import com.reiko.economy.jobs.JobsData;
import com.reiko.economy.shop.ShopActionResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for daily contracts. They add a made-up "courier" job with exactly three contracts, so every
 * player who joins it gets all three, and put the loaded jobs back afterwards.
 */
@GameTestHolder(ReikoEconomy.MODID)
@PrefixGameTestTemplate(false)
public final class ContractGameTests {
    private static final String EMPTY = EconomyGameTests.EMPTY;
    private static final BlockPos SPOT = new BlockPos(1, 2, 1);
    private static final ResourceLocation COURIER = ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "test_courier");
    private static final String COURIER_JSON = """
            {
              "name": "Courier",
              "icon": "minecraft:bundle",
              "contracts": [
                { "deliver": "minecraft:dried_kelp", "count": 5, "pay": 1.00, "xp": 2 },
                { "break": "minecraft:gilded_blackstone", "count": 2, "pay": 3.00, "xp": 4 },
                { "kill": "minecraft:zombie", "count": 1, "pay": 2.00, "xp": 6 }
              ]
            }""";

    private ContractGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void contractsComeFromYourJobsAndChangeEachDay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = worker(helper);
        ServerPlayer jobless = worker(helper);
        List<JobDefinition> saved = addCourier();
        ContractService.setDayForTests(20_000L);
        try {
            check(helper, ContractService.current(jobless).isEmpty(), "no job, no contracts");
            check(helper, !ContractService.boardData(jobless).hasJobs(), "the board says to join a job");

            check(helper, JobService.join(player, COURIER).success(), "join the courier job");
            List<ContractsData.Contract> today = ContractService.current(player);
            helper.assertValueEqual(today.size(), 3, "three contracts, all from the courier job");
            check(helper, today == ContractService.current(player), "the same ones all day");

            ItemStack kelp = new ItemStack(Items.DRIED_KELP, 5);
            player.getInventory().add(kelp);
            int deliver = index(today, ContractKind.DELIVER);
            check(helper, ContractService.deliver(player, deliver).success(), "deliver the kelp");
            check(helper, today.get(deliver).done(), "done");

            ContractService.setDayForTests(20_001L);
            List<ContractsData.Contract> tomorrow = ContractService.current(player);
            helper.assertValueEqual(tomorrow.size(), 3, "three new ones the next day");
            check(helper, tomorrow.stream().noneMatch(ContractsData.Contract::done), "with no progress");
        } finally {
            JobDefinitions.set(saved);
            ContractService.setDayForTests(null);
            leave(server, player, jobless);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void deliveriesTakeItemsAndPayWhenDone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = worker(helper);
        List<JobDefinition> saved = addCourier();
        ContractService.setDayForTests(30_000L);
        try {
            ECO.setBalance(server, player.getUUID(), 0, "gametest.setup");
            check(helper, JobService.join(player, COURIER).success(), "join the courier job");
            List<ContractsData.Contract> contracts = ContractService.current(player);
            int deliver = index(contracts, ContractKind.DELIVER);

            check(helper, !ContractService.deliver(player, deliver).success(), "nothing to deliver yet");
            player.getInventory().add(new ItemStack(Items.DRIED_KELP, 3));
            ShopActionResult first = ContractService.deliver(player, deliver);
            check(helper, first.success(), "deliver 3: " + first.message().getString());
            helper.assertValueEqual(contracts.get(deliver).progress(), 3, "3 of 5");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 0L, "not paid until it's done");

            player.getInventory().add(new ItemStack(Items.DRIED_KELP, 4));
            check(helper, ContractService.deliver(player, deliver).success(), "deliver the rest");
            helper.assertValueEqual(player.getInventory().countItem(Items.DRIED_KELP), 2, "only the 2 needed were taken");
            check(helper, contracts.get(deliver).done(), "done");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 500L, "5 at $1.00 each");
            helper.assertValueEqual(JobsData.get(server).progress(player.getUUID(), COURIER).xp(), 10L, "5 at 2 XP each");
            check(helper, !ContractService.deliver(player, deliver).success(), "can't deliver to a finished contract");
        } finally {
            JobDefinitions.set(saved);
            ContractService.setDayForTests(null);
            leave(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void workCountsButNotPlacedBlocksOrCreativeMode(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = worker(helper);
        List<JobDefinition> saved = addCourier();
        ContractService.setDayForTests(40_000L);
        try {
            ECO.setBalance(server, player.getUUID(), 0, "gametest.setup");
            check(helper, JobService.join(player, COURIER).success(), "join the courier job");
            List<ContractsData.Contract> contracts = ContractService.current(player);
            ContractsData.Contract breaking = contracts.get(index(contracts, ContractKind.BREAK));
            ContractsData.Contract killing = contracts.get(index(contracts, ContractKind.KILL));

            // A block the player placed doesn't count.
            helper.setBlock(SPOT, Blocks.GILDED_BLACKSTONE);
            BlockPos pos = helper.absolutePos(SPOT);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), pos),
                    Blocks.STONE.defaultBlockState(), player));
            player.gameMode.destroyBlock(pos);
            helper.assertValueEqual(breaking.progress(), 0, "a placed block doesn't count");

            player.setGameMode(GameType.CREATIVE);
            helper.setBlock(SPOT, Blocks.GILDED_BLACKSTONE);
            player.gameMode.destroyBlock(pos);
            helper.assertValueEqual(breaking.progress(), 0, "creative mode doesn't count");
            player.setGameMode(GameType.SURVIVAL);

            for (int i = 0; i < 2; i++) {
                helper.setBlock(SPOT, Blocks.GILDED_BLACKSTONE);
                player.gameMode.destroyBlock(pos);
            }
            check(helper, breaking.done(), "two natural blocks finish it");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 600L, "2 at $3.00 each");

            Zombie zombie = helper.spawn(EntityType.ZOMBIE, SPOT.above());
            zombie.hurt(player.damageSources().playerAttack(player), 1_000);
            check(helper, killing.done(), "the zombie finished the kill contract");
            helper.assertValueEqual(ECO.getBalance(server, player.getUUID()), 800L, "$2.00 more");
        } finally {
            JobDefinitions.set(saved);
            ContractService.setDayForTests(null);
            leave(server, player);
        }
        helper.succeed();
    }

    // ---- helpers ----

    /** Adds the courier job to the loaded jobs and returns what was loaded before, to put back. */
    private static List<JobDefinition> addCourier() {
        List<JobDefinition> saved = List.copyOf(JobDefinitions.all());
        List<JobDefinition> with = new ArrayList<>(saved);
        with.add(JobDefinitions.parse(COURIER, JsonParser.parseString(COURIER_JSON).getAsJsonObject()));
        JobDefinitions.set(with);
        return saved;
    }

    private static int index(List<ContractsData.Contract> contracts, ContractKind kind) {
        JobDefinition courier = JobDefinitions.get(COURIER).orElseThrow();
        for (int i = 0; i < contracts.size(); i++) {
            if (courier.contracts().get(contracts.get(i).line()).kind() == kind) {
                return i;
            }
        }
        throw new IllegalStateException("no " + kind + " contract");
    }

    private static ServerPlayer worker(GameTestHelper helper) {
        ServerPlayer player = joinMockPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}

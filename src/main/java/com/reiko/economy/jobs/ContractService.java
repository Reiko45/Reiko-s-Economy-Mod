package com.reiko.economy.jobs;

import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.shop.InventorySync;
import com.reiko.economy.shop.ShopActionResult;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.LongSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Daily contracts. Each real day (at the server's midnight) every player gets a few contracts picked from the
 * jobs they have: deliver items to the job board, or do some work, for a reward in money and job XP. Work
 * counts the same way job pay does: no blocks the player placed (except fully grown crops), and no spawner
 * mobs unless the server pays for them. Players in creative mode make no progress.
 */
public final class ContractService {
    private static final LongSupplier REAL_DAY = () -> LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    private static LongSupplier today = REAL_DAY;

    private ContractService() {
    }

    /** Pretends it's another day, for game tests. Null goes back to the real date. */
    public static void setDayForTests(Long day) {
        today = day == null ? REAL_DAY : () -> day;
    }

    public static long today() {
        return today.getAsLong();
    }

    /** Seconds until new contracts, at the next midnight on the server's clock. */
    public static long secondsToNewDay() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        return Math.max(0, Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds());
    }

    // ---- handing out ----

    /** The player's contracts for today, giving out new ones if it's a new day (or they had none and now have a job). */
    public static List<ContractsData.Contract> current(ServerPlayer player) {
        ContractsData data = ContractsData.get(player.server);
        ContractsData.Day day = data.day(player.getUUID());
        long now = today();
        if (day.day != now || day.contracts.isEmpty()) {
            List<ContractsData.Contract> rolled = roll(player, now);
            if (day.day != now || !rolled.isEmpty()) {
                data.replace(day, now, rolled);
            }
        }
        return day.contracts;
    }

    /**
     * Picks the day's contracts from the player's jobs, taking turns between jobs so they get a mix. The same
     * player on the same day always gets the same picks.
     */
    static List<ContractsData.Contract> roll(ServerPlayer player, long day) {
        int wanted = EconomyConfig.contractsPerDay();
        List<ContractsData.Contract> picked = new ArrayList<>();
        if (wanted <= 0 || !EconomyConfig.jobsEnabled()) {
            return picked;
        }
        Random random = new Random(player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits() ^ (day * 0x9E3779B97F4A7C15L));
        List<JobDefinition> jobs = new ArrayList<>();
        for (ResourceLocation id : JobService.activeJobs(player)) {
            JobDefinitions.get(id).filter(job -> !job.contracts().isEmpty()).ifPresent(jobs::add);
        }
        Collections.shuffle(jobs, random);
        List<List<Integer>> remaining = new ArrayList<>();
        for (JobDefinition job : jobs) {
            List<Integer> lines = new ArrayList<>();
            for (int i = 0; i < job.contracts().size(); i++) {
                lines.add(i);
            }
            Collections.shuffle(lines, random);
            remaining.add(lines);
        }
        double multiplier = EconomyConfig.jobPayMultiplier();
        boolean any = true;
        while (picked.size() < wanted && any) {
            any = false;
            for (int j = 0; j < jobs.size() && picked.size() < wanted; j++) {
                if (remaining.get(j).isEmpty()) {
                    continue;
                }
                any = true;
                int index = remaining.get(j).remove(0);
                ContractLine line = jobs.get(j).contracts().get(index);
                int count = line.minCount() + random.nextInt(line.maxCount() - line.minCount() + 1);
                long reward = Math.round(count * line.payCents() * multiplier);
                picked.add(new ContractsData.Contract(jobs.get(j).id(), index, count, reward, count * line.xp()));
            }
        }
        return picked;
    }

    private static Optional<ContractLine> lineOf(ContractsData.Contract contract) {
        return JobDefinitions.get(contract.job())
                .filter(job -> contract.line() >= 0 && contract.line() < job.contracts().size())
                .map(job -> job.contracts().get(contract.line()));
    }

    private static boolean canWork(ServerPlayer player) {
        return EconomyConfig.jobsEnabled() && !player.isCreative() && !player.isSpectator();
    }

    // ---- work ----

    public static void blockBroken(ServerPlayer player, BlockState state, boolean placedByPlayer) {
        work(player, ContractKind.BREAK, state, 1, line -> line.matureOnly() ? JobService.isFullyGrown(state) : !placedByPlayer);
    }

    public static void mobKilled(ServerPlayer player, Entity victim) {
        work(player, ContractKind.KILL, victim, 1, line -> true);
    }

    public static void fishCaught(ServerPlayer player, List<ItemStack> catches) {
        for (ItemStack stack : catches) {
            work(player, ContractKind.FISH, stack, stack.getCount(), line -> true);
        }
    }

    public static void animalsBred(ServerPlayer player, Entity parent) {
        work(player, ContractKind.BREED, parent, 1, line -> true);
    }

    /** Another mod's action, reported through {@link com.reiko.economy.api.JobsApi}. */
    public static void work(ServerPlayer player, ResourceLocation action, int amount) {
        work(player, ContractKind.WORK, action, amount, line -> true);
    }

    private static void work(ServerPlayer player, ContractKind kind, Object target, int amount,
                             java.util.function.Predicate<ContractLine> counts) {
        if (!canWork(player)) {
            return;
        }
        ContractsData data = ContractsData.get(player.server);
        for (ContractsData.Contract contract : current(player)) {
            if (contract.done()) {
                continue;
            }
            Optional<ContractLine> line = lineOf(contract);
            if (line.isEmpty() || line.get().kind() != kind || !line.get().matches().test(target) || !counts.test(line.get())) {
                continue;
            }
            if (data.addProgress(contract, amount)) {
                finish(player, contract, line.get());
            } else {
                player.displayClientMessage(Component.translatable("contract.reikoeconomy.progress", line.get().label().get(),
                        contract.progress(), contract.count()).withStyle(ChatFormatting.AQUA), true);
            }
            return; // one action counts toward one contract
        }
    }

    // ---- deliveries ----

    /** Hands in the player's matching items for delivery contract {@code index}, up to what's still needed. */
    public static ShopActionResult deliver(ServerPlayer player, int index) {
        if (!EconomyConfig.jobsEnabled()) {
            return ShopActionResult.fail("job.reikoeconomy.disabled");
        }
        if (player.isCreative() || player.isSpectator()) {
            return ShopActionResult.fail("contract.reikoeconomy.creative");
        }
        List<ContractsData.Contract> contracts = current(player);
        if (index < 0 || index >= contracts.size()) {
            return ShopActionResult.fail("contract.reikoeconomy.gone");
        }
        ContractsData.Contract contract = contracts.get(index);
        Optional<ContractLine> line = lineOf(contract);
        if (line.isEmpty() || line.get().kind() != ContractKind.DELIVER) {
            return ShopActionResult.fail("contract.reikoeconomy.gone");
        }
        if (contract.done()) {
            return ShopActionResult.fail("contract.reikoeconomy.already_done");
        }
        int held = held(player, line.get());
        if (held <= 0) {
            return ShopActionResult.fail("contract.reikoeconomy.nothing_to_deliver", line.get().label().get());
        }
        int handIn = Math.min(held, contract.remaining());
        List<ItemStack> before = InventorySync.snapshot(player);
        int left = handIn;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                break;
            }
            if (!stack.isEmpty() && line.get().matches().test(stack)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
        InventorySync.sendChanges(player, before);
        ContractsData data = ContractsData.get(player.server);
        if (data.addProgress(contract, handIn)) {
            return ShopActionResult.ok(finish(player, contract, line.get()));
        }
        return ShopActionResult.ok(Component.translatable("contract.reikoeconomy.delivered", handIn, line.get().label().get(),
                contract.remaining()));
    }

    /** Matching items in the player's main inventory and hotbar. */
    private static int held(ServerPlayer player, ContractLine line) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && line.matches().test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Pays a finished contract into the bank and adds the job XP. Returns the message it showed. */
    private static Component finish(ServerPlayer player, ContractsData.Contract contract, ContractLine line) {
        EconomyService eco = EconomyService.INSTANCE;
        JobDefinition job = JobDefinitions.get(contract.job()).orElseThrow();
        eco.ensureAccount(player.server, player.getUUID(), player.getGameProfile().getName());
        if (contract.reward() > 0) {
            eco.deposit(player.server, player.getUUID(), contract.reward(), "contract." + contract.job().getPath());
        }
        JobService.addXp(player, job, contract.xp());
        Component message = Component.translatable("contract.reikoeconomy.finished", text(line, contract.count()),
                eco.format(contract.reward()), contract.xp(), job.name()).withStyle(ChatFormatting.GOLD);
        player.sendSystemMessage(message);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.5F, 1.6F);
        return message;
    }

    /** "Deliver Wheat × 48", "Harvest Wheat × 64", "Kill Zombie × 8". */
    static Component text(ContractLine line, int count) {
        String key = line.kind() == ContractKind.BREAK && line.matureOnly() ? "harvest" : line.kind().key();
        return Component.translatable("contract.reikoeconomy." + key, line.label().get(), count);
    }

    /** The player's contracts as the job board shows them. */
    public static List<ContractView> views(ServerPlayer player) {
        List<ContractView> views = new ArrayList<>();
        for (ContractsData.Contract contract : current(player)) {
            Optional<ContractLine> line = lineOf(contract);
            Optional<JobDefinition> job = JobDefinitions.get(contract.job());
            if (line.isEmpty() || job.isEmpty()) {
                continue;
            }
            ItemStack icon = line.get().icon().get();
            boolean deliver = line.get().kind() == ContractKind.DELIVER;
            views.add(new ContractView(icon.isEmpty() ? new ItemStack(job.get().icon()) : icon, text(line.get(), contract.count()),
                    job.get().name(), contract.progress(), contract.count(), contract.reward(), contract.xp(), deliver,
                    contract.done(), deliver ? held(player, line.get()) : 0));
        }
        return views;
    }

    /** Everything the job board shows. */
    public static JobBoardData boardData(ServerPlayer player) {
        return new JobBoardData(JobService.views(player), EconomyConfig.maxJobs(), views(player), secondsToNewDay(),
                !JobService.activeJobs(player).isEmpty());
    }
}

package com.reiko.economy.jobs;

import com.reiko.economy.config.EconomyConfig;
import com.reiko.economy.core.EconomyService;
import com.reiko.economy.core.Money;
import com.reiko.economy.shop.ShopActionResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Joining and quitting jobs, and paying for work.
 *
 * <p>Pay shows up straight away above the hotbar, but is deposited into the bank every few seconds as one
 * transaction per job, so mining a vein of ore doesn't flood the transaction log with tiny payments. Pay waiting
 * to be deposited is also paid out when the player logs out or the server stops.
 */
public final class JobService {
    /** How often waiting pay is deposited, in ticks. */
    public static final int DEPOSIT_EVERY_TICKS = 100;

    /** Pay waiting to be deposited, in cents (with fractions), per player and job. */
    private static final Map<UUID, Map<ResourceLocation, Double>> PENDING = new HashMap<>();

    private JobService() {
    }

    // ---- joining ----

    public static ShopActionResult join(ServerPlayer player, ResourceLocation jobId) {
        if (!EconomyConfig.jobsEnabled()) {
            return ShopActionResult.fail("job.reikoeconomy.disabled");
        }
        Optional<JobDefinition> job = JobDefinitions.get(jobId);
        if (job.isEmpty()) {
            return ShopActionResult.fail("job.reikoeconomy.unknown");
        }
        JobsData data = JobsData.get(player.server);
        JobsData.Progress progress = data.progress(player.getUUID(), jobId);
        if (progress.active()) {
            return ShopActionResult.fail("job.reikoeconomy.already", job.get().name());
        }
        List<ResourceLocation> active = activeJobs(player);
        if (active.size() >= EconomyConfig.maxJobs()) {
            return ShopActionResult.fail("job.reikoeconomy.too_many", EconomyConfig.maxJobs());
        }
        data.setActive(progress, true);
        return ShopActionResult.ok(Component.translatable("job.reikoeconomy.joined", job.get().name(), progress.level()));
    }

    public static ShopActionResult quit(ServerPlayer player, ResourceLocation jobId) {
        JobsData data = JobsData.get(player.server);
        JobsData.Progress progress = data.progress(player.getUUID(), jobId);
        Component name = JobDefinitions.get(jobId).map(JobDefinition::name).orElse(Component.literal(jobId.toString()));
        if (!progress.active()) {
            return ShopActionResult.fail("job.reikoeconomy.not_yours", name);
        }
        deposit(player.server, player.getUUID());
        data.setActive(progress, false);
        return ShopActionResult.ok(Component.translatable("job.reikoeconomy.quit", name, progress.level()));
    }

    /** The jobs the player has that are still loaded (a removed datapack's jobs are ignored). */
    public static List<ResourceLocation> activeJobs(ServerPlayer player) {
        List<ResourceLocation> active = new ArrayList<>();
        for (ResourceLocation id : JobsData.get(player.server).activeJobs(player.getUUID())) {
            if (JobDefinitions.get(id).isPresent()) {
                active.add(id);
            }
        }
        return active;
    }

    // ---- earning ----

    /** A player broke a block. Pays each of their jobs that pays for it. */
    public static void blockBroken(ServerPlayer player, BlockState state, BlockPos pos) {
        boolean placedByPlayer = PlacedBlocks.takePlaced(player.level(), pos);
        forEachJob(player, job -> job.forBreaking(state).ifPresent(reward -> {
            if (reward.matureOnly() ? isFullyGrown(state) : !placedByPlayer) {
                earn(player, job, reward);
            }
        }));
        ContractService.blockBroken(player, state, placedByPlayer);
    }

    /** Whether some job pays for breaking this block when it grew naturally, so placing it should be remembered. */
    public static boolean paysForBreaking(BlockState state) {
        for (JobDefinition job : JobDefinitions.all()) {
            Optional<JobReward<BlockState>> reward = job.forBreaking(state);
            if (reward.isPresent() && !reward.get().matureOnly()) {
                return true;
            }
            for (ContractLine line : job.contracts()) {
                if (line.kind() == ContractKind.BREAK && !line.matureOnly() && line.matches().test(state)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void mobKilled(ServerPlayer player, Entity victim) {
        forEachJob(player, job -> job.forKilling(victim).ifPresent(reward -> earn(player, job, reward)));
        ContractService.mobKilled(player, victim);
    }

    public static void fishCaught(ServerPlayer player, List<ItemStack> catches) {
        for (ItemStack stack : catches) {
            forEachJob(player, job -> job.forFishing(stack).ifPresent(reward -> earn(player, job, reward)));
        }
        ContractService.fishCaught(player, catches);
    }

    public static void animalsBred(ServerPlayer player, Entity parent) {
        forEachJob(player, job -> job.forBreeding(parent).ifPresent(reward -> earn(player, job, reward)));
        ContractService.animalsBred(player, parent);
    }

    /**
     * A player did {@code amount} of another mod's {@code action} (see {@link com.reiko.economy.api.JobsApi}).
     * Pays each of their jobs with a {@code work} line for it, and counts toward a matching contract.
     */
    public static void work(ServerPlayer player, ResourceLocation action, int amount) {
        if (amount <= 0) {
            return;
        }
        forEachJob(player, job -> job.forWork(action).ifPresent(reward -> earn(player, job, reward, amount)));
        ContractService.work(player, action, amount);
    }

    /** Whether any loaded job pays for, or has a contract for, another mod's {@code action}. */
    public static boolean paysForWork(ResourceLocation action) {
        for (JobDefinition job : JobDefinitions.all()) {
            if (job.forWork(action).isPresent()) {
                return true;
            }
            for (ContractLine line : job.contracts()) {
                if (line.kind() == ContractKind.WORK && line.matches().test(action)) {
                    return true;
                }
            }
        }
        return false;
    }

    private interface JobAction {
        void accept(JobDefinition job);
    }

    private static void forEachJob(ServerPlayer player, JobAction action) {
        if (!EconomyConfig.jobsEnabled() || player.isCreative() || player.isSpectator()) {
            return;
        }
        for (ResourceLocation id : JobsData.get(player.server).activeJobs(player.getUUID())) {
            JobDefinitions.get(id).ifPresent(action::accept);
        }
    }

    /** Pays for one action, adds job XP, and shows the pay above the hotbar. */
    static void earn(ServerPlayer player, JobDefinition job, JobReward<?> reward) {
        earn(player, job, reward, 1);
    }

    /** Pays for {@code amount} of an action at once. */
    static void earn(ServerPlayer player, JobDefinition job, JobReward<?> reward, int amount) {
        JobsData.Progress progress = JobsData.get(player.server).progress(player.getUUID(), job.id());
        int maxLevel = EconomyConfig.maxJobLevel();
        double cents = amount * JobMath.pay(reward.payCents(), Math.min(progress.level(), maxLevel), EconomyConfig.jobBonusPerLevel(),
                EconomyConfig.jobPayMultiplier());
        double pending = PENDING.computeIfAbsent(player.getUUID(), id -> new LinkedHashMap<>()).merge(job.id(), cents, Double::sum);
        addXp(player, job, (long) reward.xp() * amount);
        if (pending >= 0.5) {
            player.displayClientMessage(Component.translatable("job.reikoeconomy.earned",
                    EconomyService.INSTANCE.format(Math.round(pending)), job.name()).withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** Adds job XP, announcing a level up. */
    static void addXp(ServerPlayer player, JobDefinition job, long xp) {
        JobsData data = JobsData.get(player.server);
        JobsData.Progress progress = data.progress(player.getUUID(), job.id());
        int before = progress.level();
        JobMath.Progress next = JobMath.addXp(new JobMath.Progress(progress.level(), progress.xp()), xp, EconomyConfig.maxJobLevel());
        data.setProgress(progress, next);
        if (next.level() > before) {
            player.sendSystemMessage(Component.translatable("job.reikoeconomy.level_up", next.level(), job.name(),
                    bonusText(next.level())).withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.6F, 1.2F);
        }
    }

    /** "+6%": the pay bonus at a level. */
    public static String bonusText(int level) {
        return "+" + Money.toPlainString(JobMath.bonusBasisPoints(level, EconomyConfig.jobBonusPerLevel())).replace(".00", "") + "%";
    }

    /** True for crops at their last growth stage (any block with an "age" property at its highest value). */
    static boolean isFullyGrown(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return state.getValue(age) == max;
            }
        }
        return true;
    }

    // ---- paying out ----

    /** Deposits everyone's waiting pay. */
    public static void depositAll(MinecraftServer server) {
        for (UUID player : new ArrayList<>(PENDING.keySet())) {
            deposit(server, player);
        }
    }

    /** Deposits one player's waiting pay, keeping fractions of a cent for next time. */
    public static void deposit(MinecraftServer server, UUID player) {
        Map<ResourceLocation, Double> pending = PENDING.get(player);
        if (pending == null) {
            return;
        }
        EconomyService eco = EconomyService.INSTANCE;
        pending.replaceAll((jobId, cents) -> {
            long whole = (long) Math.floor(cents);
            if (whole <= 0) {
                return cents;
            }
            String reason = JobDefinitions.get(jobId).map(JobDefinition::payReason).orElse("job." + jobId.getPath());
            return eco.deposit(server, player, whole, reason).isSuccess() ? cents - whole : cents;
        });
        pending.values().removeIf(cents -> cents < 0.000_001);
        if (pending.isEmpty()) {
            PENDING.remove(player);
        }
    }

    /** Forgets all waiting pay. Called after the last deposit when the server stops. */
    public static void clear() {
        PENDING.clear();
    }

    // ---- what the player sees ----

    /** Everything the job board shows about each job, for this player. */
    public static List<JobView> views(ServerPlayer player) {
        JobsData data = JobsData.get(player.server);
        List<JobView> views = new ArrayList<>();
        EconomyService eco = EconomyService.INSTANCE;
        double multiplier = EconomyConfig.jobPayMultiplier();
        for (JobDefinition job : JobDefinitions.all()) {
            JobsData.Progress progress = data.progress(player.getUUID(), job.id());
            int level = Math.min(progress.level(), EconomyConfig.maxJobLevel());
            List<Component> examples = new ArrayList<>();
            for (JobReward<?> reward : job.examples(5)) {
                long cents = Math.round(JobMath.pay(reward.payCents(), level, EconomyConfig.jobBonusPerLevel(), multiplier));
                examples.add(Component.translatable("gui.reikoeconomy.jobs.example", reward.label().get(), eco.format(cents)));
            }
            boolean maxed = level >= EconomyConfig.maxJobLevel();
            views.add(new JobView(job.id(), job.name(), job.description(), new ItemStack(job.icon()), examples, level,
                    progress.xp(), maxed ? 0 : JobMath.xpToNext(level), progress.active(), bonusText(level)));
        }
        return views;
    }
}

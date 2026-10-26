package com.reiko.economy.jobs;

import com.reiko.economy.ReikoEconomy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every player's contracts for the day, saved with the overworld in {@code data/reikoeconomy_contracts.dat}.
 * Only {@link ContractService} changes it.
 */
public final class ContractsData extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_contracts";

    private final Map<UUID, Day> players = new HashMap<>();

    /** One player's contracts and the day (days since 1970, local time) they were given out. */
    static final class Day {
        long day = Long.MIN_VALUE;
        final List<Contract> contracts = new ArrayList<>();
    }

    /** One contract: a line of a job file, how many it asks for, how far along it is and what it pays. */
    public static final class Contract {
        private final ResourceLocation job;
        private final int line;
        private final int count;
        private int progress;
        private final long reward;
        private final int xp;
        private boolean done;

        Contract(ResourceLocation job, int line, int count, long reward, int xp) {
            this.job = job;
            this.line = line;
            this.count = count;
            this.reward = reward;
            this.xp = xp;
        }

        public ResourceLocation job() {
            return job;
        }

        /** Index into the job's contract lines. */
        public int line() {
            return line;
        }

        public int count() {
            return count;
        }

        public int progress() {
            return progress;
        }

        /** Money for finishing it, in cents. */
        public long reward() {
            return reward;
        }

        public int xp() {
            return xp;
        }

        public boolean done() {
            return done;
        }

        public int remaining() {
            return Math.max(0, count - progress);
        }
    }

    public static ContractsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ContractsData::new, ContractsData::load), DATA_NAME);
    }

    Day day(UUID player) {
        return players.computeIfAbsent(player, id -> new Day());
    }

    void replace(Day day, long today, List<Contract> contracts) {
        day.day = today;
        day.contracts.clear();
        day.contracts.addAll(contracts);
        setDirty();
    }

    /** Adds progress, capped at the count. Returns true if this finished the contract. */
    boolean addProgress(Contract contract, int amount) {
        if (contract.done || amount <= 0) {
            return false;
        }
        contract.progress = Math.min(contract.count, contract.progress + amount);
        if (contract.progress >= contract.count) {
            contract.done = true;
        }
        setDirty();
        return contract.done;
    }

    public static ContractsData load(CompoundTag tag, HolderLookup.Provider registries) {
        ContractsData data = new ContractsData();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("player")) {
                continue;
            }
            Day day = new Day();
            day.day = entry.getLong("day");
            ListTag contracts = entry.getList("contracts", Tag.TAG_COMPOUND);
            for (int j = 0; j < contracts.size(); j++) {
                CompoundTag saved = contracts.getCompound(j);
                ResourceLocation job = ResourceLocation.tryParse(saved.getString("job"));
                if (job == null) {
                    continue;
                }
                Contract contract = new Contract(job, saved.getInt("line"), saved.getInt("count"), saved.getLong("reward"), saved.getInt("xp"));
                contract.progress = saved.getInt("progress");
                contract.done = saved.getBoolean("done");
                day.contracts.add(contract);
            }
            data.players.put(entry.getUUID("player"), day);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((player, day) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putLong("day", day.day);
            ListTag contracts = new ListTag();
            for (Contract contract : day.contracts) {
                CompoundTag saved = new CompoundTag();
                saved.putString("job", contract.job.toString());
                saved.putInt("line", contract.line);
                saved.putInt("count", contract.count);
                saved.putInt("progress", contract.progress);
                saved.putLong("reward", contract.reward);
                saved.putInt("xp", contract.xp);
                saved.putBoolean("done", contract.done);
                contracts.add(saved);
            }
            entry.put("contracts", contracts);
            list.add(entry);
        });
        tag.put("players", list);
        return tag;
    }
}

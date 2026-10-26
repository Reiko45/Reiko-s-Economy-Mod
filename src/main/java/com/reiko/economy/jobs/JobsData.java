package com.reiko.economy.jobs;

import com.reiko.economy.ReikoEconomy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Every player's job levels, saved with the overworld in {@code data/reikoeconomy_jobs.dat}. A player keeps
 * their level in a job after quitting it, so rejoining picks up where they left off.
 */
public final class JobsData extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_jobs";

    private final Map<UUID, Map<ResourceLocation, Progress>> players = new HashMap<>();

    public static JobsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(JobsData::new, JobsData::load), DATA_NAME);
    }

    /** One player's standing in one job. */
    public static final class Progress {
        private int level = 1;
        private long xp;
        private boolean active;

        public int level() {
            return level;
        }

        public long xp() {
            return xp;
        }

        public boolean active() {
            return active;
        }
    }

    public static JobsData load(CompoundTag tag, HolderLookup.Provider registries) {
        JobsData data = new JobsData();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("player")) {
                continue;
            }
            Map<ResourceLocation, Progress> jobs = new LinkedHashMap<>();
            ListTag jobList = entry.getList("jobs", Tag.TAG_COMPOUND);
            for (int j = 0; j < jobList.size(); j++) {
                CompoundTag job = jobList.getCompound(j);
                ResourceLocation id = ResourceLocation.tryParse(job.getString("id"));
                if (id == null) {
                    continue;
                }
                Progress progress = new Progress();
                progress.level = Math.max(1, job.getInt("level"));
                progress.xp = job.getLong("xp");
                progress.active = job.getBoolean("active");
                jobs.put(id, progress);
            }
            data.players.put(entry.getUUID("player"), jobs);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((player, jobs) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            ListTag jobList = new ListTag();
            jobs.forEach((id, progress) -> {
                CompoundTag job = new CompoundTag();
                job.putString("id", id.toString());
                job.putInt("level", progress.level);
                job.putLong("xp", progress.xp);
                job.putBoolean("active", progress.active);
                jobList.add(job);
            });
            entry.put("jobs", jobList);
            list.add(entry);
        });
        tag.put("players", list);
        return tag;
    }

    /** The player's progress in a job, starting at level 1 if they never had it. */
    public Progress progress(UUID player, ResourceLocation job) {
        return players.computeIfAbsent(player, id -> new LinkedHashMap<>()).computeIfAbsent(job, id -> new Progress());
    }

    /** The jobs the player has now. */
    public List<ResourceLocation> activeJobs(UUID player) {
        List<ResourceLocation> active = new ArrayList<>();
        players.getOrDefault(player, Map.of()).forEach((id, progress) -> {
            if (progress.active) {
                active.add(id);
            }
        });
        return active;
    }

    void setActive(Progress progress, boolean active) {
        progress.active = active;
        setDirty();
    }

    void setProgress(Progress progress, JobMath.Progress next) {
        progress.level = next.level();
        progress.xp = next.xp();
        setDirty();
    }
}

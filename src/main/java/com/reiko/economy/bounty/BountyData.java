package com.reiko.economy.bounty;

import com.reiko.economy.ReikoEconomy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every open bounty. The money is held here, out of the poster's bank account, until the bounty is collected or
 * runs out. Saved with the overworld in {@code data/reikoeconomy_bounties.dat}. Only {@link BountyService}
 * changes it.
 */
public final class BountyData extends SavedData {
    public static final String DATA_NAME = ReikoEconomy.MODID + "_bounties";

    private final List<Bounty> bounties = new ArrayList<>();

    /** One bounty: {@code amount} for killing {@code target}, posted by {@code poster}, until {@code expiresAt}. */
    public record Bounty(UUID id, UUID poster, String posterName, UUID target, String targetName, long amount,
                         long postedAt, long expiresAt) {
    }

    public static BountyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BountyData::new, BountyData::load), DATA_NAME);
    }

    public List<Bounty> all() {
        return List.copyOf(bounties);
    }

    /** The bounties on {@code target}, oldest first. */
    public List<Bounty> on(UUID target) {
        return bounties.stream().filter(bounty -> bounty.target().equals(target)).toList();
    }

    /** The bounties {@code poster} has up, oldest first. */
    public List<Bounty> postedBy(UUID poster) {
        return bounties.stream().filter(bounty -> bounty.poster().equals(poster)).toList();
    }

    /** What's on {@code target} in total. */
    public long totalOn(UUID target) {
        long total = 0;
        for (Bounty bounty : bounties) {
            if (bounty.target().equals(target)) {
                total += bounty.amount();
            }
        }
        return total;
    }

    void add(Bounty bounty) {
        bounties.add(bounty);
        setDirty();
    }

    void remove(Bounty bounty) {
        if (bounties.remove(bounty)) {
            setDirty();
        }
    }

    public static BountyData load(CompoundTag tag, HolderLookup.Provider registries) {
        BountyData data = new BountyData();
        ListTag list = tag.getList("bounties", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            if (!saved.hasUUID("id") || !saved.hasUUID("poster") || !saved.hasUUID("target")) {
                continue;
            }
            data.bounties.add(new Bounty(saved.getUUID("id"), saved.getUUID("poster"), saved.getString("poster_name"),
                    saved.getUUID("target"), saved.getString("target_name"), saved.getLong("amount"),
                    saved.getLong("posted_at"), saved.getLong("expires_at")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Bounty bounty : bounties) {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("id", bounty.id());
            saved.putUUID("poster", bounty.poster());
            saved.putString("poster_name", bounty.posterName());
            saved.putUUID("target", bounty.target());
            saved.putString("target_name", bounty.targetName());
            saved.putLong("amount", bounty.amount());
            saved.putLong("posted_at", bounty.postedAt());
            saved.putLong("expires_at", bounty.expiresAt());
            list.add(saved);
        }
        tag.put("bounties", list);
        return tag;
    }
}

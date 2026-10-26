package com.reiko.economy.jobs;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Remembers which job-paying blocks (ores, logs, dirt...) a player placed, so placing and breaking the same
 * block over and over doesn't pay. Stored on each chunk and saved with it.
 */
public final class PlacedBlocks {
    public static final Codec<PlacedBlocks> CODEC = Codec.LONG.listOf()
            .xmap(list -> new PlacedBlocks(new LongOpenHashSet(list)), placed -> new ArrayList<>(placed.positions));

    private final LongSet positions;

    public PlacedBlocks() {
        this(new LongOpenHashSet());
    }

    private PlacedBlocks(LongSet positions) {
        this.positions = positions;
    }

    public static void markPlaced(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (chunk.getData(ModAttachments.PLACED_BLOCKS).positions.add(pos.asLong())) {
            chunk.setUnsaved(true);
        }
    }

    /** Whether a player placed the block at {@code pos}. Forgets it either way, since it's being broken. */
    public static boolean takePlaced(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!chunk.hasData(ModAttachments.PLACED_BLOCKS)) {
            return false;
        }
        boolean placed = chunk.getData(ModAttachments.PLACED_BLOCKS).positions.remove(pos.asLong());
        if (placed) {
            chunk.setUnsaved(true);
        }
        return placed;
    }
}

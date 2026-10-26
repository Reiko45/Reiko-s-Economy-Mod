package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.jobs.JobView;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the job board's jobs and the player's levels, after they joined or quit a job. */
public record JobBoardSyncPayload(int containerId, List<JobView> views, int maxJobs) implements CustomPacketPayload {
    public static final Type<JobBoardSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "job_board_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JobBoardSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JobBoardSyncPayload::containerId,
            JobView.LIST_CODEC, JobBoardSyncPayload::views,
            ByteBufCodecs.VAR_INT, JobBoardSyncPayload::maxJobs,
            JobBoardSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

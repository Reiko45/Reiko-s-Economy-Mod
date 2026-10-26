package com.reiko.economy.jobs;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything the job board screen shows, sent when it opens and after every change.
 *
 * @param secondsToNew seconds until new contracts (at the server's midnight)
 * @param hasJobs      whether the player has a job; contracts come from their jobs
 */
public record JobBoardData(List<JobView> views, int maxJobs, List<ContractView> contracts, long secondsToNew, boolean hasJobs) {
    public static final StreamCodec<RegistryFriendlyByteBuf, JobBoardData> STREAM_CODEC = StreamCodec.composite(
            JobView.LIST_CODEC, JobBoardData::views,
            ByteBufCodecs.VAR_INT, JobBoardData::maxJobs,
            ContractView.LIST_CODEC, JobBoardData::contracts,
            ByteBufCodecs.VAR_LONG, JobBoardData::secondsToNew,
            ByteBufCodecs.BOOL, JobBoardData::hasJobs,
            JobBoardData::new);
}

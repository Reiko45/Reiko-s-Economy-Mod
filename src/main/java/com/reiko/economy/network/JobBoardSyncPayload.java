package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import com.reiko.economy.jobs.JobBoardData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: everything the open job board shows, after the player joined or quit a job or delivered. */
public record JobBoardSyncPayload(int containerId, JobBoardData data) implements CustomPacketPayload {
    public static final Type<JobBoardSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "job_board_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JobBoardSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JobBoardSyncPayload::containerId,
            JobBoardData.STREAM_CODEC, JobBoardSyncPayload::data,
            JobBoardSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

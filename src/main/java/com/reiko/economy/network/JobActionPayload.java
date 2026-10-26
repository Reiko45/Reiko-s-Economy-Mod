package com.reiko.economy.network;

import com.reiko.economy.ReikoEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: join or quit a job from the open job board. */
public record JobActionPayload(int containerId, ResourceLocation job, boolean join) implements CustomPacketPayload {
    public static final Type<JobActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ReikoEconomy.MODID, "job_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JobActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JobActionPayload::containerId,
            ResourceLocation.STREAM_CODEC, JobActionPayload::job,
            ByteBufCodecs.BOOL, JobActionPayload::join,
            JobActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
